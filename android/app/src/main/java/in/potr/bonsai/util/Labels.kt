package `in`.potr.bonsai.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Care event types a person can log (mirrors MANUAL_EVENT_TYPES on the server). */
val MANUAL_EVENT_TYPES = listOf(
    "photo", "pruning", "repotting", "wiring", "wire_removal", "defoliation", "styling",
    "fertilizing", "pest_treatment", "measurement", "award", "note", "correction",
)

/** Types that make sense to schedule ahead. */
val TASK_TYPES = listOf(
    "pruning", "repotting", "wiring", "wire_removal", "defoliation", "styling",
    "fertilizing", "pest_treatment", "photo", "measurement", "note",
)

val EVENT_LABELS = mapOf(
    "registered" to "Registered",
    "photo" to "Photo update",
    "pruning" to "Pruning",
    "repotting" to "Repotting",
    "wiring" to "Wiring",
    "wire_removal" to "Wire removed",
    "defoliation" to "Defoliation",
    "styling" to "Styling",
    "fertilizing" to "Fertilising",
    "pest_treatment" to "Pest / disease treatment",
    "measurement" to "Measurement",
    "pot_change" to "Pot change",
    "location_change" to "Moved",
    "award" to "Award / exhibition",
    "note" to "Note",
    "correction" to "Correction",
    "reserved" to "Reserved",
    "sold" to "Sold",
    "status_change" to "Status change",
)

val ORIGIN_LABELS = linkedMapOf(
    "seed" to "Grown from seed",
    "cutting" to "Cutting",
    "air_layer" to "Air layer",
    "graft" to "Graft",
    "nursery_stock" to "Nursery stock",
    "yamadori" to "Collected (yamadori)",
    "import" to "Imported",
    "other" to "Other",
)

val BONSAI_STATUS_LABELS = linkedMapOf(
    "active" to "In collection",
    "reserved" to "Reserved",
    "sold" to "Sold",
    "deceased" to "Deceased",
    "archived" to "Archived",
)

val POT_STATUS_LABELS = linkedMapOf(
    "in_stock" to "In stock",
    "in_use" to "Holding a bonsai",
    "reserved" to "Reserved",
    "sold" to "Sold",
    "retired" to "Retired",
)

val STYLES = listOf(
    "Formal upright (Chokkan)", "Informal upright (Moyogi)", "Slanting (Shakan)", "Cascade (Kengai)",
    "Semi-cascade (Han-kengai)", "Broom (Hokidachi)", "Literati (Bunjingi)", "Windswept (Fukinagashi)",
    "Twin trunk (Sokan)", "Clump (Kabudachi)", "Forest (Yose-ue)", "Root over rock (Sekijoju)",
    "Raft (Ikadabuki)", "Other",
)

fun eventLabel(type: String) = EVENT_LABELS[type] ?: type.replace('_', ' ').replaceFirstChar { it.uppercase() }

private val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
fun formatInr(v: Double?): String = v?.let { inr.format(it) } ?: "—"

private val displayDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
fun formatDate(iso: String?): String =
    iso?.take(10)?.let { runCatching { LocalDate.parse(it).format(displayDate) }.getOrNull() } ?: "—"

fun today(): String = LocalDate.now().toString()

fun formatNumber(v: Double?): String = when {
    v == null -> ""
    v % 1.0 == 0.0 -> v.toLong().toString()
    else -> v.toString()
}
