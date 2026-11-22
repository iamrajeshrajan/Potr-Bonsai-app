package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.ScheduledActionEntity
import `in`.potr.bonsai.data.model.SpecimenEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLogDialog(
    specimen: SpecimenEntity,
    action: ScheduledActionEntity,
    careActions: List<CareActionEntity>,
    beforeVideoPath: String?,
    afterVideoPath: String?,
    onPickBeforeVideo: () -> Unit,
    onPickAfterVideo: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (notes: String, followUpTitle: String?, followUpDays: Int?) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var followUpExpanded by remember { mutableStateOf(false) }

    val defaultFollowUp = remember(careActions, action.title) {
        careActions.firstOrNull { it.title.equals(action.title, ignoreCase = true) }
            ?: careActions.firstOrNull { it.title.contains("Root", ignoreCase = true) }
            ?: careActions.firstOrNull()
    }
    var selectedFollowUp by remember(defaultFollowUp) {
        mutableStateOf<CareActionEntity?>(defaultFollowUp)
    }
    var followUpDaysText by remember(defaultFollowUp, action.intervalDays) {
        mutableStateOf((defaultFollowUp?.defaultDays ?: action.intervalDays).toString())
    }

    val followUpDaysValid = selectedFollowUp == null || (followUpDaysText.toIntOrNull()?.let { it in 0..3650 } == true)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            specimen.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Action: ${action.title}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Videos are saved locally immediately so you can work offline in the garden, then uploaded to YouTube in the background once online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VideoPickBox(
                            label = "Before Video",
                            isAttached = !beforeVideoPath.isNullOrBlank(),
                            onPick = onPickBeforeVideo,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_before_video_button")
                        )
                        VideoPickBox(
                            label = "After Video",
                            isAttached = !afterVideoPath.isNullOrBlank(),
                            onPick = onPickAfterVideo,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_after_video_button")
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Care Notes & Observations") },
                        placeholder = { Text("Removed crossing interior shoots, sealed primary cut with paste...") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("action_notes_input")
                    )

                    // Global follow-up action selector + editable plant-specific days
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = followUpExpanded,
                            onExpandedChange = { followUpExpanded = it },
                            modifier = Modifier.weight(1.6f)
                        ) {
                            OutlinedTextField(
                                value = selectedFollowUp?.title ?: "No follow-up reminder",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Next Care Task") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = followUpExpanded) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = followUpExpanded,
                                onDismissRequest = { followUpExpanded = false },
                                modifier = Modifier.heightIn(max = 260.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("No follow-up reminder") },
                                    onClick = {
                                        selectedFollowUp = null
                                        followUpExpanded = false
                                    }
                                )
                                careActions.forEach { globalAction ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(globalAction.title)
                                                Text(
                                                    "${globalAction.category} • Default ${globalAction.defaultDays}d",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedFollowUp = globalAction
                                            followUpDaysText = globalAction.defaultDays.toString()
                                            followUpExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedFollowUp != null) {
                            OutlinedTextField(
                                value = followUpDaysText,
                                onValueChange = { followUpDaysText = it.filter(Char::isDigit).take(4) },
                                label = { Text("Days") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.75f)
                                    .testTag("followup_days_input")
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = followUpDaysValid,
                        onClick = {
                            onSubmit(
                                notes,
                                selectedFollowUp?.title,
                                if (selectedFollowUp != null) followUpDaysText.toIntOrNull() else null
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("complete_action_button")
                    ) {
                        Text("Complete & Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPickBox(
    label: String,
    isAttached: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isAttached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = onPick,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isAttached) Icons.Default.CheckCircle else Icons.Default.Videocam,
                    contentDescription = null,
                    tint = if (isAttached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isAttached) "Ready ✓" else "Select",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
