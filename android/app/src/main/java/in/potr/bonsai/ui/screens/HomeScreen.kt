package `in`.potr.bonsai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import `in`.potr.bonsai.data.Bonsai
import `in`.potr.bonsai.data.CareTask
import `in`.potr.bonsai.data.Pot
import `in`.potr.bonsai.ui.LocalSession
import `in`.potr.bonsai.ui.Routes
import `in`.potr.bonsai.ui.components.Badge
import `in`.potr.bonsai.ui.components.LoadContent
import `in`.potr.bonsai.ui.components.rememberLoader
import `in`.potr.bonsai.ui.openScanned
import `in`.potr.bonsai.util.BONSAI_STATUS_LABELS
import `in`.potr.bonsai.util.POT_STATUS_LABELS
import `in`.potr.bonsai.util.eventLabel
import `in`.potr.bonsai.util.formatDate
import `in`.potr.bonsai.util.formatInr
import `in`.potr.bonsai.util.scanQr
import `in`.potr.bonsai.util.today
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Bonsai("Bonsai", Icons.Filled.Forest),
    Pots("Pots", Icons.Filled.Spa),
    Care("Care", Icons.Filled.Event),
    More("More", Icons.Filled.MoreHoriz),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController, onSignOut: () -> Unit) {
    val session = LocalSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Bonsai) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.Bonsai) "Potr Bonsai" else tab.label) },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val scanned = try {
                                scanQr(context)
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Scanner unavailable", Toast.LENGTH_LONG).show()
                                null
                            } ?: return@launch
                            openScanned(session, nav, scanned) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        }
                    }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                Tab.Bonsai -> ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.BONSAI_NEW) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Register bonsai") },
                )
                Tab.Pots -> ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.POT_NEW) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add pot") },
                )
                else -> {}
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.Bonsai -> BonsaiList(nav)
                Tab.Pots -> PotList(nav)
                Tab.Care -> CareList(nav)
                Tab.More -> MoreTab(onSignOut)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier) {
    Surface(modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), color = MaterialTheme.colorScheme.surfaceVariant) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun BonsaiList(nav: NavHostController) {
    val api = LocalSession.current.api
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val loader = rememberLoader(query, filter) {
        val status = if (filter == "active" || filter == "sold") filter else null
        api.listBonsai(query, status).filter { filter != "for_sale" || it.forSale }
    }
    Column {
        SearchField(query, { query = it }, "Search code, name or species")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val filters = listOf("all" to "All", "active" to "In collection", "for_sale" to "For sale", "sold" to "Sold")
            items(filters) { (value, label) ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
            }
        }
        LoadContent(loader) { list ->
            if (list.isEmpty()) {
                EmptyHint("No bonsai yet. Tap “Register bonsai” to add the first one.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { b -> BonsaiRow(b) { nav.navigate(Routes.bonsai(b.id)) } }
                }
            }
        }
    }
}

@Composable
fun BonsaiRow(b: Bonsai, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Thumb(b.coverPhotoUrl) },
        headlineContent = { Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(b.speciesBotanical ?: b.speciesCommon.takeIf { b.name != null }, b.ageYears?.let { "$it yrs" })
                        .joinToString(" · "),
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                )
                Text(b.code, style = MaterialTheme.typography.labelMedium)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (b.status != "active") Badge(BONSAI_STATUS_LABELS[b.status] ?: b.status)
                if (b.forSale && b.status == "active") {
                    Badge(
                        formatInr(b.priceInr),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
    )
    HorizontalDivider()
}

@Composable
private fun PotList(nav: NavHostController) {
    val api = LocalSession.current.api
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val loader = rememberLoader(query, filter) {
        api.listPots(query, filter.takeIf { it != "all" && it != "for_sale" })
            .filter { filter != "for_sale" || it.forSale }
    }
    Column {
        SearchField(query, { query = it }, "Search code, name, maker")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val filters = listOf("all" to "All", "in_stock" to "In stock", "in_use" to "In use", "for_sale" to "For sale", "sold" to "Sold")
            items(filters) { (value, label) ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
            }
        }
        LoadContent(loader) { list ->
            if (list.isEmpty()) {
                EmptyHint("No pots yet. Each pot is unique; tap “Add pot” to catalogue one.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { p -> PotRow(p) { nav.navigate(Routes.pot(p.id)) } }
                }
            }
        }
    }
}

@Composable
fun PotRow(p: Pot, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Thumb(p.coverPhotoUrl) },
        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(listOfNotNull(p.maker, p.material, p.shape).joinToString(" · "), maxLines = 1)
                Text(p.code, style = MaterialTheme.typography.labelMedium)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Badge(POT_STATUS_LABELS[p.status] ?: p.status)
                if (p.forSale && p.status == "in_stock") {
                    Badge(formatInr(p.priceInr), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
    )
    HorizontalDivider()
}

@Composable
private fun CareList(nav: NavHostController) {
    val api = LocalSession.current.api
    val loader = rememberLoader { api.tasks() }
    LoadContent(loader) { tasks ->
        if (tasks.isEmpty()) {
            EmptyHint("No care scheduled. Open a bonsai and tap “Schedule” to plan pruning, repotting or wiring.")
        } else {
            val now = today()
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(tasks, key = { it.id }) { t -> TaskRow(t, overdue = t.dueOn < now, nav) }
            }
        }
    }
}

@Composable
fun TaskRow(t: CareTask, overdue: Boolean, nav: NavHostController, showBonsai: Boolean = true) {
    ListItem(
        modifier = Modifier.clickable { nav.navigate(Routes.bonsai(t.bonsaiId)) },
        headlineContent = { Text(t.title ?: eventLabel(t.type)) },
        supportingContent = {
            Column {
                if (showBonsai) Text("${t.bonsaiLabel ?: ""} · ${t.bonsaiCode ?: ""}")
                Text(
                    (if (overdue) "Overdue · " else "Due ") + formatDate(t.dueOn) +
                        (t.recurrenceDays?.let { " · every $it days" } ?: ""),
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            TextButton(onClick = { nav.navigate(Routes.logCare(t.bonsaiId, t.id)) }) {
                Icon(Icons.Filled.CheckCircle, null)
                Text(" Done")
            }
        },
    )
    HorizontalDivider()
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoreTab(onSignOut: () -> Unit) {
    val session = LocalSession.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text(session.user.name, style = MaterialTheme.typography.titleLarge)
                Text("${session.user.role} · ${session.serverUrl}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 8.dp)) { TextButton(onClick = onSignOut) { Text("Sign out") } }
            }
        }
        if (session.user.isAdmin) item { TeamSection() }
    }
}
