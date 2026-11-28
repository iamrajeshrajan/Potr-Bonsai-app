package `in`.potr.bonsai.cloud

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import `in`.potr.bonsai.data.local.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed interface AuthTokenResult {
    data class Success(val token: String, val email: String) : AuthTokenResult
    data object NotSignedIn : AuthTokenResult
    data class Error(val message: String, val isConfigError: Boolean = false) : AuthTokenResult
}

/**
 * Standard RFC 7636 OAuth 2.0 Authorization Code + PKCE provider using the device's system browser.
 *
 * Bypasses Google Play Services AuthorizationClient IPC (which can fail with opaque
 * `[8] Unknown error [status=UNKNOWN]` on sideloaded APKs and OEM ROMs), stores access + refresh
 * tokens encrypted in Android Keystore via [SecurePrefs], and refreshes tokens silently for
 * background Drive auto-backups, Sheets exports, and YouTube uploads.
 */
object GoogleAuthTokenProvider {

    const val ANDROID_CLIENT_ID =
        "136300696845-6a19pif97gjn5i1h0d672rtlpj7pgsap.apps.googleusercontent.com"

    const val REDIRECT_SCHEME =
        "com.googleusercontent.apps.136300696845-6a19pif97gjn5i1h0d672rtlpj7pgsap"

    const val REDIRECT_PATH = "/oauth2redirect"
    const val REDIRECT_URI = "$REDIRECT_SCHEME:$REDIRECT_PATH"

    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_SHEETS = "https://www.googleapis.com/auth/spreadsheets"
    const val SCOPE_YOUTUBE_UPLOAD = "https://www.googleapis.com/auth/youtube.upload"
    const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"

    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
    private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"

    private val ALL_SCOPES = listOf(
        SCOPE_DRIVE_FILE,
        SCOPE_SHEETS,
        SCOPE_YOUTUBE_UPLOAD,
        SCOPE_USERINFO_EMAIL
    ).joinToString(" ")

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Generates a fresh PKCE verifier/challenge + CSRF state, persists them encrypted in [prefs],
     * and returns a browser [Intent] to launch Google's OAuth 2.0 consent screen.
     */
    fun buildBrowserSignInIntent(prefs: SecurePrefs): Intent {
        val verifier = randomBase64Url(48)
        val state = randomBase64Url(24)
        val challenge = sha256Base64Url(verifier)

        prefs.pendingOAuthVerifier = verifier
        prefs.pendingOAuthState = state

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", ANDROID_CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", ALL_SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        return Intent(Intent.ACTION_VIEW, authUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }

    fun isOAuthRedirectUri(uri: Uri?): Boolean {
        if (uri == null) return false
        return uri.scheme.equals(REDIRECT_SCHEME, ignoreCase = true) &&
            uri.path == REDIRECT_PATH
    }

    /**
     * Completes the PKCE OAuth 2.0 callback from Chrome: verifies CSRF state, exchanges the
     * authorization code for access + refresh tokens, fetches the Google account email, and
     * stores everything encrypted in [prefs].
     */
    suspend fun handleOAuthRedirect(
        uri: Uri,
        prefs: SecurePrefs
    ): AuthTokenResult = withContext(Dispatchers.IO) {
        val oauthError = uri.getQueryParameter("error")
        if (!oauthError.isNullOrBlank()) {
            prefs.pendingOAuthVerifier = ""
            prefs.pendingOAuthState = ""
            val desc = uri.getQueryParameter("error_description").orEmpty()
            val detail = if (desc.isNotBlank()) "$oauthError ($desc)" else oauthError
            return@withContext AuthTokenResult.Error(
                message = if (oauthError == "access_denied") {
                    "Google Sign-In permission was denied."
                } else {
                    "Google Sign-In error: $detail"
                },
                isConfigError = oauthError != "access_denied"
            )
        }

        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        val expectedState = prefs.pendingOAuthState
        val verifier = prefs.pendingOAuthVerifier

        // Clear one-time PKCE state immediately
        prefs.pendingOAuthVerifier = ""
        prefs.pendingOAuthState = ""

        if (code.isNullOrBlank()) {
            return@withContext AuthTokenResult.Error(
                "Google Sign-In did not return an authorization code. Please try again."
            )
        }
        if (expectedState.isBlank() || returnedState != expectedState || verifier.isBlank()) {
            return@withContext AuthTokenResult.Error(
                "Sign-In session expired or mismatched. Please tap Sign In again."
            )
        }

        try {
            val form = FormBody.Builder()
                .add("client_id", ANDROID_CLIENT_ID)
                .add("code", code)
                .add("code_verifier", verifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val req = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(form)
                .build()

            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (!resp.isSuccessful || json == null) {
                    val err = json?.optString("error").orEmpty()
                    val desc = json?.optString("error_description").orEmpty()
                    val detail = listOf(err, desc).filter { it.isNotBlank() }.joinToString(": ")
                        .ifBlank { "HTTP ${resp.code}" }
                    return@withContext AuthTokenResult.Error(
                        "Token exchange failed ($detail).",
                        isConfigError = true
                    )
                }

                val accessToken = json.optString("access_token")
                if (accessToken.isBlank()) {
                    return@withContext AuthTokenResult.Error("Google returned an empty access token.")
                }
                val expiresInSec = json.optLong("expires_in", 3600L).coerceAtLeast(120L)
                val refreshToken = json.optString("refresh_token")

                prefs.googleAccessToken = accessToken
                prefs.googleTokenExpiresAtMs = System.currentTimeMillis() + (expiresInSec - 60L) * 1000L
                if (refreshToken.isNotBlank()) {
                    prefs.googleRefreshToken = refreshToken
                }

                val email = fetchUserEmail(accessToken)
                    ?: prefs.googleEmail?.takeIf { it.isNotBlank() }
                    ?: "Google Account"
                prefs.googleEmail = email

                AuthTokenResult.Success(token = accessToken, email = email)
            }
        } catch (e: Exception) {
            AuthTokenResult.Error("Network error during Google Sign-In: ${e.message}")
        }
    }

    /**
     * Returns a valid OAuth 2.0 Bearer access token from encrypted [prefs], silently refreshing
     * it with the stored refresh token whenever expired (works both in foreground and WorkManager).
     */
    suspend fun fetchAccessToken(prefs: SecurePrefs): AuthTokenResult = withContext(Dispatchers.IO) {
        val email = prefs.googleEmail?.takeIf { it.isNotBlank() }
        val cachedToken = prefs.googleAccessToken
        val expiresAt = prefs.googleTokenExpiresAtMs

        if (cachedToken.isNotBlank() && System.currentTimeMillis() < expiresAt) {
            return@withContext AuthTokenResult.Success(
                token = cachedToken,
                email = email ?: fetchUserEmail(cachedToken) ?: "Google Account"
            )
        }

        val refreshToken = prefs.googleRefreshToken
        if (refreshToken.isBlank()) {
            return@withContext AuthTokenResult.NotSignedIn
        }

        try {
            val form = FormBody.Builder()
                .add("client_id", ANDROID_CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val req = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(form)
                .build()

            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (!resp.isSuccessful || json == null) {
                    val err = json?.optString("error").orEmpty()
                    if (err == "invalid_grant") {
                        prefs.clearGoogleSession()
                        return@withContext AuthTokenResult.NotSignedIn
                    }
                    val desc = json?.optString("error_description").orEmpty()
                    val detail = listOf(err, desc).filter { it.isNotBlank() }.joinToString(": ")
                        .ifBlank { "HTTP ${resp.code}" }
                    return@withContext AuthTokenResult.Error("Token refresh failed ($detail).")
                }

                val newAccess = json.optString("access_token")
                if (newAccess.isBlank()) {
                    return@withContext AuthTokenResult.Error("Google returned an empty refreshed token.")
                }
                val expiresInSec = json.optLong("expires_in", 3600L).coerceAtLeast(120L)
                val rotatedRefresh = json.optString("refresh_token")

                prefs.googleAccessToken = newAccess
                prefs.googleTokenExpiresAtMs = System.currentTimeMillis() + (expiresInSec - 60L) * 1000L
                if (rotatedRefresh.isNotBlank()) {
                    prefs.googleRefreshToken = rotatedRefresh
                }

                val resolvedEmail = email ?: fetchUserEmail(newAccess) ?: "Google Account"
                prefs.googleEmail = resolvedEmail
                AuthTokenResult.Success(token = newAccess, email = resolvedEmail)
            }
        } catch (e: Exception) {
            AuthTokenResult.Error("Unable to refresh Google token: ${e.message}")
        }
    }

    /**
     * Best-effort revocation of stored OAuth token on Sign Out.
     */
    suspend fun revokeTokens(prefs: SecurePrefs) = withContext(Dispatchers.IO) {
        val tokenToRevoke = prefs.googleRefreshToken.ifBlank { prefs.googleAccessToken }
        if (tokenToRevoke.isBlank()) return@withContext
        runCatching {
            val form = FormBody.Builder()
                .add("token", tokenToRevoke)
                .build()
            val req = Request.Builder()
                .url(REVOKE_ENDPOINT)
                .post(form)
                .build()
            http.newCall(req).execute().close()
        }
    }

    suspend fun fetchUserEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(USERINFO_ENDPOINT)
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

    /**
     * Reads the SHA-1 signing certificate fingerprint directly from this installed APK.
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

    private fun randomBase64Url(numBytes: Int): String {
        val bytes = ByteArray(numBytes)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sha256Base64Url(input: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
