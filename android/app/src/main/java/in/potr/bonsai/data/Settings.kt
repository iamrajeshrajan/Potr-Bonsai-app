package `in`.potr.bonsai.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import `in`.potr.bonsai.BuildConfig

/** Signed-in staff member plus the API client for their server. */
class Session(val serverUrl: String, val accessKey: String, val user: User) {
    val api = Api(serverUrl, accessKey)
}

/** Stores the server and access key in app-private preferences. */
class AppState(context: Context) {
    private val prefs = context.getSharedPreferences("potr", Context.MODE_PRIVATE)

    var session by mutableStateOf(load())
        private set

    val lastServerUrl: String
        get() = prefs.getString("server", null) ?: BuildConfig.DEFAULT_SERVER_URL

    private fun load(): Session? {
        val server = prefs.getString("server", null) ?: return null
        val key = prefs.getString("key", null) ?: return null
        val user = User(
            id = prefs.getString("user_id", "") ?: "",
            name = prefs.getString("user_name", "") ?: "",
            role = prefs.getString("user_role", "staff") ?: "staff",
        )
        return Session(server, key, user)
    }

    fun signIn(server: String, key: String, user: User) {
        prefs.edit()
            .putString("server", server)
            .putString("key", key)
            .putString("user_id", user.id)
            .putString("user_name", user.name)
            .putString("user_role", user.role)
            .apply()
        session = Session(server, key, user)
    }

    fun signOut() {
        prefs.edit().remove("key").remove("user_id").remove("user_name").remove("user_role").apply()
        session = null
    }
}
