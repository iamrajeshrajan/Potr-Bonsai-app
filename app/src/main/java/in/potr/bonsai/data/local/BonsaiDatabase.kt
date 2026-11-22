package `in`.potr.bonsai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import `in`.potr.bonsai.data.model.AssetPhotoKeys
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.DefaultBonsaiActions
import `in`.potr.bonsai.data.model.ScheduledActionEntity
import `in`.potr.bonsai.data.model.SpecimenEntity
import `in`.potr.bonsai.data.model.TimelineEntryEntity
import java.time.LocalDate

@Database(
    entities = [
        SpecimenEntity::class,
        CareActionEntity::class,
        ScheduledActionEntity::class,
        TimelineEntryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BonsaiDatabase : RoomDatabase() {
    abstract fun bonsaiDao(): BonsaiDao

    companion object {
        @Volatile
        private var INSTANCE: BonsaiDatabase? = null

        /**
         * Non-destructive migration from v1 (species-wise schedules) to v2 (global care_actions),
         * preserving all cataloged specimens, active reminders, and lifespan timelines.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `care_actions` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `defaultDays` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS `care_rules`")
                db.execSQL("DROP TABLE IF EXISTS `species_schedules`")
            }
        }

        fun getInstance(context: Context): BonsaiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BonsaiDatabase::class.java,
                    "potr_bonsai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object InitialSeedData {

    suspend fun seedIfEmpty(dao: BonsaiDao) {
        // Always ensure global bonsai care actions exist (including after v1 -> v2 upgrade)
        if (dao.countCareActions() == 0) {
            dao.insertAllCareActions(DefaultBonsaiActions.all)
        }

        // Only seed demo specimens on a fresh install
        if (dao.countSpecimens() > 0) return

        val today = LocalDate.now().toEpochDay()
        val now = System.currentTimeMillis()

        val specimens = listOf(
            SpecimenEntity(
                id = "bns-1",
                code = "BNS-0042",
                name = "Banyan Sentinel",
                species = "Ficus microcarpa",
                subspecies = "var. retusa",
                age = 34,
                ageType = "approximate",
                ageNote = "Estimated from aerial root fusion & trunk girth",
                localImagePath = AssetPhotoKeys.FICUS,
                pot = "Dark Glazed Oval Ceramic",
                potMeta = "34 × 25 × 7 cm • High-fired stoneware",
                repotted = "Mar 2025",
                published = true,
                purchasePrice = 12000.0,
                newPotPrice = 3500.0,
                effortCost = 8500.0,
                sellingPrice = 32000.0,
                createdAtEpochMs = now - 8000L
            ),
            SpecimenEntity(
                id = "bns-2",
                code = "BNS-0187",
                name = "Shimpaku Wind",
                species = "Juniperus chinensis",
                subspecies = "cv. 'Shimpaku'",
                age = 42,
                ageType = "approximate",
                ageNote = "Yamadori stock acquired 2012 (~28 yrs at collection)",
                localImagePath = AssetPhotoKeys.JUNIPER,
                pot = "Unglazed Tokoname Rectangular",
                potMeta = "30 × 22 × 8 cm • Hand-pressed clay",
                repotted = "Feb 2024",
                published = true,
                purchasePrice = 18000.0,
                newPotPrice = 4800.0,
                effortCost = 11200.0,
                sellingPrice = 45000.0,
                createdAtEpochMs = now - 7000L
            ),
            SpecimenEntity(
                id = "bns-3",
                code = "BNS-0419",
                name = "Crimson Mist",
                species = "Acer palmatum",
                subspecies = "var. palmatum",
                age = 19,
                ageType = "accurate",
                ageNote = "Propagated from air-layer in Spring 2007",
                localImagePath = AssetPhotoKeys.MAPLE,
                pot = "Matte Chestnut Oval",
                potMeta = "28 × 21 × 5.5 cm • Shallow drum rim",
                repotted = "Feb 2026",
                published = true,
                purchasePrice = 9500.0,
                newPotPrice = 2800.0,
                effortCost = 6200.0,
                sellingPrice = 24000.0,
                createdAtEpochMs = now - 6000L
            ),
            SpecimenEntity(
                id = "bns-4",
                code = "BNS-0703",
                name = "Shoolagiri Flame",
                species = "Bougainvillea glabra",
                subspecies = "cv. 'Sanderiana'",
                age = 26,
                ageType = "approximate",
                ageNote = "Field-grown trunk dug 2016; approx. 16 yrs old then",
                localImagePath = AssetPhotoKeys.BOUGAINVILLEA,
                pot = "Handcrafted Wood-Fired Terracotta",
                potMeta = "31 × 24 × 9 cm • Breathable raw clay",
                repotted = "Apr 2025",
                published = false,
                purchasePrice = 6500.0,
                newPotPrice = 2200.0,
                effortCost = 4100.0,
                sellingPrice = 16500.0,
                createdAtEpochMs = now - 5000L
            )
        )

        val actions = listOf(
            ScheduledActionEntity(
                id = "act-101",
                specimenId = "bns-1",
                title = "Structural / Hard Pruning",
                dueDateEpochDay = today,
                ruleDescription = "30-day canopy reset",
                intervalDays = 30
            ),
            ScheduledActionEntity(
                id = "act-102",
                specimenId = "bns-1",
                title = "Root Health & Compaction Check",
                dueDateEpochDay = today + 90,
                ruleDescription = "90 days post-hard pruning",
                intervalDays = 90
            ),
            ScheduledActionEntity(
                id = "act-201",
                specimenId = "bns-2",
                title = "Wire Bite Inspection",
                dueDateEpochDay = today - 2,
                ruleDescription = "21-day bark check",
                intervalDays = 21
            ),
            ScheduledActionEntity(
                id = "act-301",
                specimenId = "bns-3",
                title = "Maintenance Pruning & Pinching",
                dueDateEpochDay = today + 21,
                ruleDescription = "Scheduled +21 days",
                intervalDays = 21
            ),
            ScheduledActionEntity(
                id = "act-401",
                specimenId = "bns-4",
                title = "Post-Flush / Post-Bloom Pruning",
                dueDateEpochDay = today,
                ruleDescription = "Due after bract drop",
                intervalDays = 30
            )
        )

        val timeline = listOf(
            TimelineEntryEntity(
                id = "tl-1",
                specimenId = "bns-1",
                dateIso = LocalDate.now().minusMonths(2).toString(),
                taskTitle = "Aerial Root Training & Canopy Thinning",
                notes = "Guided three new aerial roots into akadama substrate; thinned upper crown for light penetration.",
                createdAtEpochMs = now - 2000L
            ),
            TimelineEntryEntity(
                id = "tl-2",
                specimenId = "bns-2",
                dateIso = LocalDate.now().minusMonths(4).toString(),
                taskTitle = "Jin & Shari Deadwood Carving",
                notes = "Cleaned weathered shari vein and applied diluted lime sulfur preservative.",
                createdAtEpochMs = now - 3000L
            )
        )

        dao.insertAllSpecimens(specimens)
        dao.insertAllActions(actions)
        dao.insertAllTimelineEntries(timeline)
    }
}
