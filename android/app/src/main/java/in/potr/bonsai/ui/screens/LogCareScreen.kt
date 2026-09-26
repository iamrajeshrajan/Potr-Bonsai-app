package `in`.potr.bonsai.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import `in`.potr.bonsai.ui.LocalSession
import `in`.potr.bonsai.ui.components.DateInput
import `in`.potr.bonsai.ui.components.Dropdown
import `in`.potr.bonsai.ui.components.PhotoButtons
import `in`.potr.bonsai.ui.components.TextInput
import `in`.potr.bonsai.ui.components.rememberPhotoLauncher
import `in`.potr.bonsai.util.MANUAL_EVENT_TYPES
import `in`.potr.bonsai.util.compressForUpload
import `in`.potr.bonsai.util.eventLabel
import `in`.potr.bonsai.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Extra structured fields captured per care type (stored in the event's details). */
private val DETAIL_FIELDS: Map<String, List<Pair<String, String>>> = mapOf(
    "repotting" to listOf("soil_mix" to "Soil mix", "root_work" to "Root work"),
    "wiring" to listOf("wire" to "Wire (e.g. 2 mm aluminium)", "branches" to "Branches wired"),
    "fertilizing" to listOf("fertilizer" to "Fertiliser", "dose" to "Dose"),
    "pest_treatment" to listOf("issue" to "Pest / disease", "treatment" to "Treatment"),
    "pruning" to listOf("kind" to "Kind (maintenance, structural, root)"),
    "measurement" to listOf("height_cm" to "Height (cm)", "trunk_cm" to "Trunk (cm)", "spread_cm" to "Spread (cm)"),
    "award" to listOf("award" to "Award", "event" to "Exhibition / event"),
)
private val NUMERIC = setOf("height_cm", "trunk_cm", "spread_cm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCareScreen(nav: NavHostController, bonsaiId: String, taskId: String?) {
    val session = LocalSession.current
    val api = session.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf("pruning") }
    var date by remember { mutableStateOf(today()) }
    var notes by remember { mutableStateOf("") }
    var by by remember { mutableStateOf(session.user.name) }
    var isPublic by remember { mutableStateOf(true) }
    val details = remember { mutableStateOf(mapOf<String, String>()) }
    val photos = remember { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var taskTitle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(taskId) {
        if (taskId == null) return@LaunchedEffect
        runCatching { api.tasks(bonsaiId).firstOrNull { it.id == taskId } }.getOrNull()?.let { t ->
            if (t.type in MANUAL_EVENT_TYPES) type = t.type else type = "note"
            taskTitle = t.title ?: eventLabel(t.type)
            notes = t.title ?: t.notes ?: ""
        }
    }
    val launcher = rememberPhotoLauncher { photos.add(it) }

    fun save() {
        busy = true
        error = null
        scope.launch {
            try {
                val photoIds = photos.map { uri ->
                    val bytes = withContext(Dispatchers.IO) { compressForUpload(context, uri) }
                    api.uploadPhoto("bonsai", bonsaiId, bytes, setCover = false).id
                }
                val body = buildJsonObject {
                    put("type", type)
                    put("performed_on", date)
                    put("performed_by", by)
                    if (notes.isNotBlank()) put("notes", notes.trim())
                    put("is_public", isPublic)
                    put("set_cover", photoIds.isNotEmpty())
                    put("photo_ids", buildJsonArray { photoIds.forEach { add(JsonPrimitive(it)) } })
                    put("details", buildJsonObject {
                        DETAIL_FIELDS[type].orEmpty().forEach { (key, _) ->
                            val v = details.value[key]?.trim().orEmpty()
                            if (v.isNotEmpty()) {
                                if (key in NUMERIC) v.toDoubleOrNull()?.let { put(key, it) } else put(key, v)
                            }
                        }
                    })
                }
                if (taskId != null) api.completeTask(taskId, body) else api.addEvent(bonsaiId, body)
                nav.popBackStack()
            } catch (e: Exception) {
                error = e.message ?: "Could not save"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(taskTitle?.let { "Complete: $it" } ?: "Log care") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Dropdown("What was done", type, MANUAL_EVENT_TYPES.associateWith(::eventLabel), { type = it }, allowEmpty = false)
            DateInput("Date", date, { date = it })
            DETAIL_FIELDS[type].orEmpty().forEach { (key, label) ->
                TextInput(label, details.value[key].orEmpty(), { details.value = details.value + (key to it) }, number = key in NUMERIC)
            }
            TextInput("Notes", notes, { notes = it }, singleLine = false)
            TextInput("Done by", by, { by = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show on certificate")
                    Text("Private entries stay in the internal record only.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = isPublic, onCheckedChange = { isPublic = it })
            }
            Text("Photos (${photos.size})", style = MaterialTheme.typography.titleSmall)
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            PhotoButtons(launcher)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Saving…" else "Save to history")
            }
            Text(
                "History entries are permanent and sealed. Mistakes are fixed by adding a correction.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
