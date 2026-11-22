package `in`.potr.bonsai.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AuthTokenResult {
    data class Success(val token: String, val email: String) : AuthTokenResult
    data object NotSignedIn : AuthTokenResult
    data class NeedsConsent(val recoveryIntent: Intent) : AuthTokenResult
    data class Error(val message: String) : AuthTokenResult
}

object GoogleAuthTokenProvider {

    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_SHEETS = "https://www.googleapis.com/auth/spreadsheets"
    const val SCOPE_YOUTUBE_UPLOAD = "https://www.googleapis.com/auth/youtube.upload"

    private const val OAUTH_SCOPE_STRING =
        "oauth2:$SCOPE_DRIVE_FILE $SCOPE_SHEETS $SCOPE_YOUTUBE_UPLOAD"

    fun buildSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(SCOPE_DRIVE_FILE),
                Scope(SCOPE_SHEETS),
                Scope(SCOPE_YOUTUBE_UPLOAD)
            )
            .build()

    suspend fun fetchAccessToken(context: Context): AuthTokenResult = withContext(Dispatchers.IO) {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val androidAccount = lastAccount?.account ?: return@withContext AuthTokenResult.NotSignedIn
        val email = lastAccount.email.orEmpty()
        try {
            val token = GoogleAuthUtil.getToken(context, androidAccount, OAUTH_SCOPE_STRING)
            AuthTokenResult.Success(token = token, email = email)
        } catch (recoverable: UserRecoverableAuthException) {
            val intent = recoverable.intent
            if (intent != null) {
                AuthTokenResult.NeedsConsent(intent)
            } else {
                AuthTokenResult.Error("Google authorization required — please sign in again")
            }
        } catch (e: Exception) {
            AuthTokenResult.Error(e.message ?: "Unable to fetch Google access token")
        }
    }
}
