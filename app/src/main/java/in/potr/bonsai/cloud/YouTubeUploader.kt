package `in`.potr.bonsai.cloud

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uploads Before & After bonsai lifespan videos directly to YouTube via YouTube Data API v3
 * resumable uploads so large phone videos stream directly from disk without exhausting RAM.
 */
class YouTubeUploader {

    data class YouTubeUploadResult(
        val videoId: String,
        val watchUrl: String
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun uploadVideoResumable(
        accessToken: String,
        videoFile: File,
        title: String,
        description: String,
        privacyStatus: String = "unlisted",
        tags: List<String> = listOf("POTR Bonsai", "Bonsai", "Pots on the Rocks")
    ): YouTubeUploadResult {
        if (!videoFile.exists() || videoFile.length() <= 0L) {
            throw IllegalStateException("Local video file is missing or empty")
        }

        val safePrivacy = if (privacyStatus == "private" || privacyStatus == "public") privacyStatus else "unlisted"
        val metadata = JSONObject().apply {
            put(
                "snippet",
                JSONObject().apply {
                    put("title", title.take(95))
                    put("description", description.take(4500))
                    put("categoryId", "26") // Howto & Style
                    put("tags", JSONArray(tags))
                }
            )
            put(
                "status",
                JSONObject().apply {
                    put("privacyStatus", safePrivacy)
                    put("selfDeclaredMadeForKids", false)
                }
            )
        }

        // Step 1: Initiate resumable upload session
        val initReq = Request.Builder()
            .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("X-Upload-Content-Length", videoFile.length().toString())
            .addHeader("X-Upload-Content-Type", "video/*")
            .post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val uploadLocation: String = http.newCall(initReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("YouTube upload init failed (${resp.code}): ${resp.body?.string()}")
            }
            resp.header("Location")
                ?: throw IllegalStateException("YouTube resumable upload missing Location header")
        }

        // Step 2: Stream video bytes from file to session URL
        val fileBody = object : RequestBody() {
            override fun contentType(): MediaType = "video/*".toMediaType()
            override fun contentLength(): Long = videoFile.length()
            override fun writeTo(sink: BufferedSink) {
                videoFile.source().use { source ->
                    sink.writeAll(source)
                }
            }
        }

        val uploadReq = Request.Builder()
            .url(uploadLocation)
            .addHeader("Authorization", "Bearer $accessToken")
            .put(fileBody)
            .build()

        http.newCall(uploadReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("YouTube video transfer failed (${resp.code}): $bodyStr")
            }
            val json = JSONObject(bodyStr)
            val id = json.optString("id")
            if (id.isBlank()) {
                throw IllegalStateException("YouTube response missing video ID")
            }
            return YouTubeUploadResult(
                videoId = id,
                watchUrl = "https://youtu.be/$id"
            )
        }
    }
}
