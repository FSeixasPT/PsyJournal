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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RoutineFixesTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")
    private fun min(m: Long) = Duration.ofMinutes(m)

    @Test
    fun farStepsGetHalfwayCheckpoints() {
        val now = dose.minus(Duration.ofHours(11))
        val step = dose.minus(Duration.ofHours(4))
        // 7 h away: wake up halfway, so a late delivery can never pass the step
        assertEquals(now.plus(Duration.ofMinutes(210)), nextCheckpoint(now, step))
        // close steps are armed directly
        assertEquals(step, nextCheckpoint(step.minus(min(10)), step))
    }

    @Test
    fun aDoseStepJustPastIsStillShownWhenStarting() {
        val now = dose.plus(min(5))
        val lastPosted = initialLastPostedAt(RoutineKind.REGULAR, dose, now)
        assertEquals(0L, stepToPost(RoutineKind.REGULAR, dose, now, lastPosted)!!.offset.toMinutes())
    }

    @Test
    fun stepsLongPastAreStillSkippedWhenStarting() {
        val now = dose.plus(min(45))
        val lastPosted = initialLastPostedAt(RoutineKind.REGULAR, dose, now)
        assertEquals(null, stepToPost(RoutineKind.REGULAR, dose, now, lastPosted))
    }

    @Test
    fun aRoutineIsActiveOnlyUntilItsLastStep() {
        assertTrue(isRoutineActive(ActiveRoutine(RoutineKind.REGULAR, dose, 250, null), dose))
        assertFalse(isRoutineActive(ActiveRoutine(RoutineKind.REGULAR, dose.minus(Duration.ofHours(8)), 250, null), dose))
        assertFalse(isRoutineActive(null, dose))
    }
}
