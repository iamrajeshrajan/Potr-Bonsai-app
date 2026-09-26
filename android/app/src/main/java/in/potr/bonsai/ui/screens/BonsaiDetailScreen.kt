package `in`.potr.bonsai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import `in`.potr.bonsai.data.BonsaiDetail
import `in`.potr.bonsai.data.CareEvent
import `in`.potr.bonsai.ui.LocalSession
import `in`.potr.bonsai.ui.Routes
import `in`.potr.bonsai.ui.components.Badge
import `in`.potr.bonsai.ui.components.DateInput
import `in`.potr.bonsai.ui.components.Dropdown
import `in`.potr.bonsai.ui.components.Fact
import `in`.potr.bonsai.ui.components.Load
import `in`.potr.bonsai.ui.components.LoadContent
import `in`.potr.bonsai.ui.components.Section
import `in`.potr.bonsai.ui.components.TextInput
import `in`.potr.bonsai.ui.components.rememberLoader
import `in`.potr.bonsai.ui.components.rememberPhotoLauncher
import `in`.potr.bonsai.util.BONSAI_STATUS_LABELS
import `in`.potr.bonsai.util.ORIGIN_LABELS
import `in`.potr.bonsai.util.TASK_TYPES
import `in`.potr.bonsai.util.compressForUpload
import `in`.potr.bonsai.util.eventLabel
import `in`.potr.bonsai.util.formatDate
import `in`.potr.bonsai.util.formatInr
import `in`.potr.bonsai.util.formatNumber
import `in`.potr.bonsai.util.openUrl
import `in`.potr.bonsai.util.shareText
import `in`.potr.bonsai.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BonsaiDetailScreen(nav: NavHostController, ref: String) {
    val session = LocalSession.current
    val api = session.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(ref) { api.bonsai(ref) }
    var busy by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    @Suppress("UNCHECKED_CAST")
    val detail = (loader.state as? Load.Ok<BonsaiDetail>)?.value
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // New photo -> upload -> "Photo update" event (becomes the cover).
    val photos = rememberPhotoLauncher { uri ->
        val b = detail?.bonsai ?: return@rememberPhotoLauncher
        busy = "Uploading photo…"
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { compressForUpload(context, uri) }
                val photo = api.uploadPhoto("bonsai", b.id, bytes, setCover = true)
                api.addEvent(b.id, buildJsonObject {
                    put("type", "photo")
                    put("performed_on", today())
                    put("photo_ids", buildJsonArray { add(JsonPrimitive(photo.id)) })
                })
                loader.reload()
            } catch (e: Exception) {
                toast(e.message ?: "Upload failed")
            } finally {
                busy = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.bonsai?.code ?: "Bonsai") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = { nav.navigate(Routes.qr(detail.bonsai.code)) }) { Icon(Icons.Filled.QrCode2, "QR card") }
                        IconButton(onClick = { nav.navigate(Routes.bonsaiEdit(detail.bonsai.id)) }) { Icon(Icons.Filled.Edit, "Edit") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Open certificate") }, onClick = {
                                menu = false
                                openUrl(context, detail.bonsai.certificateUrl)
                            })
                            DropdownMenuItem(text = { Text("Share certificate link") }, onClick = {
                                menu = false
                                shareText(context, "${detail.bonsai.title} (${detail.bonsai.code}) – ${detail.bonsai.certificateUrl}")
                            })
                            DropdownMenuItem(text = { Text("Sync to shop now") }, onClick = {
                                menu = false
                                scope.launch {
                                    busy = "Syncing with potr.in…"
                                    runCatching { api.syncBonsai(detail.bonsai.id) }
                                        .onSuccess { toast(if (it.status == "error") "Shop sync failed: ${it.error}" else "Shop: ${it.status}") }
                                        .onFailure { toast(it.message ?: "Sync failed") }
                                    busy = null
                                    loader.reload()
                                }
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            busy?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
            LoadContent(loader) { d ->
                BonsaiDetailContent(
                    d,
                    nav,
                    onAddPhoto = photos.takePhoto,
                    onPickPhoto = photos.pickFromGallery,
                    onLogCare = { nav.navigate(Routes.logCare(d.bonsai.id)) },
                    onSchedule = { scheduling = true },
                    onCancelTask = { id ->
                        scope.launch {
                            runCatching { api.cancelTask(id) }.onFailure { toast(it.message ?: "Failed") }
                            loader.reload()
                        }
                    },
                )
            }
        }
    }

    if (scheduling && detail != null) {
        ScheduleDialog(
            onDismiss = { scheduling = false },
            onSave = { body ->
                scope.launch {
                    runCatching { api.createTask(buildJsonObject { put("bonsai_id", detail.bonsai.id); body.forEach { (k, v) -> put(k, v) } }) }
                        .onSuccess { scheduling = false; loader.reload() }
                        .onFailure { toast(it.message ?: "Could not schedule") }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BonsaiDetailContent(
    d: BonsaiDetail,
    nav: NavHostController,
    onAddPhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onLogCare: () -> Unit,
    onSchedule: () -> Unit,
    onCancelTask: (String) -> Unit,
) {
    val b = d.bonsai
    val now = today()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (b.coverPhotoUrl != null) {
                    AsyncImage(model = b.coverPhotoUrl, contentDescription = b.title, contentScale = ContentScale.Crop)
                } else {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = onAddPhoto) { Text("Add the first photo") }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(b.title, style = MaterialTheme.typography.headlineSmall)
                Text(b.speciesBotanical ?: b.speciesCommon, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(BONSAI_STATUS_LABELS[b.status] ?: b.status)
                    if (b.forSale) {
                        Badge("For sale · ${formatInr(b.priceInr)}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    if (b.wooSyncStatus == "error") {
                        Badge("Shop sync failed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    } else if (b.wooSyncStatus == "synced") {
                        Badge("On potr.in")
                    }
                }
                b.wooSyncError?.takeIf { b.wooSyncStatus == "error" }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAddPhoto) { Icon(Icons.Filled.AddAPhoto, null); Text("  Photo") }
                TextButton(onClick = onPickPhoto) { Text("From gallery") }
                FilledTonalButton(onClick = onLogCare) { Icon(Icons.Filled.EditNote, null); Text("  Log care") }
                FilledTonalButton(onClick = onSchedule) { Icon(Icons.Filled.EventAvailable, null); Text("  Schedule") }
            }
        }
        item {
            Section("Details") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val w = Modifier.width(140.dp)
                    Fact("Age", b.ageYears?.let { (if (b.ageBasis == "documented") "" else "approx. ") + "$it years" }, w)
                    Fact("Style", b.style, w)
                    Fact("Origin", listOfNotNull(ORIGIN_LABELS[b.originType], b.originLocation).joinToString(", "), w)
                    Fact("Source", b.sourceName, w)
                    Fact("Acquired", b.acquiredOn?.let(::formatDate), w)
                    Fact("Height", b.heightCm?.let { "${formatNumber(it)} cm" }, w)
                    Fact("Trunk", b.trunkCm?.let { "${formatNumber(it)} cm" }, w)
                    Fact("Spread", b.spreadCm?.let { "${formatNumber(it)} cm" }, w)
                    Fact("Location", b.location, w)
                    Fact("Cost", b.costInr?.let(::formatInr), w)
                }
                d.pot?.let { pot ->
                    TextButton(onClick = { nav.navigate(Routes.pot(pot.id)) }) { Text("Pot: ${pot.name} · ${pot.code}") }
                }
                b.description?.let { Text(it) }
                b.internalNotes?.let { Text("Internal: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (d.tasks.isNotEmpty()) {
            item {
                Section("Care due") {
                    d.tasks.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title ?: eventLabel(t.type))
                                Text(
                                    formatDate(t.dueOn) + (t.recurrenceDays?.let { " · every $it days" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (t.dueOn < now) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onCancelTask(t.id) }) { Text("Skip") }
                            TextButton(onClick = { nav.navigate(Routes.logCare(b.id, t.id)) }) { Text("Done") }
                        }
                    }
                }
            }
        }
        if (d.photos.size > 1) {
            item {
                Section("Growth photos") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(d.photos, key = { it.id }) { p ->
                            Column {
                                AsyncImage(
                                    model = p.url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Text(formatDate(p.takenOn ?: p.createdAt), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Section("History") {
                d.chain?.let { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (c.valid) Icons.Filled.VerifiedUser else Icons.Filled.Warning,
                            null,
                            tint = if (c.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            if (c.valid) "${c.length} records, sealed" else "History seal broken – contact admin",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                d.events.forEach { EventRow(it) }
            }
        }
    }
}

@Composable
private fun EventRow(e: CareEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(formatDate(e.performedOn), Modifier.width(92.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(eventLabel(e.type) + if (!e.isPublic) " (private)" else "", style = MaterialTheme.typography.bodyMedium)
            val details = e.details.entries
                .filter { it.key != "changes" }
                .joinToString(" · ") { (k, v) -> "${k.replace('_', ' ')}: ${(v as? JsonPrimitive)?.content ?: v}" }
            val summary = listOfNotNull(e.notes, details.ifBlank { null }, e.performedBy?.let { "by $it" }).joinToString(" · ")
            if (summary.isNotBlank()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScheduleDialog(onDismiss: () -> Unit, onSave: (Map<String, JsonPrimitive>) -> Unit) {
    var type by remember { mutableStateOf("pruning") }
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(today()) }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule care") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("Type", type, TASK_TYPES.associateWith(::eventLabel), { type = it }, allowEmpty = false)
                TextInput("Title (optional)", title, { title = it })
                DateInput("Due", due, { due = it })
                TextInput("Repeat every N days (optional)", repeat, { repeat = it.filter(Char::isDigit) }, number = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    buildMap {
                        put("type", JsonPrimitive(type))
                        put("due_on", JsonPrimitive(due))
                        if (title.isNotBlank()) put("title", JsonPrimitive(title.trim()))
                        repeat.toIntOrNull()?.let { put("recurrence_days", JsonPrimitive(it)) }
                    },
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
