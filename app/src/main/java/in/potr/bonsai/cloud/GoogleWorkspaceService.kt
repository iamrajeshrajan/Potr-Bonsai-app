package `in`.potr.bonsai.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles Google Drive v3 folder creation ("Bonsai Catalogue"), photo uploads,
 * Google Sheets v4 master database creation & row synchronization, and YouTube Data API v3 uploads.
 */
class GoogleWorkspaceService {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Finds or creates the "Bonsai Catalogue" folder in the user's Google Drive.
     */
    fun ensureDriveFolder(accessToken: String, folderName: String = "Bonsai Catalogue"): String {
        val queryUrl = "https://www.googleapis.com/drive/v3/files?q=" +
            "mimeType='application/vnd.google-apps.folder'+and+name='$folderName'+and+trashed=false" +
            "&fields=files(id,name)"

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

        // Create folder if not found
        val meta = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(meta.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(createReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Drive folder creation failed: $bodyStr")
            }
            return JSONObject(bodyStr).getString("id")
        }
    }

    /**
     * Uploads a compressed JPEG photo into the "Bonsai Catalogue" Google Drive folder.
     */
    fun uploadCompressedJpegToDrive(
        accessToken: String,
        folderId: String,
        fileName: String,
        jpegBytes: ByteArray
    ): String {
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
        }

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
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
                throw IllegalStateException("Drive photo upload failed: $bodyStr")
            }
            val json = JSONObject(bodyStr)
            return json.optString("webViewLink", "https://drive.google.com/file/d/${json.optString("id")}/view")
        }
    }

    /**
     * Creates or updates the master Google Sheet ("POTR Bonsai Master Catalogue") with all specimen records.
     */
    fun syncSpecimensToSheet(
        accessToken: String,
        existingSheetId: String?,
        specimensJsonArray: JSONArray
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
                .post(createPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val str = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Sheets creation failed: $str")
                JSONObject(str).getString("spreadsheetId")
            }
        }

        // Prepare rows
        val rows = JSONArray()
        val header = JSONArray().apply {
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
        }
        rows.put(header)

        for (i in 0 until specimensJsonArray.length()) {
            val s = specimensJsonArray.getJSONObject(i)
            val purchase = s.optDouble("purchasePrice", 0.0)
            val potCost = s.optDouble("newPotPrice", 0.0)
            val effort = s.optDouble("effortCost", 0.0)
            val est = purchase + potCost + effort
            val row = JSONArray().apply {
                put(s.optString("code"))
                put(s.optString("name"))
                put(s.optString("species"))
                put(s.optString("subspecies"))
                put(s.optInt("age", 0))
                put(s.optString("ageType"))
                put(s.optString("pot"))
                put(purchase)
                put(potCost)
                put(effort)
                put(est)
                put(s.optDouble("sellingPrice", 0.0))
                put(if (s.optBoolean("published")) "YES" else "NO")
            }
            rows.put(row)
        }

        val valueRange = JSONObject().apply {
            put("range", "A1")
            put("majorDimension", "ROWS")
            put("values", rows)
        }

        val updateReq = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/A1?valueInputOption=USER_ENTERED")
            .addHeader("Authorization", "Bearer $accessToken")
            .put(valueRange.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(updateReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Sheets sync failed: ${resp.body?.string()}")
            }
        }
        return spreadsheetId
    }
}
