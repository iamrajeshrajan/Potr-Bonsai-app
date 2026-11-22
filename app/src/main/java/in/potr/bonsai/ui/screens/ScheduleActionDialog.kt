package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.potr.bonsai.data.model.CareActionCategories
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.DueDateHelper
import `in`.potr.bonsai.ui.theme.SageBorder
import java.time.LocalDate

/**
 * Dialog allowing the user to pick any global bonsai care action (or type a custom one)
 * and set the exact number of days for this specific plant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleActionDialog(
    specimenName: String,
    specimenCode: String,
    careActions: List<CareActionEntity>,
    preselectedAction: CareActionEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, days: Int, note: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedTitle by remember(preselectedAction) {
        mutableStateOf(preselectedAction?.title.orEmpty())
    }
    var daysText by remember(preselectedAction) {
        mutableStateOf((preselectedAction?.defaultDays ?: 30).toString())
    }
    var note by remember { mutableStateOf("") }

    val categories = remember(careActions) {
        val present = careActions.map { it.category }.distinct()
        listOf("All") + CareActionCategories.all.filter { it in present } +
            present.filter { it !in CareActionCategories.all }
    }

    val filteredActions = remember(careActions, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        careActions.filter { action ->
            val catOk = selectedCategory == "All" || action.category == selectedCategory
            val qOk = q.isEmpty() ||
                action.title.lowercase().contains(q) ||
                action.category.lowercase().contains(q) ||
                action.description.lowercase().contains(q)
            catOk && qOk
        }
    }

    val parsedDays = daysText.toIntOrNull()
    val previewDate = remember(parsedDays) {
        if (parsedDays != null && parsedDays in 0..3650) {
            DueDateHelper.formatEpochDay(LocalDate.now().plusDays(parsedDays.toLong()).toEpochDay())
        } else {
            null
        }
    }
    val canConfirm = selectedTitle.isNotBlank() && parsedDays != null && parsedDays in 0..3650

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
                            text = "$specimenCode • $specimenName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Schedule Care Action",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search global bonsai actions…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_action_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Scrollable global action library list
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 210.dp)
                    ) {
                        if (filteredActions.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "No matching actions in library. You can type a custom action below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredActions, key = { it.id }) { action ->
                                    val isSelected = selectedTitle.equals(action.title, ignoreCase = true)
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) SageBorder else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedTitle = action.title
                                                daysText = action.defaultDays.toString()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = action.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    ),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${action.category} • Suggested ${action.defaultDays}d",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action name (editable / custom) + Plant-specific Days interval
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(
                            value = selectedTitle,
                            onValueChange = { selectedTitle = it },
                            label = { Text("Action *") },
                            placeholder = { Text("Pick above or type custom") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("schedule_action_title_input")
                        )
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { daysText = it.filter(Char::isDigit).take(4) },
                            label = { Text("Days *") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.75f)
                                .testTag("schedule_action_days_input")
                        )
                    }

                    // Quick day presets for fast bench entry
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0 to "Today", 7 to "7d", 14 to "14d", 21 to "21d", 30 to "30d", 45 to "45d", 60 to "60d", 90 to "90d").forEach { (d, label) ->
                            FilterChip(
                                selected = parsedDays == d,
                                onClick = { daysText = d.toString() },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (previewDate != null) {
                        Text(
                            text = if (parsedDays == 0) "Reminder due today ($previewDate)" else "Reminder due on $previewDate (+${parsedDays} days)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Optional Note for This Plant") },
                        placeholder = { Text("e.g., Check apex wire after monsoon flush") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canConfirm,
                        onClick = {
                            if (parsedDays != null) {
                                onConfirm(selectedTitle.trim(), parsedDays, note.trim())
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_schedule_action_button")
                    ) {
                        Text("Schedule Action")
                    }
                }
            }
        }
    }
}
