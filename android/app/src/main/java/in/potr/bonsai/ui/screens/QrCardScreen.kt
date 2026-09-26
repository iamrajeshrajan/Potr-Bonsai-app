package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import `in`.potr.bonsai.data.Meta
import `in`.potr.bonsai.ui.LocalSession
import `in`.potr.bonsai.ui.components.Load
import `in`.potr.bonsai.ui.components.rememberLoader
import `in`.potr.bonsai.util.openUrl
import `in`.potr.bonsai.util.qrBitmap
import `in`.potr.bonsai.util.shareBitmap

/**
 * The QR printed on the pot card points at the public certificate URL, so
 * customers can scan it with any phone, and staff phones open it in this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNCHECKED_CAST")
fun QrCardScreen(nav: NavHostController, code: String) {
    val session = LocalSession.current
    val context = LocalContext.current
    // Public links use the server's configured public address (e.g. a custom domain).
    val meta = rememberLoader { session.api.meta() }
    val base = ((meta.state as? Load.Ok<Meta>)?.value?.publicBaseUrl ?: session.serverUrl).trimEnd('/')
    val url = "$base/c/$code"
    val bitmap = remember(url) { qrBitmap(url) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR card") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(color = Color.White, shape = MaterialTheme.shapes.large, shadowElevation = 2.dp) {
                Image(bitmap.asImageBitmap(), contentDescription = "QR code for $code", modifier = Modifier.size(260.dp).padding(12.dp))
            }
            Text(code, style = MaterialTheme.typography.titleLarge)
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { openUrl(context, "$base/card/$code") }, modifier = Modifier.fillMaxWidth()) {
                Text("Print card (85 × 55 mm)")
            }
            OutlinedButton(onClick = { shareBitmap(context, bitmap, code, url) }, modifier = Modifier.fillMaxWidth()) {
                Text("Share QR image")
            }
            OutlinedButton(onClick = { openUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
                Text("View certificate")
            }
            Text(
                "Print opens the card sheet in your browser; use Share → Print, or send it to the shop printer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
