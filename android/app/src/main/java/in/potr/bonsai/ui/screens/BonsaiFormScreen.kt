package `in`.potr.bonsai.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import `in`.potr.bonsai.data.Bonsai
import `in`.potr.bonsai.data.Pot
import `in`.potr.bonsai.ui.LocalSession
import `in`.potr.bonsai.ui.Routes
import `in`.potr.bonsai.ui.components.DateInput
import `in`.potr.bonsai.ui.components.Dropdown
import `in`.potr.bonsai.ui.components.Loading
import `in`.potr.bonsai.ui.components.PhotoButtons
import `in`.potr.bonsai.ui.components.TextInput
import `in`.potr.bonsai.ui.components.rememberPhotoLauncher
import `in`.potr.bonsai.util.BONSAI_STATUS_LABELS
import `in`.potr.bonsai.util.ORIGIN_LABELS
import `in`.potr.bonsai.util.STYLES
import `in`.potr.bonsai.util.compressForUpload
import `in`.potr.bonsai.util.formatNumber
import `in`.potr.bonsai.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val TEXT = listOf(
    "name", "species_common", "species_botanical", "style", "origin_type", "origin_location", "source_name",
    "acquired_on", "age_basis", "pot_id", "location", "description", "internal_notes",
)
private val NUMBERS = listOf("height_cm", "trunk_cm", "spread_cm", "cost_inr", "price_inr")

private fun Bonsai.toForm(form: FormState) {
    form["name"] = name.orEmpty()
    form["species_common"] = speciesCommon
    form["species_botanical"] = speciesBotanical.orEmpty()
    form["style"] = style.orEmpty()
    form["origin_type"] = originType.orEmpty()
    form["origin_location"] = originLocation.orEmpty()
    form["source_name"] = sourceName.orEmpty()
    form["acquired_on"] = acquiredOn.orEmpty()
    form["age_years"] = ageYears?.toString().orEmpty()
    form["age_basis"] = ageBasis.orEmpty()
    form["height_cm"] = formatNumber(heightCm)
    form["trunk_cm"] = formatNumber(trunkCm)
    form["spread_cm"] = formatNumber(spreadCm)
    form["pot_id"] = potId.orEmpty()
    form["location"] = location.orEmpty()
    form["description"] = description.orEmpty()
    form["internal_notes"] = internalNotes.orEmpty()
    form["cost_inr"] = formatNumber(costInr)
    form["price_inr"] = formatNumber(priceInr)
    form["status"] = status
    form["for_sale"] = forSale.toString()
}

/** Register a new bonsai (bonsaiId == null) or edit an existing one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BonsaiFormScreen(nav: NavHostController, bonsaiId: String?) {
    val api = LocalSession.current.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val form = rememberSaveable(saver = FormSaver) { mutableStateMapOf("age_basis" to "estimated", "for_sale" to "false") }
    var loaded by rememberSaveable { mutableStateOf(bonsaiId == null) }
    var pots by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(bonsaiId) {
        try {
            var current: Pot? = null
            if (bonsaiId != null && !loaded) {
                val d = api.bonsai(bonsaiId)
                d.bonsai.toForm(form)
                current = d.pot
                loaded = true
            }
            val free = api.listPots(status = "in_stock")
            pots = (listOfNotNull(current) + free).associate { it.id to "${it.name} · ${it.code}" }
        } catch (e: Exception) {
            error = e.message
        }
    }
    val launcher = rememberPhotoLauncher { photo = it.toString() }

    fun save() {
        busy = true
        error = null
        scope.launch {
            try {
                val body = jsonOf(
                    form.toJson(TEXT, NUMBERS) + buildMap {
                        val age = form.text("age_years").toIntOrNull()
                        if (age != null) put("age_years", JsonPrimitive(age)) else put("start_year", JsonNull)
                        put("for_sale", JsonPrimitive(form["for_sale"] == "true"))
                        if (bonsaiId != null) form["status"]?.let { put("status", JsonPrimitive(it)) }
                    },
                )
                val saved = if (bonsaiId == null) api.createBonsai(body) else api.updateBonsai(bonsaiId, body)
                photo?.let { uri ->
                    val bytes = withContext(Dispatchers.IO) { compressForUpload(context, Uri.parse(uri)) }
                    val p = api.uploadPhoto("bonsai", saved.id, bytes)
                    api.addEvent(saved.id, buildJsonObject {
                        put("type", "photo")
                        put("performed_on", today())
                        put("notes", if (bonsaiId == null) "Registration photo" else "Photo update")
                        put("photo_ids", buildJsonArray { add(JsonPrimitive(p.id)) })
                    })
                }
                if (bonsaiId == null) {
                    // Straight to the new tree so its QR card can be printed.
                    nav.navigate(Routes.bonsai(saved.id)) { popUpTo(Routes.BONSAI_NEW) { inclusive = true } }
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
                title = { Text(if (bonsaiId == null) "Register bonsai" else "Edit bonsai") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (!loaded) {
            Loading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (bonsaiId == null) {
                Text("Current photo", style = MaterialTheme.typography.titleSmall)
                photo?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                PhotoButtons(launcher)
            }

            Heading("Identity")
            TextInput("Species (common name) *", form.text("species_common"), { form["species_common"] = it })
            TextInput("Botanical name", form.text("species_botanical"), { form["species_botanical"] = it })
            TextInput("Tree name (optional)", form.text("name"), { form["name"] = it })
            Dropdown("Style", form.text("style"), STYLES.associateWith { it }, { form["style"] = it })

            Heading("Origin & age")
            Dropdown("Origin", form.text("origin_type"), ORIGIN_LABELS, { form["origin_type"] = it })
            TextInput("Origin location", form.text("origin_location"), { form["origin_location"] = it })
            TextInput("Source (nursery / collector / artist)", form.text("source_name"), { form["source_name"] = it })
            DateInput("Acquired on", form.text("acquired_on"), { form["acquired_on"] = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Age (years)", form.text("age_years"), { form["age_years"] = it.filter(Char::isDigit) }, Modifier.weight(1f), number = true)
                Dropdown(
                    "Age is",
                    form.text("age_basis"),
                    mapOf("estimated" to "Estimated", "documented" to "Documented"),
                    { form["age_basis"] = it },
                    Modifier.weight(1f),
                    allowEmpty = false,
                )
            }

            Heading("Size & placement")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Height cm", form.text("height_cm"), { form["height_cm"] = it }, Modifier.weight(1f), number = true)
                TextInput("Trunk cm", form.text("trunk_cm"), { form["trunk_cm"] = it }, Modifier.weight(1f), number = true)
                TextInput("Spread cm", form.text("spread_cm"), { form["spread_cm"] = it }, Modifier.weight(1f), number = true)
            }
            Dropdown("Pot", form.text("pot_id"), pots, { form["pot_id"] = it })
            TextInput("Location in nursery", form.text("location"), { form["location"] = it })

            Heading("Description")
            TextInput("Public description", form.text("description"), { form["description"] = it }, singleLine = false)
            TextInput("Internal notes (private)", form.text("internal_notes"), { form["internal_notes"] = it }, singleLine = false)

            Heading("Sale")
            TextInput("Cost ₹ (private)", form.text("cost_inr"), { form["cost_inr"] = it }, number = true)
            TextInput("Price ₹", form.text("price_inr"), { form["price_inr"] = it }, number = true)
            SwitchRow(
                "List for sale on potr.in",
                "Publishes it to the WooCommerce shop with its certificate link.",
                form["for_sale"] == "true",
            ) { form["for_sale"] = it.toString() }
            if (bonsaiId != null) {
                Dropdown("Status", form.text("status"), BONSAI_STATUS_LABELS, { form["status"] = it }, allowEmpty = false)
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = ::save,
                enabled = !busy && form.text("species_common").isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Saving…" else if (bonsaiId == null) "Register" else "Save changes") }
            if (bonsaiId != null) {
                Text(
                    "Changes to identity, origin, age or size are added to the sealed history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
