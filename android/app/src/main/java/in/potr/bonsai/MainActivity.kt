package `in`.potr.bonsai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import `in`.potr.bonsai.data.AppState
import `in`.potr.bonsai.ui.PotrApp
import `in`.potr.bonsai.ui.theme.PotrTheme

class MainActivity : ComponentActivity() {
    /** Certificate link that opened the app (https://host/c/CODE), handled once signed in. */
    private val pendingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = AppState(applicationContext)
        if (savedInstanceState == null) pendingLink.value = intent?.dataString
        setContent {
            PotrTheme {
                PotrApp(state, pendingLink.value, onLinkHandled = { pendingLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { pendingLink.value = it }
    }
}
