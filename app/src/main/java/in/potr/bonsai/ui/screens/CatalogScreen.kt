package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.potr.bonsai.data.model.DueDateHelper
import `in`.potr.bonsai.data.model.SpecimenWithDetails
import `in`.potr.bonsai.data.model.ValuationCalculator
import `in`.potr.bonsai.ui.AlertFilter
import `in`.potr.bonsai.ui.CatalogFilter
import `in`.potr.bonsai.ui.CatalogViewMode
import `in`.potr.bonsai.ui.MainUiState
import `in`.potr.bonsai.ui.components.CodeBadge
import `in`.potr.bonsai.ui.components.SpecimenPhoto
import `in`.potr.bonsai.ui.components.StatusPill
import `in`.potr.bonsai.ui.theme.JetBrainsMonoFamily
import java.time.LocalDate

@Composable
fun CatalogScreen(
    state: MainUiState,
    onSearchChange: (String) -> Unit,
    onSelectFilter: (CatalogFilter) -> Unit,
    onSetViewMode: (CatalogViewMode) -> Unit,
    onOpenSpecimen: (String) -> Unit,
    onKpiTotalClick: () -> Unit,
    onKpiDueClick: () -> Unit,
    onKpiUpcomingClick: () -> Unit,
    onKpiPublishedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now().toEpochDay()

    if (state.viewMode == CatalogViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 172.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CatalogControlsHeader(
                    state = state,
                    onSearchChange = onSearchChange,
                    onSelectFilter = onSelectFilter,
                    onSetViewMode = onSetViewMode,
                    onKpiTotalClick = onKpiTotalClick,
                    onKpiDueClick = onKpiDueClick,
                    onKpiUpcomingClick = onKpiUpcomingClick,
                    onKpiPublishedClick = onKpiPublishedClick
                )
            }

            if (state.filteredSpecimens.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyCatalogCard()
                }
            } else {
                items(state.filteredSpecimens, key = { it.specimen.id }) { item ->
                    SpecimenGridCard(
                        item = item,
                        todayEpochDay = today,
                        onClick = { onOpenSpecimen(item.specimen.id) }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier.fillMaxSize()
        ) {
            item {
                CatalogControlsHeader(
                    state = state,
                    onSearchChange = onSearchChange,
                    onSelectFilter = onSelectFilter,
                    onSetViewMode = onSetViewMode,
                    onKpiTotalClick = onKpiTotalClick,
                    onKpiDueClick = onKpiDueClick,
                    onKpiUpcomingClick = onKpiUpcomingClick,
                    onKpiPublishedClick = onKpiPublishedClick
                )
            }

            if (state.filteredSpecimens.isEmpty()) {
                item { EmptyCatalogCard() }
            } else {
                items(state.filteredSpecimens, key = { it.specimen.id }) { item ->
                    SpecimenTableRowCard(
                        item = item,
                        todayEpochDay = today,
                        onClick = { onOpenSpecimen(item.specimen.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogControlsHeader(
    state: MainUiState,
    onSearchChange: (String) -> Unit,
    onSelectFilter: (CatalogFilter) -> Unit,
    onSetViewMode: (CatalogViewMode) -> Unit,
    onKpiTotalClick: () -> Unit,
    onKpiDueClick: () -> Unit,
    onKpiUpcomingClick: () -> Unit,
    onKpiPublishedClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 2x2 KPI Strip
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = "Collection",
                    value = state.totalCount.toString(),
                    unit = "specimens",
                    pillText = "Catalog →",
                    pillContainer = MaterialTheme.colorScheme.primaryContainer,
                    pillContent = MaterialTheme.colorScheme.primary,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onKpiTotalClick,
                    modifier = Modifier.weight(1f).testTag("kpi_total_card")
                )
                KpiCard(
                    title = "Due Alerts",
                    value = state.dueCount.toString(),
                    unit = "need action",
                    pillText = "View Due →",
                    pillContainer = MaterialTheme.colorScheme.secondaryContainer,
                    pillContent = MaterialTheme.colorScheme.secondary,
                    valueColor = MaterialTheme.colorScheme.secondary,
                    onClick = onKpiDueClick,
                    modifier = Modifier.weight(1f).testTag("kpi_due_card")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = "Scheduled",
                    value = state.upcomingCount.toString(),
                    unit = "upcoming",
                    pillText = "Queue →",
                    pillContainer = MaterialTheme.colorScheme.surfaceVariant,
                    pillContent = MaterialTheme.colorScheme.onSurfaceVariant,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onKpiUpcomingClick,
                    modifier = Modifier.weight(1f).testTag("kpi_upcoming_card")
                )
                KpiCard(
                    title = "Showcase",
                    value = state.publishedCount.toString(),
                    unit = "live trees",
                    pillText = "Gallery →",
                    pillContainer = MaterialTheme.colorScheme.primaryContainer,
                    pillContent = MaterialTheme.colorScheme.primary,
                    valueColor = MaterialTheme.colorScheme.primary,
                    onClick = onKpiPublishedClick,
                    modifier = Modifier.weight(1f).testTag("kpi_published_card")
                )
            }
        }

        // Search & Filter Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            "Search name, BNS code, species, or pot…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search catalog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catalog_search_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filters = listOf(
                            CatalogFilter.ALL to "All",
                            CatalogFilter.ALERTS_DUE to "Alerts Due",
                            CatalogFilter.APPROX_AGE to "Approx. Age",
                            CatalogFilter.PUBLISHED to "Published"
                        )
                        filters.forEach { (filter, label) ->
                            val selected = state.catalogFilter == filter
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectFilter(filter) },
                                label = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null,
                                modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Grid / Table toggle
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            IconButton(
                                onClick = { onSetViewMode(CatalogViewMode.GRID) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (state.viewMode == CatalogViewMode.GRID) MaterialTheme.colorScheme.surface
                                        else Color.Transparent
                                    )
                                    .testTag("view_mode_grid")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Grid view",
                                    tint = if (state.viewMode == CatalogViewMode.GRID) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onSetViewMode(CatalogViewMode.TABLE) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (state.viewMode == CatalogViewMode.TABLE) MaterialTheme.colorScheme.surface
                                        else Color.Transparent
                                    )
                                    .testTag("view_mode_table")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableRows,
                                    contentDescription = "Table view",
                                    tint = if (state.viewMode == CatalogViewMode.TABLE) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Count caption
        val shown = state.filteredSpecimens.size
        val total = state.totalCount
        Text(
            text = if (shown == total) {
                "Showing $total specimen${if (total == 1) "" else "s"} in local archive"
            } else {
                "Showing $shown of $total specimens"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    unit: String,
    pillText: String,
    pillContainer: Color,
    pillContent: Color,
    valueColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusPill(
                    text = pillText,
                    containerColor = pillContainer,
                    contentColor = pillContent
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = valueColor
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun SpecimenGridCard(
    item: SpecimenWithDetails,
    todayEpochDay: Long,
    onClick: () -> Unit
) {
    val s = item.specimen
    val dueCount = item.dueCount(todayEpochDay)
    val nextAction = item.sortedActions.firstOrNull()

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("specimen_card_${s.code}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                SpecimenPhoto(
                    imagePath = s.localImagePath,
                    contentDescription = s.name,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x55000000), Color.Transparent)
                            )
                        )
                )
                CodeBadge(
                    code = s.code,
                    dark = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
                if (s.published) {
                    StatusPill(
                        text = "Live",
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
                if (!s.drivePhotoFileId.isNullOrBlank()) {
                    Surface(
                        color = Color(0x991F2421),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Backed up to Drive",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(13.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = s.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${if (s.ageType == "approximate") "~" else ""}${s.age}y",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (s.ageType == "accurate") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary
                    )
                }

                Text(
                    text = s.species,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Est ${ValuationCalculator.formatInr(s.estimatedCurrentPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = ValuationCalculator.formatInr(s.sellingPrice),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        dueCount > 0 -> StatusPill(
                            text = "$dueCount Alert${if (dueCount > 1) "s" else ""} Due",
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                        nextAction != null -> {
                            val days = DueDateHelper.daysUntilDue(nextAction.dueDateEpochDay, todayEpochDay)
                            Text(
                                text = "${nextAction.title} (${DueDateHelper.formatDueLabel(days)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> Text(
                            text = "No tasks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecimenTableRowCard(
    item: SpecimenWithDetails,
    todayEpochDay: Long,
    onClick: () -> Unit
) {
    val s = item.specimen
    val nextAction = item.sortedActions.firstOrNull()
    val nextDays = nextAction?.let { DueDateHelper.daysUntilDue(it.dueDateEpochDay, todayEpochDay) }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("specimen_row_${s.code}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpecimenPhoto(
                imagePath = s.localImagePath,
                contentDescription = s.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CodeBadge(code = s.code)
                    Text(
                        text = s.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${s.species} • ${if (s.ageType == "approximate") "~" else ""}${s.age} yrs",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Est ${ValuationCalculator.formatInr(s.estimatedCurrentPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sell ${ValuationCalculator.formatInr(s.sellingPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(
                    text = if (s.published) "Live" else "Private",
                    containerColor = if (s.published) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (s.published) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (nextAction != null && nextDays != null) {
                    Text(
                        text = DueDateHelper.formatDueLabel(nextDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (nextDays <= 0) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCatalogCard() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Park,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No matching specimens",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Try clearing your search or tap Catalog Bonsai to add a new tree.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
