package com.isaakhanimann.journal.ui.tabs.stats

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMetricTest {
    private val colors = mapOf("MDMA" to AdaptiveColor.PINK, "LSD" to AdaptiveColor.BLUE)

    private fun ingestion(experienceId: Int, substanceName: String) =
        ChartIngestion(experienceId = experienceId, substanceName = substanceName)

    // a night with two MDMA doses and one LSD dose, and another night with one MDMA dose
    private val bucket = listOf(
        ingestion(1, "MDMA"),
        ingestion(1, "MDMA"),
        ingestion(1, "LSD"),
        ingestion(2, "MDMA"),
    )

    @Test
    fun theMainChartOffersExperiencesAndIngestionsOnly() {
        assertEquals(listOf(ChartMetric.EXPERIENCES, ChartMetric.INGESTIONS), ChartMetric.entries.toList())
    }

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
    fun aSubstanceWithoutAColorIsLeftOut() {
        assertEquals(emptyList<ColorCount>(), barSegments(listOf(ingestion(1, "2C-B")), ChartMetric.INGESTIONS, colors))
    }
}
