package `in`.potr.bonsai.cloud

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object SemVerComparator {
    fun isNewer(candidate: String, current: String): Boolean {
        val cParts = parseParts(candidate)
        val curParts = parseParts(current)
        if (cParts.isEmpty() || curParts.isEmpty()) return false
        val maxLen = maxOf(cParts.size, curParts.size)
        for (i in 0 until maxLen) {
            val a = cParts.getOrElse(i) { 0 }
            val b = curParts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parseParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .split(".")
            .mapNotNull { it.toIntOrNull() }
}

/**
 * Checks GitHub Releases for a newer build of the app using semantic version comparison
 * and downloads the APK asset.
 */
class UpdateChecker(private val repoOwner: String, private val repoName: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class UpdateResult(
        val updateAvailable: Boolean,
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String?
    )

    fun checkLatestRelease(currentVersion: String): Result<UpdateResult> = runCatching {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
            .addHeader("Accept", "application/vnd.github+json")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 404) {
                error("No published release found yet (push to GitHub & ensure repo is public)")
            }
            if (!resp.isSuccessful) {
                error("GitHub release check failed (HTTP ${resp.code})")
            }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets")
            var downloadUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk") && name.contains("installable")) {
                        downloadUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (downloadUrl == null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name", "").endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
            }

            UpdateResult(
                updateAvailable = SemVerComparator.isNewer(tagName, currentVersion),
                currentVersion = currentVersion,
                latestVersion = tagName.ifBlank { currentVersion },
                downloadUrl = downloadUrl
            )
        }
    }

    fun downloadApk(apkUrl: String, destFile: File): Result<File> = runCatching {
        val req = Request.Builder().url(apkUrl).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("APK download failed (${resp.code})")
            }
            destFile.parentFile?.mkdirs()
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Empty download response")
        }
        destFile
    }
}
