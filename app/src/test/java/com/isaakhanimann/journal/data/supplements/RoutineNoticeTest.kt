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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RoutineNoticeTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")
    private val time: (Instant) -> String = { "T" + Duration.between(dose, it).toMinutes() }
    private val dayTime: (Instant) -> String = { "D" + Duration.between(dose, it).toHours() }
    private val routine = ActiveRoutine(RoutineKind.REGULAR, dose, 250, lastPostedAt = null)
    private fun step(hours: Long) = stepsOf(RoutineKind.REGULAR).first { it.offset == Duration.ofHours(hours) }
    private fun notice(routine: ActiveRoutine, minutesAfterDose: Long) =
        routineNotice(routine, dose.plus(Duration.ofMinutes(minutesAfterDose)), dose, time, dayTime)

    @Test
    fun aDueStepNotMarkedDoneIsTheReminderWithDone() {
        val notice = notice(routine, 10)!!
        assertTrue(notice.isDue)
        assertEquals(step(0), notice.step)
        assertEquals("MDMA routine · with your dose (T0)", notice.title)
        assertEquals(takeLine(step(0)), notice.lines.first())
    }

    @Test
    fun onceDoneItShowsTheNextStepAndTheWaterSoFar() {
        val notice = notice(routine.withStepDone(step(0), true), 10)!!
        assertFalse(notice.isDue)
        assertEquals(step(1), notice.step)
        assertEquals("MDMA routine · next: +1 h (T60)", notice.title)
        assertEquals(listOf(takeLine(step(1)), "Start drinking about 250 ml an hour"), notice.lines)
    }

    @Test
    fun beforeTheDoseTheNextStepSaysWhenTheDoseIsPlanned() {
        val notice = notice(routine.copy(skippedBefore = dose.minus(Duration.ofHours(5))), -300)!!
        assertFalse(notice.isDue)
        assertEquals(step(-4), notice.step)
        assertEquals("Planned MDMA at T0.", notice.lines.last())
    }

    @Test
    fun skippedStepsAreNeverDue() {
        // started 40 minutes after the dose: the dose step was skipped, so +1 h is next
        val started = routine.copy(skippedBefore = dose.plus(Duration.ofMinutes(40)))
        val notice = notice(started, 45)!!
        assertFalse(notice.isDue)
        assertEquals(step(1), notice.step)
    }

    @Test
    fun nothingOnceTheLastStepIsDone() {
        val last = stepsOf(RoutineKind.REGULAR).last()
        val done = routine.withStepDone(last, true)
        assertNull(notice(done, last.offset.toMinutes() + 5))
    }
}
