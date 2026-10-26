package com.isaakhanimann.journal.ui.tabs.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsCalculationsTest {
    private fun entry(dose: Double?, units: String? = "mg", isEstimate: Boolean = false, standardDeviation: Double? = null) =
        DoseEntry(dose = dose, units = units, isEstimate = isEstimate, standardDeviation = standardDeviation)

    @Test
    fun anUnknownDoseDoesNotHideTheKnownOnes() {
        val totals = totalDoses(listOf(entry(100.0), entry(null), entry(80.0)))
        assertEquals(listOf(TotalDose(dose = 180.0, units = "mg", isEstimate = false, estimatedDoseStandardDeviation = null)), totals.totals)
        assertEquals(1, totals.unknownCount)
    }

    @Test
    fun eachUnitGetsItsOwnTotalMostUsedFirst() {
        val totals = totalDoses(listOf(entry(1.0, "pills"), entry(100.0), entry(120.0), entry(2.0, "pills"), entry(50.0)))
        assertEquals(listOf("mg" to 270.0, "pills" to 3.0), totals.totals.map { it.units to it.dose })
        assertEquals(0, totals.unknownCount)
    }

    @Test
    fun aTotalIsAnEstimateIfAnyOfItsDosesIs() {
        val totals = totalDoses(listOf(entry(100.0), entry(20.0, isEstimate = true, standardDeviation = 5.0)))
        assertEquals(true, totals.totals.single().isEstimate)
        assertEquals(5.0, totals.totals.single().estimatedDoseStandardDeviation)
    }

    @Test
    fun aDoseWithoutUnitsCountsAsUnknown() {
        val totals = totalDoses(listOf(entry(3.0, units = null), entry(null)))
        assertEquals(emptyList<TotalDose>(), totals.totals)
        assertEquals(2, totals.unknownCount)
    }

    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun lastTakenSaysHowLongAgo() {
        assertEquals("last taken today", lastTakenText(today, today))
        assertEquals("last taken yesterday", lastTakenText(today.minusDays(1), today))
        assertEquals("last taken 12 days ago", lastTakenText(today.minusDays(12), today))
        assertEquals("last taken 59 days ago", lastTakenText(today.minusDays(59), today))
    }

    @Test
    fun longAgoIsSaidInMonthsThenYears() {
        assertEquals("last taken 3 months ago", lastTakenText(today.minusMonths(3), today))
        assertEquals("last taken 2 years ago", lastTakenText(today.minusYears(2), today))
    }
}
