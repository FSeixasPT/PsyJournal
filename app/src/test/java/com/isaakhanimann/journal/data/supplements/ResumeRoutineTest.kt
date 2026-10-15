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

package com.isaakhanimann.journal.data.supplements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ResumeRoutineTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")
    private fun after(minutes: Long): Instant = dose.plus(Duration.ofMinutes(minutes))
    private fun step(hours: Long) = stepsOf(RoutineKind.REGULAR).first { it.offset == Duration.ofHours(hours) }

    // started 40 minutes after the dose, dose step skipped, +1 h ticked, then stopped at +1 h 10
    private val stopped = ActiveRoutine(
        RoutineKind.REGULAR, dose, 500, lastPostedAt = after(60), linkedIngestionId = 8, skippedBefore = after(40)
    ).withStepDone(step(1), true)

    @Test
    fun startingAgainForTheSameDoseKeepsTheTicksAndWhatWasSkipped() {
        val resumed = resumedRoutine(stopped, RoutineKind.REGULAR, dose, linkedIngestionId = 8, now = after(250))!!
        assertEquals(setOf(60L), resumed.doneOffsets)
        assertEquals(after(40), resumed.skippedBefore)
        assertEquals(500, resumed.mlPerHour)
        // +2 h and +3 h came due while it was stopped: they're missed, not skipped
        assertEquals(StepStatus.NOT_MARKED, stepStatus(resumed, step(2), after(250)))
        assertEquals(StepStatus.NOT_MARKED, stepStatus(resumed, step(3), after(250)))
        assertEquals(StepStatus.SKIPPED, stepStatus(resumed, step(0), after(250)))
    }

    @Test
    fun oldStepsDontAlertAgainButOneFromTheLastHalfHourDoes() {
        // at +4 h 35 nothing is from the last half hour
        val late = resumedRoutine(stopped, RoutineKind.REGULAR, dose, 8, now = after(275))!!
        assertNull(stepToPost(late.kind, late.doseTime, after(275), late.lastPostedAt))
        // at +2 h 10 the +2 h step is
        val recent = resumedRoutine(stopped, RoutineKind.REGULAR, dose, 8, now = after(130))!!
        assertEquals(step(2), stepToPost(recent.kind, recent.doseTime, after(130), recent.lastPostedAt))
        // at +1 h 10 the +1 h step was already shown before stopping
        val same = resumedRoutine(stopped, RoutineKind.REGULAR, dose, 8, now = after(70))!!
        assertNull(stepToPost(same.kind, same.doseTime, after(70), same.lastPostedAt))
    }

    @Test
    fun anotherDoseStartsFresh() {
        assertNull(resumedRoutine(stopped, RoutineKind.REGULAR, dose, linkedIngestionId = 9, now = after(250)))
        assertNull(resumedRoutine(stopped, RoutineKind.REGULAR, after(30), linkedIngestionId = 8, now = after(250)))
        assertNull(resumedRoutine(stopped, RoutineKind.MINIMAL, dose, linkedIngestionId = 8, now = after(250)))
        assertNull(resumedRoutine(null, RoutineKind.REGULAR, dose, linkedIngestionId = 8, now = after(250)))
    }
}
