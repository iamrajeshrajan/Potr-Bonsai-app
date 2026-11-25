package `in`.potr.bonsai.cloud

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface AuthTokenResult {
    data class Success(val token: String, val email: String) : AuthTokenResult
    data object NotSignedIn : AuthTokenResult
    data class NeedsResolution(val pendingIntent: PendingIntent) : AuthTokenResult
    data class Error(val message: String, val isConfigError: Boolean = false) : AuthTokenResult
}

object GoogleAuthTokenProvider {

    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_SHEETS = "https://www.googleapis.com/auth/spreadsheets"
    const val SCOPE_YOUTUBE_UPLOAD = "https://www.googleapis.com/auth/youtube.upload"
    const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun buildAuthorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_DRIVE_FILE),
                    Scope(SCOPE_SHEETS),
                    Scope(SCOPE_YOUTUBE_UPLOAD),
                    Scope(SCOPE_USERINFO_EMAIL)
                )
            )
            .build()

    /**
     * Authorizes Google Drive, Sheets, YouTube, and Email scopes using the modern
     * Google Identity Services [com.google.android.gms.auth.api.identity.AuthorizationClient].
     *
     * When [requireExistingSessionEmail] is provided and null/blank, returns [AuthTokenResult.NotSignedIn]
     * without prompting (used for silent background sync / workers).
     */
    suspend fun fetchAccessToken(
        context: Context,
        existingEmail: String?,
        allowInteractiveResolution: Boolean = true
    ): AuthTokenResult = withContext(Dispatchers.IO) {
        if (!allowInteractiveResolution && existingEmail.isNullOrBlank()) {
            return@withContext AuthTokenResult.NotSignedIn
        }
        try {
            val result = Tasks.await(
                Identity.getAuthorizationClient(context)
                    .authorize(buildAuthorizationRequest())
            )
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    AuthTokenResult.NeedsResolution(pendingIntent)
                } else {
                    AuthTokenResult.Error(
                        "Google authorization requires user consent — tap Sign In to continue."
                    )
                }
            } else {
                val token = result.accessToken
                if (token.isNullOrBlank()) {
                    AuthTokenResult.Error("Google returned an empty access token. Please try Sign In again.")
                } else {
                    val resolvedEmail = existingEmail?.takeIf { it.isNotBlank() }
                        ?: fetchUserEmail(token)
                        ?: "Google Account"
                    AuthTokenResult.Success(token = token, email = resolvedEmail)
                }
            }
        } catch (e: Exception) {
            val apiEx = findApiException(e)
            if (apiEx != null) {
                val (msg, isConfig) = explainApiException(apiEx)
                AuthTokenResult.Error(msg, isConfigError = isConfig)
            } else {
                AuthTokenResult.Error(e.message ?: "Unable to connect to Google Play Services.")
            }
        }
    }

    /**
     * Resolves the authenticated Google user's email address from Google's OAuth2 UserInfo endpoint.
     */
    suspend fun fetchUserEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                JSONObject(body).optString("email").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    fun findApiException(throwable: Throwable?): ApiException? {
        var cur = throwable
        while (cur != null) {
            if (cur is ApiException) return cur
            cur = cur.cause
        }
        return null
    }

    fun explainApiException(e: ApiException): Pair<String, Boolean> {
        return when (e.statusCode) {
            10 -> Pair(
                "OAuth setup required (Error 10 — DEVELOPER_ERROR): Register package in.potr.bonsai and this APK's SHA-1 fingerprint below in Google Cloud Console → Credentials → Android OAuth Client ID.",
                true
            )
            16 -> Pair(
                "Google Sign-In canceled or OAuth client not matched (Error 16). If you didn't cancel, verify this APK's SHA-1 is registered in Google Cloud Console and your Gmail is added as a Test User.",
                true
            )
            12500 -> Pair(
                "Google Sign-In failed (Error 12500): Configure the OAuth Consent Screen in Google Cloud Console and add your Gmail under Test Users.",
                true
            )
            12501 -> Pair(
                "Google Sign-In was canceled.",
                false
            )
            12502 -> Pair(
                "Google Sign-In is already in progress. Please wait a moment.",
                false
            )
            7 -> Pair(
                "Network error (Error 7): Check your internet connection and try again.",
                false
            )
            else -> {
                val label = CommonStatusCodes.getStatusCodeString(e.statusCode)
                val isConfig = e.statusCode in setOf(8, 13, 17, 28436, 28444)
                Pair(
                    "Google Sign-In failed (Error ${e.statusCode} — $label). Check OAuth Client SHA-1 & enabled APIs in Google Cloud Console.",
                    isConfig
                )
            }
        }
    }

    /**
     * Reads the SHA-1 signing certificate fingerprint directly from this installed APK so the user
     * can copy-paste it into Google Cloud Console without digging into build logs.
     */
    @Suppress("DEPRECATION")
    fun getInstalledAppSha1(context: Context): String {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
                    else signingInfo.signingCertificateHistory
                } else {
                    emptyArray()
                }
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                    ?: emptyArray()
            }
            val certBytes = signatures.firstOrNull()?.toByteArray() ?: return "Unavailable"
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "Unavailable"
        }
    }
}
