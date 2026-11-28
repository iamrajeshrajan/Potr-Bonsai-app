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
 * and downloads the APK asset with progress reporting and integrity validation.
 */
class UpdateChecker(private val repoOwner: String, private val repoName: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class UpdateResult(
        val updateAvailable: Boolean,
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String?,
        val releasePageUrl: String
    )

    fun releasesPageUrl(): String = "https://github.com/$repoOwner/$repoName/releases/latest"

    fun checkLatestRelease(currentVersion: String): Result<UpdateResult> = runCatching {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", USER_AGENT)
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            when (resp.code) {
                404 -> error("No public GitHub Release found yet (make sure repo is Public and a Release is published)")
                403 -> error("GitHub API rate limit reached — try again in a few minutes")
            }
            if (!resp.isSuccessful) {
                error("GitHub release check failed (HTTP ${resp.code})")
            }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val htmlUrl = json.optString("html_url", releasesPageUrl())
            val assets = json.optJSONArray("assets")
            var downloadUrl: String? = null
            if (assets != null) {
                // Prefer the installable release APK
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk") && name.contains("installable", ignoreCase = true)) {
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
                downloadUrl = downloadUrl,
                releasePageUrl = htmlUrl
            )
        }
    }

    fun downloadApk(
        apkUrl: String,
        destFile: File,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<File> = runCatching {
        val req = Request.Builder()
            .url(apkUrl)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/octet-stream")
            .get()
            .build()

        destFile.parentFile?.mkdirs()
        val tmpFile = File(destFile.parentFile, "${destFile.name}.part")
        if (tmpFile.exists()) tmpFile.delete()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("APK download failed (HTTP ${resp.code})")
            }
            val body = resp.body ?: error("Empty download response")
            val totalBytes = body.contentLength()
            var copied = 0L
            var lastReportedPct = -1

            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (totalBytes > 0L) {
                            val pct = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (copied < 500_000L) {
                tmpFile.delete()
                error("Downloaded file was incomplete (${copied / 1024} KB). Please retry.")
            }
        }

        if (destFile.exists()) destFile.delete()
        if (!tmpFile.renameTo(destFile)) {
            tmpFile.copyTo(destFile, overwrite = true)
            tmpFile.delete()
        }
        onProgress(100)
        destFile
    }

    companion object {
        private const val USER_AGENT = "POTR-Bonsai-Android"
    }
}
