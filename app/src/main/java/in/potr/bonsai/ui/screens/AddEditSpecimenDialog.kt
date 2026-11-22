package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.SpecimenEntity
import `in`.potr.bonsai.data.model.ValuationCalculator
import `in`.potr.bonsai.ui.components.SpecimenPhoto
import `in`.potr.bonsai.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSpecimenDialog(
    editingSpecimen: SpecimenEntity?,
    suggestedCode: String,
    speciesSuggestions: List<String>,
    careActions: List<CareActionEntity>,
    stagedPhotoPath: String?,
    onRequestCameraPhoto: (code: String) -> Unit,
    onRequestGalleryPhoto: (code: String) -> Unit,
    onClearPhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSaveNew: (
        code: String,
        name: String,
        species: String,
        subspecies: String,
        age: Int,
        ageType: String,
        ageNote: String,
        pot: String,
        potMeta: String,
        localImagePath: String?,
        purchasePrice: Double,
        newPotPrice: Double,
        effortCost: Double,
        sellingPrice: Double,
        initialActionTitle: String?,
        initialActionDays: Int?
    ) -> Unit,
    onSaveEdit: (
        existing: SpecimenEntity,
        code: String,
        name: String,
        species: String,
        subspecies: String,
        age: Int,
        ageType: String,
        ageNote: String,
        pot: String,
        potMeta: String,
        repotted: String,
        localImagePath: String?,
        purchasePrice: Double,
        newPotPrice: Double,
        effortCost: Double,
        sellingPrice: Double
    ) -> Unit
) {
    val isEdit = editingSpecimen != null
    var name by remember { mutableStateOf(editingSpecimen?.name.orEmpty()) }
    var code by remember { mutableStateOf(editingSpecimen?.code ?: suggestedCode) }
    var species by remember { mutableStateOf(editingSpecimen?.species.orEmpty()) }
    var subspecies by remember { mutableStateOf(editingSpecimen?.subspecies.orEmpty()) }
    var ageText by remember { mutableStateOf((editingSpecimen?.age ?: 14).toString()) }
    var ageType by remember { mutableStateOf(editingSpecimen?.ageType ?: "approximate") }
    var ageNote by remember { mutableStateOf(editingSpecimen?.ageNote.orEmpty()) }
    var pot by remember { mutableStateOf(editingSpecimen?.pot.orEmpty()) }
    var potMeta by remember { mutableStateOf(editingSpecimen?.potMeta.orEmpty()) }
    var repotted by remember { mutableStateOf(editingSpecimen?.repotted.orEmpty()) }
    var purchaseText by remember { mutableStateOf((editingSpecimen?.purchasePrice?.toLong() ?: 4500L).toString()) }
    var newPotText by remember { mutableStateOf((editingSpecimen?.newPotPrice?.toLong() ?: 0L).toString()) }
    var effortText by remember { mutableStateOf((editingSpecimen?.effortCost?.toLong() ?: 1200L).toString()) }
    var sellingText by remember { mutableStateOf((editingSpecimen?.sellingPrice?.toLong() ?: 8500L).toString()) }

    var speciesMenuExpanded by remember { mutableStateOf(false) }
    var initialActionMenuExpanded by remember { mutableStateOf(false) }

    val defaultInitialAction = remember(careActions) {
        careActions.firstOrNull { it.title.contains("Maintenance", ignoreCase = true) }
            ?: careActions.firstOrNull()
    }
    var selectedInitialAction by remember(defaultInitialAction) {
        mutableStateOf<CareActionEntity?>(defaultInitialAction)
    }
    var initialDaysText by remember(defaultInitialAction) {
        mutableStateOf((defaultInitialAction?.defaultDays ?: 30).toString())
    }

    val filteredSpecies = remember(species, speciesSuggestions) {
        val q = species.trim().lowercase()
        if (q.isEmpty()) speciesSuggestions
        else speciesSuggestions.filter { it.lowercase().contains(q) }
    }

    val estimatedPrice = ValuationCalculator.estimatedCurrentPrice(
        purchaseText.toDoubleOrNull() ?: 0.0,
        newPotText.toDoubleOrNull() ?: 0.0,
        effortText.toDoubleOrNull() ?: 0.0
    )

    val effectivePhotoPath = stagedPhotoPath ?: editingSpecimen?.localImagePath
    val initialDaysValid = selectedInitialAction == null || (initialDaysText.toIntOrNull()?.let { it in 0..3650 } == true)
    val canSave = name.isNotBlank() && code.isNotBlank() && species.isNotBlank() && (isEdit || initialDaysValid)

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
                    Text(
                        text = if (isEdit) "Edit Bonsai Specimen" else "Catalog New Bonsai",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Photo capture / gallery row
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!effectivePhotoPath.isNullOrBlank()) {
                                SpecimenPhoto(
                                    imagePath = effectivePhotoPath,
                                    contentDescription = "Selected specimen photo",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (!stagedPhotoPath.isNullOrBlank()) {
                                        "Compressed 1600px JPEG ready ✓"
                                    } else {
                                        "Auto-compresses to 1600px JPEG & backs up to Drive"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onRequestCameraPhoto(code.ifBlank { suggestedCode }) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("add_photo_camera_button")
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Camera", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { onRequestGalleryPhoto(code.ifBlank { suggestedCode }) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("add_photo_gallery_button")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Gallery", style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (!stagedPhotoPath.isNullOrBlank()) {
                                        TextButton(onClick = onClearPhoto) {
                                            Text("Clear", color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Specimen Name *") },
                            placeholder = { Text("Monsoon Banyan") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("specimen_name_input")
                        )
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Code *") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("specimen_code_input")
                        )
                    }

                    // Free-text Species with helpful autocomplete suggestions (no schedule gate!)
                    ExposedDropdownMenuBox(
                        expanded = speciesMenuExpanded && filteredSpecies.isNotEmpty(),
                        onExpandedChange = { speciesMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = species,
                            onValueChange = {
                                species = it
                                speciesMenuExpanded = true
                            },
                            label = { Text("Species *") },
                            placeholder = { Text("e.g., Ficus microcarpa, Premna…") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speciesMenuExpanded) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("specimen_species_input")
                        )
                        ExposedDropdownMenu(
                            expanded = speciesMenuExpanded && filteredSpecies.isNotEmpty(),
                            onDismissRequest = { speciesMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 220.dp)
                        ) {
                            filteredSpecies.take(12).forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        species = suggestion
                                        speciesMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = subspecies,
                        onValueChange = { subspecies = it },
                        label = { Text("Subspecies / Cultivar") },
                        placeholder = { Text("var. retusa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ageText,
                            onValueChange = { ageText = it.filter(Char::isDigit) },
                            label = { Text("Age (Years) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = ageType == "accurate",
                                onClick = { ageType = "accurate" },
                                label = { Text("Exact") }
                            )
                            FilterChip(
                                selected = ageType == "approximate",
                                onClick = { ageType = "approximate" },
                                label = { Text("Approx.") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = ageNote,
                        onValueChange = { ageNote = it },
                        label = { Text("Provenance / Age Note") },
                        placeholder = { Text("e.g., Air-layer propagated 2012") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pot,
                        onValueChange = { pot = it },
                        label = { Text("Pot Details") },
                        placeholder = { Text("Handcrafted Terracotta Oval") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isEdit) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = potMeta,
                                onValueChange = { potMeta = it },
                                label = { Text("Pot Dimensions / Notes") },
                                singleLine = true,
                                modifier = Modifier.weight(1.3f)
                            )
                            OutlinedTextField(
                                value = repotted,
                                onValueChange = { repotted = it },
                                label = { Text("Repotted") },
                                singleLine = true,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }

                    // Valuation block
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pricing & Valuation (₹)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Est: ${ValuationCalculator.formatInr(estimatedPrice)}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = purchaseText,
                                    onValueChange = { purchaseText = it.filter(Char::isDigit) },
                                    label = { Text("Purchase") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newPotText,
                                    onValueChange = { newPotText = it.filter(Char::isDigit) },
                                    label = { Text("New Pot") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = effortText,
                                    onValueChange = { effortText = it.filter(Char::isDigit) },
                                    label = { Text("Materials & Effort") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = sellingText,
                                    onValueChange = { sellingText = it.filter(Char::isDigit) },
                                    label = { Text("Selling Price") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Initial Scheduled Action from Global Care Library + plant-specific Days (Add mode only)
                    if (!isEdit) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Initial Care Reminder (Optional)", style = MaterialTheme.typography.titleSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ExposedDropdownMenuBox(
                                        expanded = initialActionMenuExpanded,
                                        onExpandedChange = { initialActionMenuExpanded = it },
                                        modifier = Modifier.weight(1.6f)
                                    ) {
                                        OutlinedTextField(
                                            value = selectedInitialAction?.title ?: "No initial reminder",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Global Care Action") },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = initialActionMenuExpanded)
                                            },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = initialActionMenuExpanded,
                                            onDismissRequest = { initialActionMenuExpanded = false },
                                            modifier = Modifier.heightIn(max = 260.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("No initial reminder") },
                                                onClick = {
                                                    selectedInitialAction = null
                                                    initialActionMenuExpanded = false
                                                }
                                            )
                                            careActions.forEach { action ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(action.title)
                                                            Text(
                                                                "${action.category} • Default ${action.defaultDays}d",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedInitialAction = action
                                                        initialDaysText = action.defaultDays.toString()
                                                        initialActionMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (selectedInitialAction != null) {
                                        OutlinedTextField(
                                            value = initialDaysText,
                                            onValueChange = { initialDaysText = it.filter(Char::isDigit).take(4) },
                                            label = { Text("Days") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(0.75f)
                                                .testTag("initial_action_days_input")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            val ageInt = ageText.toIntOrNull() ?: 10
                            val p = purchaseText.toDoubleOrNull() ?: 0.0
                            val potP = newPotText.toDoubleOrNull() ?: 0.0
                            val eff = effortText.toDoubleOrNull() ?: 0.0
                            val sell = sellingText.toDoubleOrNull() ?: 0.0
                            if (editingSpecimen != null) {
                                onSaveEdit(
                                    editingSpecimen,
                                    code,
                                    name,
                                    species,
                                    subspecies,
                                    ageInt,
                                    ageType,
                                    ageNote,
                                    pot,
                                    potMeta,
                                    repotted,
                                    stagedPhotoPath,
                                    p,
                                    potP,
                                    eff,
                                    sell
                                )
                            } else {
                                onSaveNew(
                                    code,
                                    name,
                                    species,
                                    subspecies,
                                    ageInt,
                                    ageType,
                                    ageNote,
                                    pot,
                                    potMeta,
                                    stagedPhotoPath,
                                    p,
                                    potP,
                                    eff,
                                    sell,
                                    selectedInitialAction?.title,
                                    if (selectedInitialAction != null) initialDaysText.toIntOrNull() else null
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_specimen_button")
                    ) {
                        Text(if (isEdit) "Save Changes" else "Save to Catalog")
                    }
                }
            }
        }
    }
}
