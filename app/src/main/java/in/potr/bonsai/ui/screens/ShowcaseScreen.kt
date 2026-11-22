package `in`.potr.bonsai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.potr.bonsai.data.model.SpecimenWithDetails
import `in`.potr.bonsai.data.model.ValuationCalculator
import `in`.potr.bonsai.ui.MainUiState
import `in`.potr.bonsai.ui.components.CodeBadge
import `in`.potr.bonsai.ui.components.SpecimenPhoto
import `in`.potr.bonsai.ui.components.StatusPill
import `in`.potr.bonsai.ui.theme.ForestGreen
import `in`.potr.bonsai.ui.theme.ForestGreenDeep

@Composable
fun ShowcaseScreen(
    state: MainUiState,
    onOpenSpecimen: (String) -> Unit,
    onCopyPublicLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val published = state.publishedSpecimens

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 240.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(ForestGreen, ForestGreenDeep)),
                        shape = MaterialTheme.shapes.large
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PUBLIC GALLERY & POTR.IN",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA9C5B8)
                    )
                    Text(
                        text = "Curated Bonsai Showcase",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Specimens marked Published appear here and push directly to potr.in/bonsai with compressed photography and YouTube care timelines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD6E3DC)
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onCopyPublicLink,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("copy_showcase_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Copy potr.in/bonsai Link")
                    }
                }
            }
        }

        if (published.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text("No published specimens yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Open any bonsai from the Catalog and tap Publish to feature it here and on potr.in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(published, key = { it.specimen.id }) { item ->
                ShowcaseCard(item = item, onClick = { onOpenSpecimen(item.specimen.id) })
            }
        }
    }
}

@Composable
private fun ShowcaseCard(
    item: SpecimenWithDetails,
    onClick: () -> Unit
) {
    val s = item.specimen
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
                if (s.wooProductId != null) {
                    StatusPill(
                        text = "Synced potr.in",
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${if (s.ageType == "approximate") "~" else ""}${s.age} yrs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${s.species} ${s.subspecies}".trim(),
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.timeline.size} timeline log${if (item.timeline.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (s.sellingPrice > 0) {
                        Text(
                            text = ValuationCalculator.formatInr(s.sellingPrice),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
