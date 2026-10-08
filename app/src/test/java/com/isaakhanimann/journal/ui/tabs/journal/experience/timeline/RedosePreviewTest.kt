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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class RedosePreviewTest {

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
        substanceName: String = "Ketamine",
        route: AdministrationRoute = AdministrationRoute.INSUFFLATED,
        units: String = "mg",
        roaDuration: RoaDuration? = exactDuration,
    ) = DataForOneEffectLine(
        substanceName = substanceName,
        route = route,
        roaDuration = roaDuration,
        height = if (dose != null) (dose / 100.0).toFloat() else 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.PINK,
        startTime = start.plus(minutesAfterStart, ChronoUnit.MINUTES),
        endTime = null,
        referenceDose = if (dose != null) 100.0 else null,
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
    fun plannedIngestionIsAddedToTheCurve() {
        // 100 mg at 0 min is fully worn off at 180 min; 50 mg planned at 120 min is at its peak from 180 min
        val real = model(line(0, dose = 100.0))
        val preview = real.withPlannedIngestion(line(120, dose = 50.0))
        val detail = preview.getQuantityDetails(at(180)).single()
        assertEquals(50.0, detail.doseEquivalents.single().dose, 0.01)
        assertEquals(at(120), preview.plannedIngestionTime)
    }

    @Test
    fun plannedIngestionDoesNotChangeTheRealModel() {
        val real = model(line(0, dose = 100.0))
        real.withPlannedIngestion(line(120, dose = 50.0))
        assertEquals(0.0, real.getQuantityDetails(at(180)).single().doseEquivalents.single().dose, 0.01)
        assertNull(real.plannedIngestionTime)
    }

    @Test
    fun plannedPeakIsTheHighestPointOfTheCombinedCurve() {
        // 100 mg at 0 min (peak 60-120 min) and 50 mg planned at 60 min (peak 120-180 min) add up to 150 mg at 120 min
        val real = model(line(0, dose = 100.0))
        val peak = real.withPlannedIngestion(line(60, dose = 50.0)).plannedPeak!!
        assertEquals(150.0, peak.doseEquivalents.single().dose, 0.01)
        assertEquals("mg", peak.doseEquivalents.single().units)
        assertEquals(at(120), peak.time)
    }

    @Test
    fun plannedPeakOnlyLooksAtTheTimeFromThePlannedIngestionOn() {
        // 100 mg at 0 min is at 50 mg equivalent at 150 min and wearing off; 10 mg planned at 150 min only reaches 10
        val real = model(line(0, dose = 100.0))
        val peak = real.withPlannedIngestion(line(150, dose = 10.0)).plannedPeak!!
        assertEquals(50.0, peak.doseEquivalents.single().dose, 0.01)
        assertEquals(at(150), peak.time)
    }

    @Test
    fun realModelHasNoPlannedPeak() {
        assertNull(model(line(0, dose = 100.0)).plannedPeak)
    }

    @Test
    fun previewExtendsTheTimelineToShowTheWholePlannedCurve() {
        val real = model(line(0, dose = 100.0))
        val preview = real.withPlannedIngestion(line(120, dose = 50.0))
        // planned curve ends 120 + 180 = 300 minutes after start
        assertTrue(preview.widthInSeconds >= 300 * 60f)
    }

    @Test
    fun previewKeepsTheRealCurveOfThePlannedSubstanceAsBaseline() {
        // 100 mg at 0 min and 50 mg planned at 120 min, which is at its peak from 180 min
        val real = model(line(0, dose = 100.0))
        val preview = real.withPlannedIngestion(line(120, dose = 50.0))
        val group = preview.groupDrawables.single()
        val baseline = group.previewBaseline!!
        val realCurve = real.groupDrawables.single().fullTimelines!!
        assertEquals(realCurve.heightAt(90 * 60f), baseline.heightAt(90 * 60f), 0.001f)
        assertEquals(0f, baseline.heightAt(180 * 60f), 0.001f)
        assertEquals(0.5f, group.fullTimelines!!.heightAt(180 * 60f), 0.001f)
    }

    @Test
    fun baselineIsDrawnOnTheSameScaleAsThePreview() {
        val real = model(line(0, dose = 100.0))
        val preview = real.withPlannedIngestion(line(60, dose = 100.0))
        val group = preview.groupDrawables.single()
        assertEquals(group.fullTimelines!!.referenceHeight, group.previewBaseline!!.referenceHeight, 0.001f)
    }

    @Test
    fun realModelHasNoBaseline() {
        assertNull(model(line(0, dose = 100.0)).groupDrawables.single().previewBaseline)
    }

    @Test
    fun plannedIngestionHasTheColorOfItsSubstance() {
        val real = model(line(0, dose = 100.0))
        val preview = real.withPlannedIngestion(line(120, dose = 50.0))
        assertEquals(AdaptiveColor.PINK, preview.plannedIngestionColor)
    }

    @Test
    fun fractionOfPeakWithRedoseIsMeasuredOnTheCombinedCurve() {
        // 100 mg at 0 and 50 mg planned at 60 min: combined curve peaks at 150 mg equivalent at 120 min
        // at 150 min: 50 (first dose halfway down) + 50 (planned at peak) = 100 of 150
        val real = model(line(0, dose = 100.0))
        val preview = real.withPlannedIngestion(line(60, dose = 50.0))
        assertEquals(100f / 150f, preview.plannedFractionOfPeakAt(at(150))!!, 0.001f)
    }

    @Test
    fun realModelHasNoFractionWithRedose() {
        assertNull(model(line(0, dose = 100.0)).plannedFractionOfPeakAt(at(90)))
    }

    @Test
    fun plannedSubstanceOfTheExperienceIsARedose() {
        val real = model(line(0, dose = 100.0))
        assertEquals(true, real.withPlannedIngestion(line(120, dose = 50.0)).isPlannedRedose)
        val other = line(120, dose = 50.0).copy(substanceName = "MDMA")
        assertEquals(false, real.withPlannedIngestion(other).isPlannedRedose)
        assertEquals(false, real.isPlannedRedose)
    }

    @Test
    fun effectTimesOfThePlannedIngestion() {
        // planned at 60 min: onset 30, comeup 30, peak 60, offset 60 minutes
        val times = model(line(0, dose = 100.0)).withPlannedIngestion(line(60, dose = 50.0)).plannedEffectTimes!!
        assertEquals(at(90), times.start)
        assertEquals(at(120), times.peakStart)
        assertEquals(at(180), times.peakEnd)
        assertEquals(at(240), times.end)
    }
}
