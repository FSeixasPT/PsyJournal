/*
 * Copyright (c) 2026. Francisco Seixas.
 * This file is part of PsyJournal, a fork of PsychonautWiki Journal.
 *
 * PsyJournal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsyJournal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsyJournal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class TimelineQuantityDetailsTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")

    // onset 30m, comeup 30m, peak 60m, offset 60m (min == max so weights don't matter)
    private val exactDuration = RoaDuration(
        onset = DurationRange(30f, 30f, DurationUnits.MINUTES),
        comeup = DurationRange(30f, 30f, DurationUnits.MINUTES),
        peak = DurationRange(60f, 60f, DurationUnits.MINUTES),
        offset = DurationRange(60f, 60f, DurationUnits.MINUTES),
        total = null,
        afterglow = null
    )

    private fun line(
        minutesAfterStart: Long,
        dose: Double?,
        referenceDose: Double? = 100.0,
        units: String? = "mg",
        roaDuration: RoaDuration? = exactDuration,
    ) = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = roaDuration,
        height = if (dose != null && referenceDose != null) (dose / referenceDose).toFloat() else 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = start.plus(minutesAfterStart, ChronoUnit.MINUTES),
        endTime = null,
        referenceDose = if (dose != null) referenceDose else null,
        doseUnits = if (dose != null) units else null
    )

    private fun model(vararg lines: DataForOneEffectLine) = AllTimelinesModel(
        dataForLines = lines.toList(),
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false
    )

    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)

    @Test
    fun singleDoseIsAtFullPeakDuringPeak() {
        val detail = model(line(0, dose = 100.0)).getQuantityDetails(at(90)).single()
        assertEquals("TestSubstance", detail.substanceName)
        assertEquals(1f, detail.fractionOfPeak, 0.001f)
        assertEquals(100.0, detail.doseEquivalents.single().dose, 0.01)
        assertEquals("mg", detail.doseEquivalents.single().units)
    }

    @Test
    fun singleDoseIsHalfwayThroughComeup() {
        val detail = model(line(0, dose = 100.0)).getQuantityDetails(at(45)).single()
        assertEquals(0.5f, detail.fractionOfPeak, 0.001f)
        assertEquals(50.0, detail.doseEquivalents.single().dose, 0.01)
    }

    @Test
    fun singleDoseIsZeroBeforeOnsetAndAfterOffset() {
        val model = model(line(0, dose = 100.0))
        assertEquals(0f, model.getQuantityDetails(at(10)).single().fractionOfPeak, 0.001f)
        assertEquals(0f, model.getQuantityDetails(at(200)).single().fractionOfPeak, 0.001f)
    }

    @Test
    fun redoseIsRelativeToHighestPointOfCombinedCurve() {
        // 100 mg at 0 min and 50 mg at 60 min: combined curve peaks at 150 mg equivalent (120-180 min)
        // at 150 min: first dose is halfway down (50) and second dose is at peak (50) = 100 mg
        val detail = model(line(0, dose = 100.0), line(60, dose = 50.0))
            .getQuantityDetails(at(150)).single()
        assertEquals(100.0, detail.doseEquivalents.single().dose, 0.01)
        assertEquals(100f / 150f, detail.fractionOfPeak, 0.001f)
    }

    @Test
    fun unknownDoseShowsPercentageWithoutDoseEquivalent() {
        val detail = model(line(0, dose = 100.0), line(30, dose = null))
            .getQuantityDetails(at(90)).single()
        assertTrue(detail.doseEquivalents.isEmpty())
        assertTrue(detail.fractionOfPeak > 0f)
    }

    @Test
    fun mixedUnitsShowPercentageWithoutDoseEquivalent() {
        val detail = model(line(0, dose = 100.0, units = "mg"), line(30, dose = 100.0, units = "µg"))
            .getQuantityDetails(at(90)).single()
        assertTrue(detail.doseEquivalents.isEmpty())
    }

    @Test
    fun substanceWithIncompleteDurationIsLeftOut() {
        val incomplete = exactDuration.copy(peak = null)
        val details = model(line(0, dose = 100.0, roaDuration = incomplete)).getQuantityDetails(at(90))
        assertTrue(details.isEmpty())
    }
}
