package com.isaakhanimann.journal.ui.tabs.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DoseChartTest {
    private val end = Instant.parse("2026-10-26T23:59:59Z")

    private fun dose(daysAgo: Long, dose: Double?, units: String? = "mg") =
        TimedDose(time = end.minus(Duration.ofDays(daysAgo)).minusSeconds(60), dose = dose, units = units)

    @Test
    fun dosesAreAddedUpPerDayOldestFirst() {
        val chart = doseChart(listOf(dose(0, 100.0), dose(0, 40.0), dose(6, 120.0)), TimePickerOption.DAYS_7, end)!!
        assertEquals("mg", chart.units)
        assertEquals(listOf(120.0, 0.0, 0.0, 0.0, 0.0, 0.0, 140.0), chart.buckets)
    }

    @Test
    fun dosesBeforeTheRangeAreLeftOut() {
        val chart = doseChart(listOf(dose(0, 100.0), dose(7, 120.0)), TimePickerOption.DAYS_7, end)!!
        assertEquals(100.0, chart.buckets.sum(), 0.0)
        assertEquals(0, chart.dosesLeftOut)
    }

    @Test
    fun theUnitUsedMostInTheRangeIsCharted() {
        val chart = doseChart(listOf(dose(0, 1.0, "pills"), dose(1, 100.0), dose(2, 80.0)), TimePickerOption.DAYS_7, end)!!
        assertEquals("mg", chart.units)
        assertEquals(180.0, chart.buckets.sum(), 0.0)
    }

    @Test
    fun unknownDosesAndOtherUnitsAreCountedAsLeftOut() {
        val chart = doseChart(listOf(dose(0, 100.0), dose(1, null), dose(2, 1.0, "pills")), TimePickerOption.DAYS_7, end)!!
        assertEquals(2, chart.dosesLeftOut)
    }

    @Test
    fun withoutAKnownDoseInTheRangeThereIsNoChart() {
        assertNull(doseChart(listOf(dose(0, null), dose(30, 100.0)), TimePickerOption.DAYS_7, end))
    }
}
