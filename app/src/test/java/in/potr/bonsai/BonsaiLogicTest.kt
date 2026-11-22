package `in`.potr.bonsai

import `in`.potr.bonsai.cloud.SemVerComparator
import `in`.potr.bonsai.data.model.BackupPayload
import `in`.potr.bonsai.data.model.BotanicalTipsLibrary
import `in`.potr.bonsai.data.model.CareActionCategories
import `in`.potr.bonsai.data.model.CareActionEntity
import `in`.potr.bonsai.data.model.CodeGenerator
import `in`.potr.bonsai.data.model.DefaultBonsaiActions
import `in`.potr.bonsai.data.model.DueDateHelper
import `in`.potr.bonsai.data.model.ScheduledActionEntity
import `in`.potr.bonsai.data.model.SpecimenEntity
import `in`.potr.bonsai.data.model.TimelineEntryEntity
import `in`.potr.bonsai.data.model.UploadStatus
import `in`.potr.bonsai.data.model.ValuationCalculator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BonsaiLogicTest {

    @Test
    fun valuationCalculatorSumsNonNegativeCosts() {
        assertEquals(24000.0, ValuationCalculator.estimatedCurrentPrice(12000.0, 3500.0, 8500.0), 0.001)
        assertEquals(5000.0, ValuationCalculator.estimatedCurrentPrice(5000.0, -100.0, 0.0), 0.001)
    }

    @Test
    fun codeGeneratorZeroPadsAndAvoidsCollisionAfterDeletions() {
        assertEquals("BNS-0001", CodeGenerator.nextSpecimenCode(emptyList()))
        assertEquals("BNS-0704", CodeGenerator.nextSpecimenCode(listOf("BNS-0042", "BNS-0703", "BNS-0008")))
    }

    @Test
    fun dueDateHelperComputesDynamicCountdownAndLabels() {
        val today = 20000L
        assertEquals(-3L, DueDateHelper.daysUntilDue(19997L, today))
        assertEquals("Overdue 3d", DueDateHelper.formatDueLabel(-3L))
        assertEquals("Due Today", DueDateHelper.formatDueLabel(0L))
        assertEquals("In 30d", DueDateHelper.formatDueLabel(30L))
    }

    @Test
    fun semVerComparatorDoesNotOfferDowngrades() {
        assertTrue(SemVerComparator.isNewer("2.2.0", "2.1.0"))
        assertTrue(SemVerComparator.isNewer("v2.1.1", "2.1.0"))
        assertFalse(SemVerComparator.isNewer("2.0.0", "2.1.0"))
        assertFalse(SemVerComparator.isNewer("2.1.0", "2.1.0"))
    }

    @Test
    fun customSpeciesGetsGeneralTipInsteadOfFicusTip() {
        val tip = BotanicalTipsLibrary.tipFor("Premna microphylla", 0)
        assertEquals("POTR Studio Notes", tip.source)
    }

    @Test
    fun defaultGlobalBonsaiActionsAreComprehensiveAndUnique() {
        val actions = DefaultBonsaiActions.all
        assertTrue("Expected comprehensive default action library", actions.size >= 25)
        assertEquals("Action IDs must be unique", actions.size, actions.map { it.id }.toSet().size)
        assertTrue("All actions must have non-blank title and positive defaultDays", actions.all {
            it.title.isNotBlank() && it.defaultDays > 0 && it.category in CareActionCategories.all
        })
    }

    @Test
    fun backupPayloadV2RoundTripsAndDecodesLegacyV1Backups() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val payload = BackupPayload(
            schemaVersion = 2,
            appVersion = "2.1.0",
            exportedAtEpochMs = 1795320000000L,
            specimens = listOf(
                SpecimenEntity(
                    id = "bns-1",
                    code = "BNS-0001",
                    name = "Test Banyan",
                    species = "Ficus microcarpa",
                    drivePhotoFileId = "drv_123",
                    wooProductId = 55
                )
            ),
            careActions = listOf(
                CareActionEntity(
                    id = "ca-custom-1",
                    title = "Monsoon Flush",
                    category = CareActionCategories.FEEDING,
                    defaultDays = 45,
                    description = "Deep flush after heavy feed"
                )
            ),
            scheduledActions = listOf(
                ScheduledActionEntity("a-1", "bns-1", "Structural / Hard Pruning", 20030L, "+30d", 30)
            ),
            timelineEntries = listOf(
                TimelineEntryEntity(
                    id = "t-1",
                    specimenId = "bns-1",
                    dateIso = "2026-11-21",
                    taskTitle = "Structural / Hard Pruning",
                    notes = "Thinned crown",
                    beforeYoutubeUrl = "https://youtu.be/abc123",
                    beforeUploadStatus = UploadStatus.UPLOADED
                )
            )
        )

        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<BackupPayload>(encoded)
        assertEquals(payload, decoded)

        // Legacy v1 JSON (had schedules/careRules instead of careActions) should decode cleanly
        val legacyV1Json = """
            {
              "schemaVersion": 1,
              "appVersion": "2.0.0",
              "exportedAtEpochMs": 1795320000000,
              "specimens": [],
              "schedules": [{"speciesKey": "ficus microcarpa", "displayName": "Ficus microcarpa"}],
              "careRules": [{"id": "r-1", "speciesKey": "ficus microcarpa", "title": "Hard Pruning", "intervalDays": 30}],
              "scheduledActions": [],
              "timelineEntries": []
            }
        """.trimIndent()
        val decodedLegacy = json.decodeFromString<BackupPayload>(legacyV1Json)
        assertEquals(1, decodedLegacy.schemaVersion)
        assertTrue(decodedLegacy.careActions.isEmpty())
    }
}
