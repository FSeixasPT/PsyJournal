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

class SupplementRoutineTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")
    private fun h(hours: Long) = Duration.ofHours(hours)

    @Test
    fun regularStepsMatchRollSafe() {
        val steps = stepsOf(RoutineKind.REGULAR)
        assertEquals(listOf(-4L, -3, -1, 0, 1, 2, 3, 4, 5, 6, 7), steps.map { it.offset.toHours() })
        assertEquals(listOf(Supplement("Ginger", "2 g")), steps[0].take)
        assertEquals(listOf(Supplement("ALCAR", "500 mg"), Supplement("Vitamin C", "500 mg")), steps[1].take)
        assertEquals(listOf(Supplement("Magnesium", "100 mg")), steps[2].optional)
        assertEquals(listOf(Supplement("ALA", "300 mg"), Supplement("Vitamin C", "500 mg")), steps[3].take)
        assertEquals(listOf(Supplement("ALA", "300 mg"), Supplement("ALCAR", "500 mg")), steps[4].take)
        assertEquals(listOf(Supplement("Magnesium", "100 mg")), steps[5].optional)
        assertEquals(listOf(Supplement("ALA", "300 mg")), steps[10].take)
    }

    @Test
    fun minimalStepsMatchRollSafeWithoutWaterOnlySteps() {
        val steps = stepsOf(RoutineKind.MINIMAL)
        assertEquals(3, steps.size)
        assertEquals(Duration.ofHours(-13), steps[0].offset)
        assertEquals(listOf(Supplement("Vitamin E", "800 IU")), steps[0].take)
        assertEquals(Duration.ofMinutes(-390), steps[1].offset)
        assertEquals(listOf(Supplement("CoQ10", "200–600 mg")), steps[1].take)
        assertEquals(listOf(Supplement("ALCAR", "1000 mg")), steps[2].take)
    }

    @Test
    fun everyStepHasASupplement() {
        RoutineKind.entries.forEach { kind -> assertTrue(stepsOf(kind).all { it.take.isNotEmpty() }) }
    }

    @Test
    fun pastStepsAreNotRemaining() {
        val remaining = remainingSteps(RoutineKind.REGULAR, dose, dose.minus(Duration.ofMinutes(150)))
        assertEquals(-1L, remaining.first().offset.toHours())
    }

    @Test
    fun stepToPostIsTheLatestDueStepNotYetPosted() {
        // phone off from -4 h to +2 h 10 min: only +2 h is posted
        val step = stepToPost(RoutineKind.REGULAR, dose, dose.plus(Duration.ofMinutes(130)), lastPostedAt = dose.minus(h(4)))
        assertEquals(2L, step!!.offset.toHours())
        assertNull(stepToPost(RoutineKind.REGULAR, dose, dose.plus(Duration.ofMinutes(130)), lastPostedAt = dose.plus(h(2))))
    }

    @Test
    fun finishedAfterTheLastStep() {
        assertFalse(isFinished(RoutineKind.REGULAR, dose, dose.plus(Duration.ofMinutes(419))))
        assertTrue(isFinished(RoutineKind.REGULAR, dose, dose.plus(h(7))))
        assertFalse(isFinished(RoutineKind.MINIMAL, dose, dose.minus(h(4))))
        assertTrue(isFinished(RoutineKind.MINIMAL, dose, dose.minus(h(3))))
    }

    @Test
    fun waterTotals() {
        assertNull(waterTotalMl(dose, dose.minus(h(1)), 250))
        assertEquals(0, waterTotalMl(dose, dose, 250))
        assertEquals(500, waterTotalMl(dose, dose.plus(h(2)), 250))
        assertEquals(1000, waterTotalMl(dose, dose.plus(h(2)), 500))
        // 2 h 10 min at 250 ml = 542 ml, rounded to 500
        assertEquals(500, waterTotalMl(dose, dose.plus(Duration.ofMinutes(130)), 250))
    }
}
