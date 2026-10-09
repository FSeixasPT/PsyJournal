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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class FallbackPhaseTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)

    // onset 30m, comeup 30m, peak 60m, no offset
    private val withoutOffset = RoaDuration(minutes(30f), minutes(30f), minutes(60f), null, null, null)

    private fun line(minutesAfterStart: Long, duration: RoaDuration?, endMinutes: Long? = null) = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = duration,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = at(minutesAfterStart),
        endTime = endMinutes?.let { at(it) },
    )

    @Test
    fun knownPhasesHaveHints() {
        val result = fallbackPhase(listOf(line(0, withoutOffset)), at(90)).phaseWithHint
        assertEquals(PhaseWithHint(Phase.PEAKING, TimeHint(HintKind.COMES_DOWN, at(120))), result)
    }

    @Test
    fun afterTheLastKnownBoundaryThePhaseHasNoHint() {
        val result = fallbackPhase(listOf(line(0, withoutOffset)), at(150))
        assertEquals(PhaseWithHint(Phase.COMING_DOWN, null), result.phaseWithHint)
        assertNull(result.knownEnd)
    }

    @Test
    fun unknownEndCountsAsEndedAfter12Hours() {
        val result = fallbackPhase(listOf(line(0, withoutOffset)), at(12 * 60 + 1))
        assertEquals(Phase.ENDED, result.phaseWithHint.phase)
    }

    @Test
    fun noDurationsMeanNoTimeline() {
        assertEquals(Phase.NO_TIMELINE, fallbackPhase(listOf(line(0, null)), at(10)).phaseWithHint.phase)
    }

    @Test
    fun onlyATotalDurationIsActiveUntilItEnds() {
        val totalOnly = RoaDuration(null, null, null, null, minutes(240f), null)
        val during = fallbackPhase(listOf(line(0, totalOnly)), at(60))
        assertEquals(PhaseWithHint(Phase.ACTIVE, TimeHint(HintKind.ENDS, at(240))), during.phaseWithHint)
        assertEquals(at(240), during.knownEnd)
        assertEquals(Phase.ENDED, fallbackPhase(listOf(line(0, totalOnly)), at(250)).phaseWithHint.phase)
    }

    @Test
    fun theMostRecentActiveIngestionDecides() {
        val result = fallbackPhase(listOf(line(0, withoutOffset), line(120, withoutOffset)), at(135)).phaseWithHint
        assertEquals(PhaseWithHint(Phase.WAITING_FOR_ONSET, TimeHint(HintKind.STARTS, at(150))), result)
    }

    @Test
    fun aTimeRangePeaksUntilItsEndPlusThePeak() {
        val result = fallbackPhase(listOf(line(0, withoutOffset, endMinutes = 90)), at(135)).phaseWithHint
        assertEquals(PhaseWithHint(Phase.PEAKING, TimeHint(HintKind.COMES_DOWN, at(150))), result)
    }
}
