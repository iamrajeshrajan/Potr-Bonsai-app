package `in`.potr.bonsai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.potr.bonsai.data.model.BotanicalTipsLibrary
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.DueDateHelper
import `in`.potr.bonsai.data.model.ScheduledActionEntity
import `in`.potr.bonsai.data.model.SpecimenWithDetails
import `in`.potr.bonsai.data.model.TimelineEntryEntity
import `in`.potr.bonsai.data.model.UploadStatus
import `in`.potr.bonsai.data.model.ValuationCalculator
import `in`.potr.bonsai.ui.components.CodeBadge
import `in`.potr.bonsai.ui.components.SpecimenPhoto
import `in`.potr.bonsai.ui.components.StatusPill
import `in`.potr.bonsai.ui.theme.JetBrainsMonoFamily
import `in`.potr.bonsai.ui.theme.SageBorder
import `in`.potr.bonsai.ui.theme.TerracottaBorder
import `in`.potr.bonsai.util.QrCodeGenerator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpecimenDetailScreen(
    item: SpecimenWithDetails,
    careActions: List<CareActionEntity>,
    onBack: () -> Unit,
    onEditSpecimen: () -> Unit,
    onTogglePublish: () -> Unit,
    onDeleteSpecimen: () -> Unit,
    onCapturePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onSavePricing: (Double, Double, Double, Double) -> Unit,
    onCycleTip: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyTag: (String) -> Unit,
    onScheduleAction: (title: String, days: Int, note: String) -> Unit,
    onDeleteScheduledAction: (String) -> Unit,
    onActionTask: (ScheduledActionEntity) -> Unit,
    onRetryYouTubeUploads: () -> Unit,
    onPlayLocalVideo: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val s = item.specimen
    val quickPickActions = remember(careActions) {
        val preferredIds = listOf(
            "ca-maint-prune",
            "ca-wire-bite",
            "ca-root-check",
            "ca-organic-feed",
            "ca-pest-inspect"
        )
        val prioritized = preferredIds.mapNotNull { id -> careActions.firstOrNull { it.id == id } }
        if (prioritized.size >= 4) prioritized else careActions.take(5)
    }
    val tip = remember(s.species, s.tipIndex) {
        BotanicalTipsLibrary.tipFor(s.species, s.tipIndex)
    }
    val qrPayload = remember(s.code, s.name) {
        "POTR|${s.code}|${s.name}|https://potr.in/?s=${s.code}"
    }
    val qrPreview = remember(qrPayload) {
        QrCodeGenerator.generateBitmap(qrPayload, 180)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var preselectedCareAction by remember { mutableStateOf<CareActionEntity?>(null) }
    var editingPrices by remember { mutableStateOf(false) }

    var purchaseText by remember(s.id, s.purchasePrice) { mutableStateOf(s.purchasePrice.toLong().toString()) }
    var potText by remember(s.id, s.newPotPrice) { mutableStateOf(s.newPotPrice.toLong().toString()) }
    var effortText by remember(s.id, s.effortCost) { mutableStateOf(s.effortCost.toLong().toString()) }
    var sellingText by remember(s.id, s.sellingPrice) { mutableStateOf(s.sellingPrice.toLong().toString()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to catalog")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = s.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = s.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEditSpecimen, modifier = Modifier.testTag("detail_edit_button")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit specimen")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("detail_delete_button")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete specimen",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero Photo + Photo Actions + Publish Bar
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                        ) {
                            SpecimenPhoto(
                                imagePath = s.localImagePath,
                                contentDescription = s.name,
                                modifier = Modifier.fillMaxSize()
                            )
                            CodeBadge(
                                code = s.code,
                                dark = true,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            )
                            if (!s.drivePhotoFileId.isNullOrBlank()) {
                                StatusPill(
                                    text = "On Drive ✓",
                                    containerColor = Color(0xCC1F2421),
                                    contentColor = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onCapturePhoto,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Camera", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = onPickPhoto,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Gallery", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = onTogglePublish,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (s.published) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.primary,
                                    contentColor = if (s.published) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("detail_publish_button")
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (s.published) "Published ✓" else "Publish",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Bench QR Tag Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQrDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (qrPreview != null) {
                            Image(
                                bitmap = qrPreview.asImageBitmap(),
                                contentDescription = "Bench QR tag for ${s.code}",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bench QR Tag Ready", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "TAG: ${s.code} • Tap to enlarge",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onCopyTag(s.code) }) {
                            Text("Copy ID")
                        }
                    }
                }
            }

            // Taxonomy, Age & Pot
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoBox(
                        label = "SPECIES",
                        primary = s.species,
                        secondary = s.subspecies.ifBlank { "—" },
                        italicPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                    InfoBox(
                        label = "AGE",
                        primary = "${s.age} years",
                        secondary = s.ageNote.ifBlank { s.ageType.replaceFirstChar { it.uppercase() } },
                        badgeText = if (s.ageType == "accurate") "Exact" else "Approx.",
                        badgeIsAccent = s.ageType != "accurate",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "POT & CONTAINER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (s.repotted.isNotBlank()) {
                                Text(
                                    "Repotted: ${s.repotted}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(s.pot.ifBlank { "Unspecified pot" }, style = MaterialTheme.typography.titleSmall)
                        if (s.potMeta.isNotBlank()) {
                            Text(
                                s.potMeta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Valuation & Pricing
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "VALUATION & PRICING (INR)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { editingPrices = !editingPrices }) {
                                Text(if (editingPrices) "Close" else "Update Costs")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PriceMetricTile(
                                label = "Purchase",
                                value = ValuationCalculator.formatInr(s.purchasePrice),
                                highlighted = false,
                                modifier = Modifier.weight(1f)
                            )
                            PriceMetricTile(
                                label = "Est. Current",
                                value = ValuationCalculator.formatInr(s.estimatedCurrentPrice),
                                highlighted = true,
                                modifier = Modifier.weight(1f)
                            )
                            PriceMetricTile(
                                label = "Selling",
                                value = ValuationCalculator.formatInr(s.sellingPrice),
                                highlighted = false,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Est. = Purchase (${ValuationCalculator.formatInr(s.purchasePrice)}) + New Pot (${ValuationCalculator.formatInr(s.newPotPrice)}) + Materials & Effort (${ValuationCalculator.formatInr(s.effortCost)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (editingPrices) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = purchaseText,
                                    onValueChange = { purchaseText = it.filter(Char::isDigit) },
                                    label = { Text("Purchase ₹") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = potText,
                                    onValueChange = { potText = it.filter(Char::isDigit) },
                                    label = { Text("New Pot ₹") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = effortText,
                                    onValueChange = { effortText = it.filter(Char::isDigit) },
                                    label = { Text("Effort ₹") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = sellingText,
                                    onValueChange = { sellingText = it.filter(Char::isDigit) },
                                    label = { Text("Selling ₹") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = {
                                        onSavePricing(
                                            purchaseText.toDoubleOrNull() ?: 0.0,
                                            potText.toDoubleOrNull() ?: 0.0,
                                            effortText.toDoubleOrNull() ?: 0.0,
                                            sellingText.toDoubleOrNull() ?: 0.0
                                        )
                                        editingPrices = false
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("save_pricing_button")
                                ) {
                                    Text("Save Pricing")
                                }
                            }
                        }
                    }
                }
            }

            // Botanical Library & Care Tip
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, SageBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Botanical Care Tip",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                StatusPill(
                                    text = tip.source,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onCycleTip, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Next care tip",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = tip.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TextButton(
                            onClick = { onOpenUrl(tip.url) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Read full care guide →")
                        }
                    }
                }
            }

            // Scheduled Actions + Global Care Action Picker
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Scheduled Actions & Alerts", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = {
                                preselectedCareAction = null
                                showScheduleDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("detail_schedule_action_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Schedule Action", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (quickPickActions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickPickActions.forEach { actionTemplate ->
                                OutlinedButton(
                                    onClick = {
                                        preselectedCareAction = actionTemplate
                                        showScheduleDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "+ ${actionTemplate.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (item.sortedActions.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No scheduled actions yet. Tap \"Schedule Action\" above to pick from ${careActions.size} global bonsai actions and set the exact days for ${s.name}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        item.sortedActions.forEach { action ->
                            val days = DueDateHelper.daysUntilDue(action.dueDateEpochDay)
                            val isDue = days <= 0
                            Surface(
                                color = if (isDue) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, if (isDue) TerracottaBorder else MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                action.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            StatusPill(
                                                text = DueDateHelper.formatDueLabel(days),
                                                containerColor = if (isDue) MaterialTheme.colorScheme.secondary
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isDue) MaterialTheme.colorScheme.onSecondary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "Due ${DueDateHelper.formatEpochDay(action.dueDateEpochDay)} • ${action.ruleDescription}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { onDeleteScheduledAction(action.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove reminder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Button(
                                        onClick = { onActionTask(action) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Log", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Historical Lifespan Timeline
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Lifespan Video Timeline", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Before & After clips upload automatically to YouTube",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${item.sortedTimeline.size} log${if (item.sortedTimeline.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.sortedTimeline.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No historical care logs yet. Action any scheduled alert to record notes and Before/After YouTube videos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(item.sortedTimeline, key = { it.id }) { entry ->
                    TimelineEntryCard(
                        entry = entry,
                        onOpenYouTubeUrl = onOpenUrl,
                        onPlayLocalVideo = onPlayLocalVideo,
                        onRetryUpload = onRetryYouTubeUploads
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${s.name}?") },
            text = {
                Text("This permanently removes ${s.code}, its scheduled care alerts, and its timeline from this device (and updates your Drive backup).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSpecimen()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showQrDialog) {
        QrCodeDialog(
            specimenCode = s.code,
            specimenName = s.name,
            qrPayload = qrPayload,
            onCopyCode = { onCopyTag(s.code) },
            onDismiss = { showQrDialog = false }
        )
    }

    if (showScheduleDialog) {
        ScheduleActionDialog(
            specimenName = s.name,
            specimenCode = s.code,
            careActions = careActions,
            preselectedAction = preselectedCareAction,
            onDismiss = {
                showScheduleDialog = false
                preselectedCareAction = null
            },
            onConfirm = { title, days, note ->
                onScheduleAction(title, days, note)
                showScheduleDialog = false
                preselectedCareAction = null
            }
        )
    }
}

@Composable
private fun InfoBox(
    label: String,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
    italicPrimary: Boolean = false,
    badgeText: String? = null,
    badgeIsAccent: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontStyle = if (italicPrimary) FontStyle.Italic else FontStyle.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badgeText != null) {
                    StatusPill(
                        text = badgeText,
                        containerColor = if (badgeIsAccent) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (badgeIsAccent) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PriceMetricTile(
    label: String,
    value: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (highlighted) SageBorder else MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TimelineEntryCard(
    entry: TimelineEntryEntity,
    onOpenYouTubeUrl: (String) -> Unit,
    onPlayLocalVideo: (String) -> Unit,
    onRetryUpload: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.taskTitle,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    entry.dateIso,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                entry.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val hasBefore = entry.beforeUploadStatus != UploadStatus.NONE || !entry.beforeYoutubeUrl.isNullOrBlank()
            val hasAfter = entry.afterUploadStatus != UploadStatus.NONE || !entry.afterYoutubeUrl.isNullOrBlank()

            if (hasBefore || hasAfter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasBefore) {
                        VideoSlotChip(
                            label = "Before",
                            status = entry.beforeUploadStatus,
                            youtubeUrl = entry.beforeYoutubeUrl,
                            localPath = entry.beforeLocalVideoPath,
                            onOpenYouTubeUrl = onOpenYouTubeUrl,
                            onPlayLocalVideo = onPlayLocalVideo,
                            onRetryUpload = onRetryUpload,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (hasAfter) {
                        VideoSlotChip(
                            label = "After",
                            status = entry.afterUploadStatus,
                            youtubeUrl = entry.afterYoutubeUrl,
                            localPath = entry.afterLocalVideoPath,
                            onOpenYouTubeUrl = onOpenYouTubeUrl,
                            onPlayLocalVideo = onPlayLocalVideo,
                            onRetryUpload = onRetryUpload,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (!entry.uploadError.isNullOrBlank()) {
                Text(
                    text = "YouTube notice: ${entry.uploadError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun VideoSlotChip(
    label: String,
    status: String,
    youtubeUrl: String?,
    localPath: String?,
    onOpenYouTubeUrl: (String) -> Unit,
    onPlayLocalVideo: (String) -> Unit,
    onRetryUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        !youtubeUrl.isNullOrBlank() -> MaterialTheme.colorScheme.primaryContainer
        status == UploadStatus.FAILED -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        !youtubeUrl.isNullOrBlank() -> MaterialTheme.colorScheme.primary
        status == UploadStatus.FAILED -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable {
            when {
                !youtubeUrl.isNullOrBlank() -> onOpenYouTubeUrl(youtubeUrl)
                status == UploadStatus.FAILED -> onRetryUpload()
                !localPath.isNullOrBlank() -> onPlayLocalVideo(localPath)
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = when {
                        !youtubeUrl.isNullOrBlank() -> Icons.Default.PlayCircle
                        status == UploadStatus.UPLOADED -> Icons.Default.CloudDone
                        else -> Icons.Default.CloudUpload
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$label Video",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
            }
            Text(
                text = when {
                    !youtubeUrl.isNullOrBlank() -> "Watch on YouTube →"
                    status == UploadStatus.UPLOADING -> "Uploading to YouTube…"
                    status == UploadStatus.PENDING -> "Queued (tap to preview)"
                    status == UploadStatus.FAILED -> "Upload failed • Tap retry"
                    else -> "Saved locally"
                },
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}
