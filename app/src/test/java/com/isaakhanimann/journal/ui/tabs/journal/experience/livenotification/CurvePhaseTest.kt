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

package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.CurveLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class CurvePhaseTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)

    // onset 30m, comeup 30m, peak 60m, offset 60m: rises 20:30-21:00, peaks until 22:00, ends 23:00
    private val exactDuration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null)

    private fun dose(minutesAfterStart: Long, height: Float) = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = exactDuration,
        height = height,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = at(minutesAfterStart),
        endTime = null,
    )

    private fun curve(vararg doses: DataForOneEffectLine): CurveLine = AllTimelinesModel(
        dataForLines = doses.toList(),
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false
    ).substanceCurves().single().line!!

    private fun assertHint(kind: HintKind, expected: Instant, actual: PhaseWithHint) {
        val hint = actual.hint!!
        assertEquals(kind, hint.kind)
        assertTrue("$expected vs ${hint.time}", Duration.between(expected, hint.time).abs() <= Duration.ofMinutes(1))
    }

    private val single get() = curve(dose(0, 1f))

    @Test
    fun beforeTheOnsetEndsItIsWaiting() {
        val result = single.phaseWithHint(at(10))
        assertEquals(Phase.WAITING_FOR_ONSET, result.phase)
        assertHint(HintKind.STARTS, at(30), result)
    }

    @Test
    fun duringTheComeupItIsComingUp() {
        val result = single.phaseWithHint(at(45))
        assertEquals(Phase.COMING_UP, result.phase)
        assertHint(HintKind.PEAKS, at(60), result)
    }

    @Test
    fun duringThePeakItIsPeaking() {
        val result = single.phaseWithHint(at(90))
        assertEquals(Phase.PEAKING, result.phase)
        assertHint(HintKind.COMES_DOWN, at(120), result)
    }

    @Test
    fun duringTheOffsetItIsComingDown() {
        val result = single.phaseWithHint(at(150))
        assertEquals(Phase.COMING_DOWN, result.phase)
        assertHint(HintKind.ENDS, at(180), result)
    }

    @Test
    fun afterTheOffsetItHasEnded() {
        val result = single.phaseWithHint(at(210))
        assertEquals(Phase.ENDED, result.phase)
        assertNull(result.hint)
    }

    @Test
    fun aSmallTopUpDuringTheComedownStillComesDown() {
        // the top-up rises 22:30-23:00 while the first dose falls faster
        val result = curve(dose(0, 1f), dose(120, 0.1f)).phaseWithHint(at(160))
        assertEquals(Phase.COMING_DOWN, result.phase)
        // the top-up's peak until 00:00 is flat and its offset ends at 01:00
        assertHint(HintKind.ENDS, at(300), result)
    }

    @Test
    fun aTopUpThatLiftsTheLineRisesAgain() {
        // the second dose rises 22:15-22:45 while the first falls 22:00-23:00
        val line = curve(dose(0, 1f), dose(105, 1f))
        val beforeTheRise = line.phaseWithHint(at(125))
        assertEquals(Phase.COMING_DOWN, beforeTheRise.phase)
        assertHint(HintKind.RISES_AGAIN, at(135), beforeTheRise)
        assertEquals(Phase.COMING_UP, line.phaseAt(at(140)))
    }

    @Test
    fun aSlowComedownIsComingDown() {
        // like oral caffeine: an 8 hour offset falls slowly but steadily
        val slow = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(480f), null, null)
        val line = AllTimelinesModel(
            dataForLines = listOf(dose(0, 1f).copy(roaDuration = slow)),
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = false
        ).substanceCurves().single().line!!
        val result = line.phaseWithHint(at(150))
        assertEquals(Phase.COMING_DOWN, result.phase)
        assertHint(HintKind.ENDS, at(600), result)
        assertHint(HintKind.COMES_DOWN, at(120), line.phaseWithHint(at(90)))
    }

    @Test
    fun aLowFlatLineIsSteady() {
        // after the first dose ends at 23:00 the second dose's peak stays at 0.3 until 00:00
        val result = curve(dose(0, 1f), dose(120, 0.3f)).phaseWithHint(at(210))
        assertEquals(Phase.STEADY, result.phase)
        assertHint(HintKind.COMES_DOWN, at(240), result)
    }
}
