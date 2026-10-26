package com.isaakhanimann.journal.ui.tabs.stats

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartMetricTest {
    private val colors = mapOf("MDMA" to AdaptiveColor.PINK, "LSD" to AdaptiveColor.BLUE)

    private fun ingestion(experienceId: Int, substanceName: String, dose: Double? = null, units: String? = "mg") =
        ChartIngestion(experienceId = experienceId, substanceName = substanceName, dose = dose, units = units)

    // a night with two MDMA doses and one LSD dose, and another night with one MDMA dose
    private val bucket = listOf(
        ingestion(1, "MDMA", 100.0),
        ingestion(1, "MDMA", 40.0),
        ingestion(1, "LSD", 100.0, "µg"),
        ingestion(2, "MDMA", 120.0),
    )

    @Test
    fun experiencesCountEachSubstanceOncePerExperience() {
        assertEquals(
            listOf(ColorCount(AdaptiveColor.PINK, 2.0), ColorCount(AdaptiveColor.BLUE, 1.0)),
            barSegments(bucket, ChartMetric.EXPERIENCES, colors)
        )
    }

    @Test
    fun ingestionsCountEveryDose() {
        assertEquals(
            listOf(ColorCount(AdaptiveColor.PINK, 3.0), ColorCount(AdaptiveColor.BLUE, 1.0)),
            barSegments(bucket, ChartMetric.INGESTIONS, colors)
        )
    }

    @Test
    fun totalDoseAddsUpTheChosenSubstanceInItsUnit() {
        assertEquals(
            listOf(ColorCount(AdaptiveColor.PINK, 260.0)),
            barSegments(bucket, ChartMetric.TOTAL_DOSE, colors, DoseChoice(substanceName = "MDMA", units = "mg"))
        )
    }

    @Test
    fun totalDoseLeavesOutUnknownDosesAndOtherUnits() {
        val mixed = listOf(ingestion(1, "MDMA", 100.0), ingestion(1, "MDMA", null), ingestion(2, "MDMA", 1.0, "pills"))
        assertEquals(
            listOf(ColorCount(AdaptiveColor.PINK, 100.0)),
            barSegments(mixed, ChartMetric.TOTAL_DOSE, colors, DoseChoice(substanceName = "MDMA", units = "mg"))
        )
    }

    @Test
    fun aBucketWithoutTheChosenSubstanceIsEmpty() {
        assertEquals(
            emptyList<ColorCount>(),
            barSegments(listOf(ingestion(1, "LSD", 100.0, "µg")), ChartMetric.TOTAL_DOSE, colors, DoseChoice("MDMA", "mg"))
        )
    }

    @Test
    fun aSubstanceWithoutAColorIsLeftOut() {
        assertEquals(emptyList<ColorCount>(), barSegments(listOf(ingestion(1, "2C-B")), ChartMetric.INGESTIONS, colors))
    }

    @Test
    fun theDoseIsChartedInTheUnitUsedMost() {
        val ingestions = listOf(ingestion(1, "MDMA", 1.0, "pills"), ingestion(2, "MDMA", 100.0), ingestion(3, "MDMA", 120.0))
        assertEquals(DoseChoice("MDMA", "mg"), doseChoice(ingestions, "MDMA"))
    }

    @Test
    fun withoutAnyKnownDoseThereIsNothingToChart() {
        assertNull(doseChoice(listOf(ingestion(1, "MDMA", null)), "MDMA"))
    }

    @Test
    fun theDoseLeftOutIsCounted() {
        val ingestions = listOf(ingestion(1, "MDMA", 100.0), ingestion(1, "MDMA", null), ingestion(2, "MDMA", 1.0, "pills"), ingestion(2, "LSD", null))
        assertEquals(2, dosesLeftOut(ingestions, DoseChoice("MDMA", "mg")))
    }

    @Test
    fun doseSubstancesFollowTheListOrderMostExperiencesThenMostRecent() {
        val newestFirst = listOf(
            ingestion(5, "MDMA", 100.0),
            ingestion(4, "LSD", 100.0, "µg"),
            ingestion(3, "Cannabis", null),
            ingestion(2, "LSD", 80.0, "µg"),
        )
        assertEquals(listOf("LSD", "MDMA"), doseSubstances(newestFirst))
    }
}
