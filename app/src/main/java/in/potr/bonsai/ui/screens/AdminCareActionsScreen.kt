package `in`.potr.bonsai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.potr.bonsai.data.model.CareActionCategories
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCareActionsScreen(
    careActions: List<CareActionEntity>,
    onBack: () -> Unit,
    onSaveAction: (existingId: String?, title: String, category: String, defaultDays: Int, description: String) -> Unit,
    onDeleteAction: (CareActionEntity) -> Unit,
    onRestoreDefaults: () -> Unit
) {
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var editingAction by remember { mutableStateOf<CareActionEntity?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CareActionEntity?>(null) }

    val categories = remember(careActions) {
        val present = careActions.map { it.category }.distinct()
        listOf("All") + CareActionCategories.all.filter { it in present } +
            present.filter { it !in CareActionCategories.all }
    }

    val filtered = remember(careActions, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        careActions.filter { action ->
            val catMatch = selectedCategory == "All" || action.category == selectedCategory
            val qMatch = q.isEmpty() ||
                action.title.lowercase().contains(q) ||
                action.category.lowercase().contains(q) ||
                action.description.lowercase().contains(q)
            catMatch && qMatch
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_actions_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Global Care Actions",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Admin Library • ${careActions.size} bonsai actions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRestoreDefaults,
                        modifier = Modifier.testTag("restore_default_actions_button")
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = "Restore missing default bonsai actions",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingAction = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Action") },
                modifier = Modifier.testTag("fab_add_care_action")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Every action in this global library is available for all plants. Default days serve as a starting suggestion — you can adjust the exact number of days whenever you schedule an action on a specimen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search pruning, wiring, repotting, feeding…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_actions_search_input")
                )
            }

            item {
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
                            label = { Text(cat) }
                        )
                    }
                }
            }

            items(filtered, key = { it.id }) { action ->
                CareActionLibraryCard(
                    action = action,
                    onEdit = {
                        editingAction = action
                        showAddEditDialog = true
                    },
                    onDelete = { pendingDelete = action }
                )
            }
        }
    }

    if (showAddEditDialog) {
        AddEditCareActionDialog(
            editing = editingAction,
            onDismiss = {
                showAddEditDialog = false
                editingAction = null
            },
            onSave = { title, category, days, description ->
                onSaveAction(editingAction?.id, title, category, days, description)
                showAddEditDialog = false
                editingAction = null
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove Care Action?") },
            text = {
                Text("Remove \"${target.title}\" from the global library? Existing reminders already scheduled on specimens will not be affected.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAction(target)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CareActionLibraryCard(
    action: CareActionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = action.category,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        StatusPill(
                            text = "Default: ${action.defaultDays}d",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit ${action.title}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete ${action.title}",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (action.description.isNotBlank()) {
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditCareActionDialog(
    editing: CareActionEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, category: String, defaultDays: Int, description: String) -> Unit
) {
    var title by remember(editing) { mutableStateOf(editing?.title.orEmpty()) }
    var category by remember(editing) { mutableStateOf(editing?.category ?: CareActionCategories.CUSTOM) }
    var defaultDaysText by remember(editing) { mutableStateOf((editing?.defaultDays ?: 30).toString()) }
    var description by remember(editing) { mutableStateOf(editing?.description.orEmpty()) }
    var catMenuExpanded by remember { mutableStateOf(false) }

    val parsedDays = defaultDaysText.toIntOrNull()
    val canSave = title.isNotBlank() && parsedDays != null && parsedDays in 1..3650

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editing == null) "Add Global Care Action" else "Edit Care Action",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Action Name *") },
                    placeholder = { Text("e.g., Monsoon Root Flush") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("care_action_title_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = catMenuExpanded,
                        onExpandedChange = { catMenuExpanded = it },
                        modifier = Modifier.weight(1.4f)
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = catMenuExpanded,
                            onDismissRequest = { catMenuExpanded = false }
                        ) {
                            CareActionCategories.all.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        category = option
                                        catMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = defaultDaysText,
                        onValueChange = { defaultDaysText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Default Days *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("care_action_days_input")
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Care Guidance / Tip") },
                    placeholder = { Text("Short note on technique or timing") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            if (parsedDays != null) {
                                onSave(title.trim(), category, parsedDays, description.trim())
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_care_action_button")
                    ) {
                        Text(if (editing == null) "Add Action" else "Save")
                    }
                }
            }
        }
    }
}
