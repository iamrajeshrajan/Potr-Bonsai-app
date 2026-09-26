package `in`.potr.bonsai.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.potr.bonsai.data.Api
import `in`.potr.bonsai.data.AppState
import `in`.potr.bonsai.ui.components.TextInput
import `in`.potr.bonsai.util.scanQr
import kotlinx.coroutines.launch

/** Parses the setup QR shown in the Team screen: potr-bonsai://setup?server=...&key=... */
fun parseSetupQr(text: String): Pair<String, String>? {
    val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
    if (uri.scheme != "potr-bonsai" || uri.host != "setup") return null
    val server = uri.getQueryParameter("server") ?: return null
    val key = uri.getQueryParameter("key") ?: return null
    return server to key
}

fun setupQrText(server: String, key: String): String =
    Uri.Builder().scheme("potr-bonsai").authority("setup")
        .appendQueryParameter("server", server)
        .appendQueryParameter("key", key)
        .build().toString()

@Composable
fun SetupScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by rememberSaveable { mutableStateOf(state.lastServerUrl) }
    var key by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun connect(s: String, k: String) {
        busy = true
        error = null
        scope.launch {
            try {
                val user = Api(s, k).me()
                state.signIn(s.trim().trimEnd('/'), k.trim(), user)
            } catch (e: Exception) {
                error = e.message ?: "Could not connect"
            } finally {
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Potr Bonsai Master", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in with the setup QR code from an admin (More → Team), or enter the server address and your access key.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    scope.launch {
                        val scanned = runCatching { scanQr(context) }.getOrNull() ?: return@launch
                        val parsed = parseSetupQr(scanned)
                        if (parsed == null) {
                            error = "That is not a Potr setup code"
                        } else {
                            server = parsed.first
                            key = parsed.second
                            connect(parsed.first, parsed.second)
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text("  Scan setup QR")
            }
            TextInput("Server", server, { server = it })
            TextInput("Access key", key, { key = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(
                onClick = { connect(server, key) },
                enabled = !busy && server.isNotBlank() && key.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Connecting…" else "Sign in") }
        }
    }
}
