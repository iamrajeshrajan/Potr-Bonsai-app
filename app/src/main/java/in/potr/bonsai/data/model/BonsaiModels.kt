package `in`.potr.bonsai.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object UploadStatus {
    const val NONE = "NONE"
    const val PENDING = "PENDING"
    const val UPLOADING = "UPLOADING"
    const val UPLOADED = "UPLOADED"
    const val FAILED = "FAILED"
}

object AssetPhotoKeys {
    const val FICUS = "asset:ficus"
    const val JUNIPER = "asset:juniper"
    const val MAPLE = "asset:maple"
    const val BOUGAINVILLEA = "asset:bougainvillea"

    fun defaultForSpecies(species: String): String {
        val lower = species.lowercase(Locale.ROOT)
        return when {
            lower.contains("juniper") -> JUNIPER
            lower.contains("acer") || lower.contains("maple") -> MAPLE
            lower.contains("bougain") -> BOUGAINVILLEA
            else -> FICUS
        }
    }
}

object CareActionCategories {
    const val PRUNING = "Pruning & Canopy"
    const val WIRING = "Wiring & Styling"
    const val ROOTS = "Roots & Repotting"
    const val FEEDING = "Feeding & Soil"
    const val DEADWOOD = "Deadwood & Refinement"
    const val HEALTH = "Health & Seasonal"
    const val CUSTOM = "Custom"

    val all: List<String> = listOf(
        PRUNING,
        WIRING,
        ROOTS,
        FEEDING,
        DEADWOOD,
        HEALTH,
        CUSTOM
    )
}

object DefaultSpeciesSuggestions {
    val common: List<String> = listOf(
        "Ficus microcarpa",
        "Ficus benjamina",
        "Ficus religiosa",
        "Juniperus chinensis",
        "Juniperus procumbens",
        "Acer palmatum",
        "Bougainvillea glabra",
        "Premna microphylla",
        "Ulmus parvifolia",
        "Carmona retusa",
        "Portulacaria afra",
        "Wrightia religiosa",
        "Tamarindus indica",
        "Adenium obesum",
        "Pinus thunbergii",
        "Podocarpus macrophyllus",
        "Buxus harlandii",
        "Schefflera arboricola"
    )
}

@Serializable
@Entity(tableName = "specimens")
data class SpecimenEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val species: String,
    val subspecies: String = "",
    val age: Int = 10,
    val ageType: String = "approximate", // "accurate" | "approximate"
    val ageNote: String = "",
    val localImagePath: String = AssetPhotoKeys.FICUS,
    val drivePhotoFileId: String? = null,
    val drivePhotoUrl: String? = null,
    val pot: String = "",
    val potMeta: String = "",
    val repotted: String = "",
    val published: Boolean = false,
    val wooProductId: Int? = null,
    val wooPermalink: String? = null,
    val purchasePrice: Double = 0.0,
    val newPotPrice: Double = 0.0,
    val effortCost: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val tipIndex: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    val estimatedCurrentPrice: Double
        get() = ValuationCalculator.estimatedCurrentPrice(purchasePrice, newPotPrice, effortCost)
}

/**
 * Global bonsai care/maintenance action available to every specimen in the studio.
 * [defaultDays] serves as a suggested reminder interval that can be customized per plant when scheduling.
 */
@Serializable
@Entity(tableName = "care_actions")
data class CareActionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val defaultDays: Int,
    val description: String = ""
)

@Serializable
@Entity(
    tableName = "scheduled_actions",
    foreignKeys = [
        ForeignKey(
            entity = SpecimenEntity::class,
            parentColumns = ["id"],
            childColumns = ["specimenId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("specimenId")]
)
data class ScheduledActionEntity(
    @PrimaryKey val id: String,
    val specimenId: String,
    val title: String,
    val dueDateEpochDay: Long,
    val ruleDescription: String,
    val intervalDays: Int = 30
)

@Serializable
@Entity(
    tableName = "timeline_entries",
    foreignKeys = [
        ForeignKey(
            entity = SpecimenEntity::class,
            parentColumns = ["id"],
            childColumns = ["specimenId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("specimenId")]
)
data class TimelineEntryEntity(
    @PrimaryKey val id: String,
    val specimenId: String,
    val dateIso: String,
    val taskTitle: String,
    val notes: String,
    val beforeLocalVideoPath: String? = null,
    val afterLocalVideoPath: String? = null,
    val beforeYoutubeVideoId: String? = null,
    val afterYoutubeVideoId: String? = null,
    val beforeYoutubeUrl: String? = null,
    val afterYoutubeUrl: String? = null,
    val beforeUploadStatus: String = UploadStatus.NONE,
    val afterUploadStatus: String = UploadStatus.NONE,
    val uploadError: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SpecimenWithDetails(
    @Embedded val specimen: SpecimenEntity,
    @Relation(parentColumn = "id", entityColumn = "specimenId")
    val actions: List<ScheduledActionEntity>,
    @Relation(parentColumn = "id", entityColumn = "specimenId")
    val timeline: List<TimelineEntryEntity>
) {
    val sortedActions: List<ScheduledActionEntity>
        get() = actions.sortedBy { it.dueDateEpochDay }

    val sortedTimeline: List<TimelineEntryEntity>
        get() = timeline.sortedByDescending { it.createdAtEpochMs }

    fun dueCount(todayEpochDay: Long = LocalDate.now().toEpochDay()): Int =
        actions.count { DueDateHelper.daysUntilDue(it.dueDateEpochDay, todayEpochDay) <= 0 }
}

data class AlertItem(
    val specimen: SpecimenEntity,
    val action: ScheduledActionEntity,
    val daysUntilDue: Long
) {
    val isDueOrOverdue: Boolean get() = daysUntilDue <= 0L
}

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 2,
    val appVersion: String = "2.1.0",
    val exportedAtEpochMs: Long,
    val specimens: List<SpecimenEntity>,
    val careActions: List<CareActionEntity> = emptyList(),
    val scheduledActions: List<ScheduledActionEntity>,
    val timelineEntries: List<TimelineEntryEntity>
)

object DefaultBonsaiActions {
    val all: List<CareActionEntity> = listOf(
        // Pruning & Canopy
        CareActionEntity(
            id = "ca-maint-prune",
            title = "Maintenance Pruning & Pinching",
            category = CareActionCategories.PRUNING,
            defaultDays = 21,
            description = "Trim elongated shoots back to 2–3 leaves to tighten pads and boost fine ramification."
        ),
        CareActionEntity(
            id = "ca-hard-prune",
            title = "Structural / Hard Pruning",
            category = CareActionCategories.PRUNING,
            defaultDays = 45,
            description = "Remove thick, crossing, or inverse-taper branches to define primary branch architecture."
        ),
        CareActionEntity(
            id = "ca-candle-prune",
            title = "Candle / Shoot Pruning (Decandling)",
            category = CareActionCategories.PRUNING,
            defaultDays = 30,
            description = "Pinch or cut spring candles on pines and conifers to balance vigor and encourage back-budding."
        ),
        CareActionEntity(
            id = "ca-defoliation",
            title = "Partial / Full Defoliation",
            category = CareActionCategories.PRUNING,
            defaultDays = 45,
            description = "Leaf-prune vigorous deciduous or tropical trees to reduce leaf size and stimulate inner buds."
        ),
        CareActionEntity(
            id = "ca-post-bloom",
            title = "Post-Flush / Post-Bloom Pruning",
            category = CareActionCategories.PRUNING,
            defaultDays = 30,
            description = "Cut back spent flowering shoots (e.g., Bougainvillea, Azalea) to redirect energy into structure."
        ),
        CareActionEntity(
            id = "ca-bud-selection",
            title = "Bud Selection & Shoot Thinning",
            category = CareActionCategories.PRUNING,
            defaultDays = 28,
            description = "Remove weak or overcrowded buds at bar branches and crotches to prevent swelling."
        ),
        CareActionEntity(
            id = "ca-sacrifice-check",
            title = "Sacrifice Branch Review",
            category = CareActionCategories.PRUNING,
            defaultDays = 60,
            description = "Check trunk-thickening sacrifice leaders to ensure they are not shading lower primary pads."
        ),
        CareActionEntity(
            id = "ca-wound-callus",
            title = "Cut-Paste & Wound Callus Check",
            category = CareActionCategories.PRUNING,
            defaultDays = 45,
            description = "Inspect major pruning scars, trim rolled edges if needed, and refresh wound sealant."
        ),

        // Wiring & Styling
        CareActionEntity(
            id = "ca-branch-wiring",
            title = "Branch Wiring (Aluminum / Copper)",
            category = CareActionCategories.WIRING,
            defaultDays = 45,
            description = "Apply annealed wire at 45° coils to set branch movement, pad spacing, and silhouette."
        ),
        CareActionEntity(
            id = "ca-wire-bite",
            title = "Wire Bite Inspection",
            category = CareActionCategories.WIRING,
            defaultDays = 21,
            description = "Check all wired branches during active growth so wire does not scar or girdle bark."
        ),
        CareActionEntity(
            id = "ca-wire-remove",
            title = "Wire Removal & Rewiring",
            category = CareActionCategories.WIRING,
            defaultDays = 60,
            description = "Cut off set wire carefully with wire cutters; rewire if branch springs back."
        ),
        CareActionEntity(
            id = "ca-guy-wire",
            title = "Guy-Wire Tension Adjustment",
            category = CareActionCategories.WIRING,
            defaultDays = 30,
            description = "Inspect padded guy-wires and gradually tighten bends on heavy limbs."
        ),
        CareActionEntity(
            id = "ca-raffia-bend",
            title = "Heavy Branch Bending & Raffia Check",
            category = CareActionCategories.WIRING,
            defaultDays = 30,
            description = "Inspect raffia/vet-wrap protection on dramatic bends for moisture buildup or cambium stress."
        ),

        // Roots & Repotting
        CareActionEntity(
            id = "ca-root-check",
            title = "Root Health & Compaction Check",
            category = CareActionCategories.ROOTS,
            defaultDays = 90,
            description = "Lift gently or check drainage holes for circling roots, percolation slowdown, or root rot."
        ),
        CareActionEntity(
            id = "ca-repotting",
            title = "Repotting & Root Pruning",
            category = CareActionCategories.ROOTS,
            defaultDays = 365,
            description = "Rake out old compacted substrate, trim coarse downward roots, and anchor into fresh soil mix."
        ),
        CareActionEntity(
            id = "ca-nebari",
            title = "Nebari (Surface Root) Arrangement",
            category = CareActionCategories.ROOTS,
            defaultDays = 180,
            description = "Comb and position radial surface roots for balanced flare and visual stability."
        ),
        CareActionEntity(
            id = "ca-aerial-roots",
            title = "Aerial Root Training",
            category = CareActionCategories.ROOTS,
            defaultDays = 45,
            description = "Guide Ficus/tropical aerial roots down into substrate with straws or humidity wraps."
        ),
        CareActionEntity(
            id = "ca-drainage-tie",
            title = "Drainage Mesh & Tie-Down Check",
            category = CareActionCategories.ROOTS,
            defaultDays = 90,
            description = "Verify pot drainage flow and confirm anchor wires keep the rootball rock-steady."
        ),

        // Feeding & Soil
        CareActionEntity(
            id = "ca-organic-feed",
            title = "Organic Fertilizer Pellets / Cakes",
            category = CareActionCategories.FEEDING,
            defaultDays = 30,
            description = "Replenish slow-release organic fertilizer baskets (biogold / neem-mustard cakes) across soil surface."
        ),
        CareActionEntity(
            id = "ca-liquid-feed",
            title = "Balanced Liquid Fertilizer Feed",
            category = CareActionCategories.FEEDING,
            defaultDays = 14,
            description = "Apply diluted balanced NPK + micronutrients after watering during active growing months."
        ),
        CareActionEntity(
            id = "ca-autumn-feed",
            title = "Low-Nitrogen Hardening Feed (0-10-10)",
            category = CareActionCategories.FEEDING,
            defaultDays = 30,
            description = "Switch to phosphorus/potassium feed in late season to harden wood and build root reserves."
        ),
        CareActionEntity(
            id = "ca-topsoil-moss",
            title = "Topsoil, Akadama & Moss Refresh",
            category = CareActionCategories.FEEDING,
            defaultDays = 90,
            description = "Weed surface, scrub moss buildup off trunk base, and top-dress washed-away fine akadama."
        ),
        CareActionEntity(
            id = "ca-soil-flush",
            title = "Deep Soil Flush & pH Check",
            category = CareActionCategories.FEEDING,
            defaultDays = 60,
            description = "Flush substrate thoroughly to leach accumulated mineral salts and verify healthy pH."
        ),

        // Deadwood & Refinement
        CareActionEntity(
            id = "ca-jin-shari",
            title = "Jin & Shari Deadwood Carving",
            category = CareActionCategories.DEADWOOD,
            defaultDays = 90,
            description = "Refine deadwood grain lines, remove soft decay, and expand shari veins safely."
        ),
        CareActionEntity(
            id = "ca-lime-sulfur",
            title = "Lime Sulfur Deadwood Treatment",
            category = CareActionCategories.DEADWOOD,
            defaultDays = 180,
            description = "Brush diluted lime sulfur onto clean dry jin/shari to bleach and preserve exposed wood."
        ),
        CareActionEntity(
            id = "ca-bark-clean",
            title = "Trunk & Bark Cleaning",
            category = CareActionCategories.DEADWOOD,
            defaultDays = 90,
            description = "Gently brush algae, lichen, and hard-water crust from trunk and nebari using soft nylon bristles."
        ),

        // Health & Seasonal
        CareActionEntity(
            id = "ca-pest-inspect",
            title = "Pest & Mite Inspection (Neem / Soap)",
            category = CareActionCategories.HEALTH,
            defaultDays = 14,
            description = "Inspect undersides of foliage for spider mites, scale, aphids, or mealybugs; treat preventively."
        ),
        CareActionEntity(
            id = "ca-fungicide",
            title = "Preventive Fungicide / Monsoon Spray",
            category = CareActionCategories.HEALTH,
            defaultDays = 30,
            description = "Apply copper or broad-spectrum fungicide ahead of high humidity or monsoon spells."
        ),
        CareActionEntity(
            id = "ca-bench-rotate",
            title = "Sun Exposure & Bench Rotation",
            category = CareActionCategories.HEALTH,
            defaultDays = 14,
            description = "Rotate specimen 180° on the bench so back branches receive even sunlight and growth."
        ),
        CareActionEntity(
            id = "ca-air-layer",
            title = "Air-Layering Moisture & Root Check",
            category = CareActionCategories.HEALTH,
            defaultDays = 45,
            description = "Verify sphagnum moss dampness and inspect root development before separation."
        ),
        CareActionEntity(
            id = "ca-seasonal-prep",
            title = "Seasonal Shade / Dormancy Prep",
            category = CareActionCategories.HEALTH,
            defaultDays = 90,
            description = "Adjust shade cloth for peak heat or move tender species to sheltered winter/monsoon benches."
        )
    )
}

object ValuationCalculator {
    fun estimatedCurrentPrice(purchasePrice: Double, newPotPrice: Double, effortCost: Double): Double =
        (purchasePrice.coerceAtLeast(0.0) + newPotPrice.coerceAtLeast(0.0) + effortCost.coerceAtLeast(0.0))

    fun formatInr(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
        return "₹${formatter.format(amount.toLong())}"
    }
}

object DueDateHelper {
    fun daysUntilDue(dueDateEpochDay: Long, todayEpochDay: Long = LocalDate.now().toEpochDay()): Long =
        dueDateEpochDay - todayEpochDay

    fun formatDueLabel(daysUntilDue: Long): String = when {
        daysUntilDue < 0 -> "Overdue ${-daysUntilDue}d"
        daysUntilDue == 0L -> "Due Today"
        else -> "In ${daysUntilDue}d"
    }

    fun formatEpochDay(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))

    fun currentMonthYear(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))

    fun todayIso(): String = LocalDate.now().toString()
}

object CodeGenerator {
    private val codePattern = Regex("""BNS-(\d+)""", RegexOption.IGNORE_CASE)

    fun normalizeSpeciesKey(species: String): String =
        species.trim().lowercase(Locale.ROOT)

    fun nextSpecimenCode(existingCodes: Collection<String>): String {
        val maxNum = existingCodes.mapNotNull { code ->
            codePattern.find(code)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        val next = maxNum + 1
        return "BNS-${next.toString().padStart(4, '0')}"
    }
}

data class BotanicalTip(
    val source: String,
    val url: String,
    val text: String
)

object BotanicalTipsLibrary {
    private val tipsBySpecies: Map<String, List<BotanicalTip>> = mapOf(
        "ficus microcarpa" to listOf(
            BotanicalTip(
                source = "Bonsai Empire",
                url = "https://www.bonsaiempire.com/tree-species/ficus",
                text = "Prune back to 2 leaves after 6–8 leaves have grown to maintain compact canopy pads. Water generously whenever soil feels slightly dry rather than following a rigid daily schedule."
            ),
            BotanicalTip(
                source = "St. James Park",
                url = "https://st-jamespark.com/ficus-microcarpa-the-ultimate-bonsai-tree-for-beginners-and-experts-alike",
                text = "Ficus microcarpa prefers bright, indirect light; harsh midday sun can scorch leaves, while waterlogged soil triggers root rot."
            )
        ),
        "juniperus chinensis" to listOf(
            BotanicalTip(
                source = "Yorkshire Bonsai",
                url = "https://www.yorkshirebonsai.co.uk/blogs/advice-guides/dwarf-juniper-bonsai-care-guide-uk",
                text = "Always retain live foliage and growing tips throughout the canopy—never cut back to bare old wood expecting dependable regrowth. Inspect wired branches often so wire does not bite into bark."
            ),
            BotanicalTip(
                source = "Bonsai Empire",
                url = "https://www.bonsaiempire.com/tree-species/juniper",
                text = "Allowing selected sacrifice branches to grow long and strong lets the tree store energy and fatten its trunk much faster before final styling."
            )
        ),
        "acer palmatum" to listOf(
            BotanicalTip(
                source = "Bonsai Empire",
                url = "https://www.bonsaiempire.com/tree-species/red-maple",
                text = "Pinch back new shoots after 3–4 nodes in spring and summer to encourage dense, fine ramification. Repot older maples every 3–4 years in early spring before bud break."
            )
        ),
        "bougainvillea glabra" to listOf(
            BotanicalTip(
                source = "Kigi Bonsai",
                url = "https://kigibonsai.com/guides/bougainvillea-bonsai-care-australia/",
                text = "Separate development from flowering: constant pruning builds foliage but prevents bracts. Wear gloves and eye protection when pruning thorny mature branches."
            ),
            BotanicalTip(
                source = "Bonsai Empire",
                url = "https://www.bonsaiempire.com/tree-species/bougainvillea",
                text = "Cut shoots after flowering leaving two leaves on each; Bougainvillea buds vigorously from old wood after hard pruning and prefers slightly acidic soil (pH 6.0–6.5)."
            ),
            BotanicalTip(
                source = "Yorkshire Bonsai",
                url = "https://www.yorkshirebonsai.co.uk/blogs/advice-guides/bougainvillea-bonsai-care-guide",
                text = "Older Bougainvillea wood is surprisingly brittle—shape young shoots gently and make a small number of deliberate cuts after each flowering flush."
            )
        )
    )

    private val generalTips = listOf(
        BotanicalTip(
            source = "POTR Studio Notes",
            url = "https://potr.in/product-category/bonsai/",
            text = "Check substrate moisture with a chopstick or fingertip 2 cm deep before watering; well-draining akadama/pumice mixes prevent root compaction and encourage fine feeder roots."
        ),
        BotanicalTip(
            source = "POTR Studio Notes",
            url = "https://potr.in/product-category/bonsai/",
            text = "Avoid combining heavy structural pruning and aggressive root work in the same month—give the specimen a full recovery flush between major interventions."
        )
    )

    fun tipFor(species: String, index: Int): BotanicalTip {
        val list = tipsBySpecies[CodeGenerator.normalizeSpeciesKey(species)] ?: generalTips
        val safeIndex = ((index % list.size) + list.size) % list.size
        return list[safeIndex]
    }
}
