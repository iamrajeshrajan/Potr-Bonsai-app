package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import `in`.potr.bonsai.data.User
import `in`.potr.bonsai.ui.LocalSession
import `in`.potr.bonsai.ui.components.Dropdown
import `in`.potr.bonsai.ui.components.Load
import `in`.potr.bonsai.ui.components.Section
import `in`.potr.bonsai.ui.components.TextInput
import `in`.potr.bonsai.ui.components.rememberLoader
import `in`.potr.bonsai.util.qrBitmap
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Admin-only: add staff and hand them a setup QR to scan on their phone. */
@Composable
fun TeamSection() {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { session.api.users() }
    var adding by remember { mutableStateOf(false) }
    var showKeyFor by remember { mutableStateOf<Pair<String, String>?>(null) } // name to key
    var error by remember { mutableStateOf<String?>(null) }

    Section("Team") {
        Text(
            "Each person gets their own access key. Lost phone? Issue a new key; the old one stops working.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val s = loader.state) {
            is Load.Ok -> s.value.forEach { u ->
                UserRow(
                    u,
                    isMe = u.id == session.user.id,
                    onToggle = { active ->
                        scope.launch {
                            runCatching { session.api.updateUser(u.id, buildJsonObject { put("active", active) }) }
                                .onFailure { error = it.message }
                            loader.reload()
                        }
                    },
                    onNewKey = {
                        scope.launch {
                            runCatching { session.api.rotateKey(u.id) }
                                .onSuccess { showKeyFor = u.name to it.accessKey }
                                .onFailure { error = it.message }
                        }
                    },
                )
            }
            is Load.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
            Load.Loading -> Text("Loading…")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { adding = true }) { Text("Add team member") }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("staff") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add team member") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput("Name", name, { name = it })
                    Dropdown("Role", role, mapOf("staff" to "Staff", "admin" to "Admin"), { role = it }, allowEmpty = false)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        runCatching {
                            session.api.createUser(buildJsonObject {
                                put("name", name.trim())
                                put("role", role)
                            })
                        }.onSuccess {
                            adding = false
                            showKeyFor = it.name to (it.accessKey ?: "")
                            loader.reload()
                        }.onFailure { error = it.message }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }

    showKeyFor?.let { (name, key) ->
        val qr = remember(key) { qrBitmap(setupQrText(session.serverUrl, key), 640) }
        AlertDialog(
            onDismissRequest = { showKeyFor = null },
            title = { Text("Setup code for $name") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("On their phone: open Potr Bonsai → Scan setup QR. This code is shown only once.")
                    Image(qr.asImageBitmap(), contentDescription = "Setup QR", modifier = Modifier.size(240.dp))
                    Text(key, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showKeyFor = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun UserRow(u: User, isMe: Boolean, onToggle: (Boolean) -> Unit, onNewKey: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(u.name + if (isMe) " (you)" else "")
            Text(
                u.role + (u.lastSeenAt?.let { " · last seen ${it.take(10)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isMe) {
            TextButton(onClick = onNewKey) { Text("New key") }
            Switch(checked = u.active != 0, onCheckedChange = onToggle)
        }
    }
}
