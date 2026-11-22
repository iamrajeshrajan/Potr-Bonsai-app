package `in`.potr.bonsai.cloud

import `in`.potr.bonsai.data.model.DueDateHelper
import `in`.potr.bonsai.data.model.SpecimenWithDetails
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Handles Google Drive v3 folder management ("Bonsai Catalogue" & "Photos"),
 * specimen JPEG uploads/downloads, versioned JSON database auto-backup/restore,
 * and Google Sheets v4 master catalogue export.
 */
class GoogleWorkspaceService {

    data class DriveUploadResult(
        val fileId: String,
        val webViewLink: String
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * Finds or creates a folder in Google Drive (optionally nested inside [parentFolderId]).
     */
    fun ensureDriveFolder(
        accessToken: String,
        folderName: String = DEFAULT_ROOT_FOLDER,
        parentFolderId: String? = null
    ): String {
        val escapedName = folderName.replace("'", "\\'")
        val parentClause = if (!parentFolderId.isNullOrBlank()) " and '$parentFolderId' in parents" else ""
        val rawQuery = "mimeType='application/vnd.google-apps.folder' and name='$escapedName'$parentClause and trashed=false"
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")
        val queryUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name)"

        val getReq = Request.Builder()
            .url(queryUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        http.newCall(getReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val files = JSONObject(bodyStr).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        }

        val meta = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
            if (!parentFolderId.isNullOrBlank()) {
                put("parents", JSONArray().put(parentFolderId))
            }
        }
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(meta.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        http.newCall(createReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Drive folder creation failed (${resp.code}): $bodyStr")
            }
            return JSONObject(bodyStr).getString("id")
        }
    }

    /**
     * Uploads a compressed JPEG photo into the specified Drive folder and returns its file ID & view link.
     */
    fun uploadCompressedJpegToDrive(
        accessToken: String,
        folderId: String,
        fileName: String,
        jpegBytes: ByteArray
    ): DriveUploadResult {
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
        }

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addPart(jpegBytes.toRequestBody("image/jpeg".toMediaType()))
            .build()

        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Drive photo upload failed (${resp.code}): $bodyStr")
            }
            val json = JSONObject(bodyStr)
            val id = json.getString("id")
            val link = json.optString("webViewLink", "https://drive.google.com/file/d/$id/view")
            return DriveUploadResult(fileId = id, webViewLink = link)
        }
    }

    /**
     * Downloads a Drive file by ID directly into [destFile]. Used when restoring photos on a new device.
     */
    fun downloadDriveFile(accessToken: String, fileId: String, destFile: File): File {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Drive download failed (${resp.code})")
            }
            destFile.parentFile?.mkdirs()
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Empty Drive response")
        }
        return destFile
    }

    /**
     * Finds an existing backup file in [folderId] by exact file name.
     */
    fun findFileInFolder(accessToken: String, folderId: String, fileName: String): String? {
        val escaped = fileName.replace("'", "\\'")
        val rawQuery = "name='$escaped' and '$folderId' in parents and trashed=false"
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,modifiedTime)")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return null
            val files = JSONObject(bodyStr).optJSONArray("files") ?: return null
            return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    /**
     * Creates or updates `potr_bonsai_backup.json` in place inside [folderId] so Drive keeps revision history.
     */
    fun uploadOrUpdateBackupJson(
        accessToken: String,
        folderId: String,
        existingFileId: String?,
        backupJson: String
    ): String {
        val targetId = existingFileId?.takeIf { it.isNotBlank() }
            ?: findFileInFolder(accessToken, folderId, BACKUP_FILE_NAME)

        if (targetId != null) {
            val patchReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$targetId?uploadType=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(backupJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            http.newCall(patchReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    return targetId
                }
                if (resp.code != 404) {
                    throw IllegalStateException("Drive backup update failed (${resp.code}): ${resp.body?.string()}")
                }
            }
        }

        val metadataJson = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("mimeType", "application/json")
            put("parents", JSONArray().put(folderId))
        }
        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addPart(backupJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val createReq = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        http.newCall(createReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Drive backup creation failed (${resp.code}): $bodyStr")
            }
            return JSONObject(bodyStr).getString("id")
        }
    }

    /**
     * Downloads the latest `potr_bonsai_backup.json` text from [folderId], or returns null if none exists yet.
     */
    fun downloadBackupJson(accessToken: String, folderId: String): Pair<String, String>? {
        val fileId = findFileInFolder(accessToken, folderId, BACKUP_FILE_NAME) ?: return null
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Drive backup download failed (${resp.code}): $bodyStr")
            }
            return fileId to bodyStr
        }
    }

    /**
     * Creates or cleanly overwrites the master Google Sheet ("POTR Bonsai Master Catalogue").
     * Clears previous rows first so deleted specimens do not leave stale trailing rows.
     */
    fun syncSpecimensToSheet(
        accessToken: String,
        existingSheetId: String?,
        specimens: List<SpecimenWithDetails>
    ): String {
        val spreadsheetId = if (!existingSheetId.isNullOrBlank()) {
            existingSheetId
        } else {
            val createPayload = JSONObject().apply {
                put("properties", JSONObject().put("title", "POTR Bonsai Master Catalogue"))
            }
            val req = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(createPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            http.newCall(req).execute().use { resp ->
                val str = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Sheets creation failed (${resp.code}): $str")
                JSONObject(str).getString("spreadsheetId")
            }
        }

        // Clear existing range A:R first so deleted items don't linger
        val clearReq = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/A:R:clear")
            .addHeader("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(clearReq).execute().close()

        val rows = JSONArray()
        rows.put(
            JSONArray().apply {
                put("Code")
                put("Name")
                put("Species")
                put("Subspecies")
                put("Age (Yrs)")
                put("Age Certainty")
                put("Pot Details")
                put("Purchase Price (INR)")
                put("New Pot Price (INR)")
                put("Materials & Effort (INR)")
                put("Est. Current Price (INR)")
                put("Selling Price (INR)")
                put("Published")
                put("Next Care Due")
                put("Drive Photo Link")
                put("YouTube Videos")
                put("potr.in URL")
            }
        )

        specimens.forEach { item ->
            val s = item.specimen
            val nextAction = item.sortedActions.firstOrNull()
            val nextCareText = if (nextAction != null) {
                "${nextAction.title} (${DueDateHelper.formatEpochDay(nextAction.dueDateEpochDay)})"
            } else {
                ""
            }
            val ytLinks = item.sortedTimeline
                .flatMap { listOfNotNull(it.beforeYoutubeUrl, it.afterYoutubeUrl) }
                .joinToString(" | ")

            rows.put(
                JSONArray().apply {
                    put(s.code)
                    put(s.name)
                    put(s.species)
                    put(s.subspecies)
                    put(s.age)
                    put(s.ageType)
                    put(s.pot)
                    put(s.purchasePrice)
                    put(s.newPotPrice)
                    put(s.effortCost)
                    put(s.estimatedCurrentPrice)
                    put(s.sellingPrice)
                    put(if (s.published) "YES" else "NO")
                    put(nextCareText)
                    put(s.drivePhotoUrl.orEmpty())
                    put(ytLinks)
                    put(s.wooPermalink.orEmpty())
                }
            )
        }

        val valueRange = JSONObject().apply {
            put("range", "A1")
            put("majorDimension", "ROWS")
            put("values", rows)
        }

        val updateReq = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/A1?valueInputOption=USER_ENTERED")
            .addHeader("Authorization", "Bearer $accessToken")
            .put(valueRange.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        http.newCall(updateReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Sheets sync failed (${resp.code}): ${resp.body?.string()}")
            }
        }
        return spreadsheetId
    }

    companion object {
        const val DEFAULT_ROOT_FOLDER = "Bonsai Catalogue"
        const val PHOTOS_SUBFOLDER = "Photos"
        const val BACKUP_FILE_NAME = "potr_bonsai_backup.json"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
