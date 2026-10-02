package `in`.potr.bonsai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import `in`.potr.bonsai.data.Pot
import `in`.potr.bonsai.data.PotDetail
import `in`.potr.bonsai.ui.LocalSession
import `in`.potr.bonsai.ui.Routes
import `in`.potr.bonsai.ui.components.Badge
import `in`.potr.bonsai.ui.components.Dropdown
import `in`.potr.bonsai.ui.components.Fact
import `in`.potr.bonsai.ui.components.Load
import `in`.potr.bonsai.ui.components.LoadContent
import `in`.potr.bonsai.ui.components.Loading
import `in`.potr.bonsai.ui.components.PhotoButtons
import `in`.potr.bonsai.ui.components.Section
import `in`.potr.bonsai.ui.components.TextInput
import `in`.potr.bonsai.ui.components.rememberLoader
import `in`.potr.bonsai.ui.components.rememberPhotoLauncher
import `in`.potr.bonsai.util.POT_STATUS_LABELS
import `in`.potr.bonsai.util.compressForUpload
import `in`.potr.bonsai.util.formatDate
import `in`.potr.bonsai.util.formatInr
import `in`.potr.bonsai.util.formatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PotDetailScreen(nav: NavHostController, ref: String) {
    val api = LocalSession.current.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(ref) { api.pot(ref) }
    var busy by remember { mutableStateOf<String?>(null) }
    @Suppress("UNCHECKED_CAST")
    val detail = (loader.state as? Load.Ok<PotDetail>)?.value
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val photos = rememberPhotoLauncher { uri ->
        val p = detail?.pot ?: return@rememberPhotoLauncher
        busy = "Uploading photo…"
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { compressForUpload(context, uri) }
                api.uploadPhoto("pot", p.id, bytes)
                loader.reload()
            } catch (e: Exception) {
                toast(e.message ?: "Upload failed")
            } finally {
                busy = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.pot?.code ?: "Pot") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = { nav.navigate(Routes.qr(detail.pot.code)) }) { Icon(Icons.Filled.QrCode2, "QR card") }
                        IconButton(onClick = { nav.navigate(Routes.potEdit(detail.pot.id)) }) { Icon(Icons.Filled.Edit, "Edit") }
                        IconButton(onClick = {
                            scope.launch {
                                busy = "Syncing with potr.in…"
                                runCatching { api.syncPot(detail.pot.id) }
                                    .onSuccess { toast(if (it.status == "error") "Shop sync failed: ${it.error}" else "Shop: ${it.status}") }
                                    .onFailure { toast(it.message ?: "Sync failed") }
                                busy = null
                                loader.reload()
                            }
                        }) { Icon(Icons.Filled.Sync, "Sync to shop") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            busy?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
            LoadContent(loader) { d ->
                val p = d.pot
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Surface(
                            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            p.coverPhotoUrl?.let { AsyncImage(model = it, contentDescription = p.name, contentScale = ContentScale.Crop) }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(p.name, style = MaterialTheme.typography.headlineSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Badge(POT_STATUS_LABELS[p.status] ?: p.status)
                                if (p.forSale) Badge("For sale · ${formatInr(p.priceInr)}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                if (p.wooSyncStatus == "error") Badge("Shop sync failed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                if (p.wooSyncStatus == "synced") Badge("On potr.in")
                            }
                            p.wooSyncError?.takeIf { p.wooSyncStatus == "error" }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = photos.takePhoto) { Icon(Icons.Filled.AddAPhoto, null); Text("  Photo") }
                            TextButton(onClick = photos.pickFromGallery) { Text("From gallery") }
                        }
                    }
                    item {
                        Section("Details") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val w = Modifier.width(140.dp)
                                Fact("Maker", p.maker, w)
                                Fact("Origin", p.origin, w)
                                Fact("Material", p.material, w)
                                Fact("Glaze", p.glaze, w)
                                Fact("Shape", p.shape, w)
                                Fact(
                                    "Size L×W×H",
                                    listOf(p.lengthCm, p.widthCm, p.heightCm).takeIf { l -> l.all { it != null } }
                                        ?.joinToString(" × ") { formatNumber(it) }?.plus(" cm"),
                                    w,
                                )
                                Fact("Made", p.yearMade?.toString(), w)
                                Fact("Condition", p.condition, w)
                                Fact("Cost", p.costInr?.let(::formatInr), w)
                            }
                            p.description?.let { Text(it) }
                            p.internalNotes?.let { Text("Internal: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    d.bonsai?.let { b ->
                        item {
                            Section("Holding") { BonsaiRow(b) { nav.navigate(Routes.bonsai(b.id)) } }
                        }
                    }
                    if (d.photos.size > 1) {
                        item {
                            Section("Photos") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(d.photos, key = { it.id }) { ph ->
                                        Column {
                                            AsyncImage(
                                                model = ph.url,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                                            )
                                            Text(formatDate(ph.createdAt), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val POT_TEXT = listOf("name", "maker", "origin", "material", "glaze", "shape", "condition", "description", "internal_notes")
private val POT_NUMBERS = listOf("length_cm", "width_cm", "height_cm", "cost_inr", "price_inr")

private fun Pot.toForm(form: FormState) {
    form["name"] = name
    form["maker"] = maker.orEmpty()
    form["origin"] = origin.orEmpty()
    form["material"] = material.orEmpty()
    form["glaze"] = glaze.orEmpty()
    form["shape"] = shape.orEmpty()
    form["condition"] = condition.orEmpty()
    form["description"] = description.orEmpty()
    form["internal_notes"] = internalNotes.orEmpty()
    form["length_cm"] = formatNumber(lengthCm)
    form["width_cm"] = formatNumber(widthCm)
    form["height_cm"] = formatNumber(heightCm)
    form["year_made"] = yearMade?.toString().orEmpty()
    form["cost_inr"] = formatNumber(costInr)
    form["price_inr"] = formatNumber(priceInr)
    form["for_sale"] = forSale.toString()
    form["status"] = status
}

private val SHAPES = listOf("Rectangle", "Oval", "Round", "Square", "Hexagon", "Octagon", "Drum", "Crescent", "Cascade", "Semi-cascade", "Free-form / slab")
private val MATERIALS = listOf("Stoneware", "Porcelain", "Earthenware", "Terracotta", "Mica / plastic", "Concrete", "Stone", "Wood")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotFormScreen(nav: NavHostController, potId: String?) {
    val api = LocalSession.current.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val form = rememberSaveable(saver = FormSaver) { mutableStateMapOf("for_sale" to "false") }
    var loaded by rememberSaveable { mutableStateOf(potId == null) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(potId) {
        if (potId != null && !loaded) {
            runCatching { api.pot(potId) }
                .onSuccess { it.pot.toForm(form); loaded = true }
                .onFailure { error = it.message }
        }
    }
    val launcher = rememberPhotoLauncher { photo = it.toString() }

    fun save() {
        busy = true
        error = null
        scope.launch {
            try {
                val body = jsonOf(
                    form.toJson(POT_TEXT, POT_NUMBERS, listOf("year_made")) + buildMap {
                        put("for_sale", JsonPrimitive(form["for_sale"] == "true"))
                        // in_use is managed through the bonsai; only offer the others here.
                        if (potId != null && form["status"] != "in_use") form["status"]?.let { put("status", JsonPrimitive(it)) }
                    },
                )
                val saved = if (potId == null) api.createPot(body) else api.updatePot(potId, body)
                photo?.let { uri ->
                    val bytes = withContext(Dispatchers.IO) { compressForUpload(context, android.net.Uri.parse(uri)) }
                    api.uploadPhoto("pot", saved.id, bytes)
                }
                if (potId == null) {
                    nav.navigate(Routes.pot(saved.id)) { popUpTo(Routes.POT_NEW) { inclusive = true } }
                } else {
                    nav.popBackStack()
                }
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
                title = { Text(if (potId == null) "Add pot" else "Edit pot") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (!loaded) {
            if (error != null) Text(error!!, Modifier.padding(padding).padding(16.dp)) else Loading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (potId == null) {
                photo?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)))
                }
                PhotoButtons(launcher)
            }
            TextInput("Name *", form.text("name"), { form["name"] = it })
            TextInput("Maker / potter", form.text("maker"), { form["maker"] = it })
            TextInput("Origin", form.text("origin"), { form["origin"] = it })
            Dropdown("Material", form.text("material"), MATERIALS.associateWith { it }, { form["material"] = it })
            TextInput("Glaze", form.text("glaze"), { form["glaze"] = it })
            Dropdown("Shape", form.text("shape"), SHAPES.associateWith { it }, { form["shape"] = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Length cm", form.text("length_cm"), { form["length_cm"] = it }, Modifier.weight(1f), number = true)
                TextInput("Width cm", form.text("width_cm"), { form["width_cm"] = it }, Modifier.weight(1f), number = true)
                TextInput("Height cm", form.text("height_cm"), { form["height_cm"] = it }, Modifier.weight(1f), number = true)
            }
            TextInput("Year made", form.text("year_made"), { form["year_made"] = it.filter(Char::isDigit) }, number = true)
            TextInput("Condition", form.text("condition"), { form["condition"] = it })
            TextInput("Public description", form.text("description"), { form["description"] = it }, singleLine = false)
            TextInput("Internal notes (private)", form.text("internal_notes"), { form["internal_notes"] = it }, singleLine = false)
            TextInput("Cost ₹ (private)", form.text("cost_inr"), { form["cost_inr"] = it }, number = true)
            TextInput("Price ₹", form.text("price_inr"), { form["price_inr"] = it }, number = true)
            SwitchRow("List for sale on potr.in", "Pots holding a bonsai are sold with the tree.", form["for_sale"] == "true") {
                form["for_sale"] = it.toString()
            }
            if (potId != null && form["status"] != "in_use") {
                Dropdown(
                    "Status",
                    form.text("status"),
                    POT_STATUS_LABELS.filterKeys { it != "in_use" },
                    { form["status"] = it },
                    allowEmpty = false,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, enabled = !busy && form.text("name").isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Saving…" else if (potId == null) "Add pot" else "Save changes")
            }
        }
    }
}
