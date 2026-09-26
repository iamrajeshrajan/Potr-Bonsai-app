package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Simple string-keyed form state that survives rotation. */
typealias FormState = SnapshotStateMap<String, String>

val FormSaver = Saver<FormState, HashMap<String, String>>(
    save = { HashMap(it) },
    restore = { saved -> mutableStateMapOf<String, String>().apply { putAll(saved) } },
)

fun FormState.text(key: String) = this[key].orEmpty()

/** Converts form fields into a JSON body; blank values become null (clears the field). */
fun FormState.toJson(textKeys: List<String>, numberKeys: List<String>, intKeys: List<String> = emptyList()): Map<String, kotlinx.serialization.json.JsonElement> =
    buildMap {
        textKeys.forEach { k -> put(k, text(k).trim().ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull) }
        numberKeys.forEach { k -> put(k, text(k).trim().replace(',', '.').toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonNull) }
        intKeys.forEach { k -> put(k, text(k).trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull) }
    }

fun jsonOf(map: Map<String, kotlinx.serialization.json.JsonElement>) = JsonObject(map)

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
