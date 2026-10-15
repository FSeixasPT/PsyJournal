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

class RoutineProgressTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")
    private fun h(hours: Long) = Duration.ofHours(hours)
    private fun step(hours: Long) = stepsOf(RoutineKind.REGULAR).first { it.offset == h(hours) }
    private fun routine(done: Set<Long> = emptySet(), skippedBefore: Instant? = null) =
        ActiveRoutine(RoutineKind.REGULAR, dose, 250, lastPostedAt = null, skippedBefore = skippedBefore, doneOffsets = done)

    @Test
    fun doneStepsAndSkippedTimeAreStored() {
        val saved = routine(done = setOf(-240L, 60L), skippedBefore = dose.minus(h(3)))
        assertEquals(saved, ActiveRoutine.fromPreferenceString(saved.toPreferenceString()))
    }

    @Test
    fun olderSavedRoutinesLoadWithNothingDone() {
        val old = ActiveRoutine.fromPreferenceString("REGULAR|2026-10-16T22:00:00Z|250||42")
        assertEquals(emptySet<Long>(), old!!.doneOffsets)
        assertEquals(null, old.skippedBefore)
        assertEquals(42, old.linkedIngestionId)
    }

    @Test
    fun markingDoneAndUndone() {
        val marked = routine().withStepDone(step(1), isDone = true)
        assertEquals(setOf(60L), marked.doneOffsets)
        assertEquals(emptySet<Long>(), marked.withStepDone(step(1), isDone = false).doneOffsets)
    }

    @Test
    fun stepStatuses() {
        val now = dose.plus(Duration.ofMinutes(150))
        val r = routine(done = setOf(60L), skippedBefore = dose.minus(h(1)))
        assertEquals(StepStatus.SKIPPED, stepStatus(r, step(-3), now))
        assertEquals(StepStatus.NOT_MARKED, stepStatus(r, step(0), now))
        assertEquals(StepStatus.DONE, stepStatus(r, step(1), now))
        assertEquals(StepStatus.NOT_MARKED, stepStatus(r, step(2), now))
        assertEquals(StepStatus.UPCOMING, stepStatus(r, step(3), now))
        assertEquals(2, notMarkedCount(r, now))
    }

    @Test
    fun theCurrentStepIsTheLatestDueOneUntilItIsDone() {
        val justAfterDose = dose.plus(Duration.ofMinutes(1))
        assertEquals(step(0), currentStep(routine(), justAfterDose))
        assertEquals(null, currentStep(routine(done = setOf(0L)), justAfterDose))
        // skipped steps aren't current
        assertEquals(null, currentStep(routine(skippedBefore = dose), justAfterDose))
        // before the first step nothing is due
        assertEquals(null, currentStep(routine(), dose.minus(h(5))))
        // an older step left undone isn't current once a newer one is due
        assertEquals(step(1), currentStep(routine(), dose.plus(Duration.ofMinutes(61))))
    }

    @Test
    fun missedStepsAreTheEarlierOnesNotMarkedDone() {
        val now = dose.plus(Duration.ofMinutes(190))
        val r = routine(done = setOf(60L), skippedBefore = dose.minus(h(1)))
        // the dose step and +2 h are missed; +3 h is current, +1 h done, earlier ones skipped
        assertEquals(listOf(step(0), step(2)), missedSteps(r, now))
        assertEquals(emptyList<RoutineStep>(), missedSteps(r.withStepDone(step(0), true).withStepDone(step(2), true), now))
    }

    @Test
    fun waterSoFarOnlyAfterTheDose() {
        assertEquals(null, waterSoFarText(dose, dose.minus(h(1)), 250))
        // nothing to have drunk yet right after the dose
        assertEquals("Start drinking about 250 ml an hour", waterSoFarText(dose, dose.plus(Duration.ofMinutes(20)), 250))
        assertEquals("Water so far: about 1.25 L (500 ml an hour)", waterSoFarText(dose, dose.plus(Duration.ofMinutes(150)), 500))
    }

    @Test
    fun aLinkedRoutineBelongsToTheExperienceWithItsDose() {
        val linked = routine().copy(linkedIngestionId = 42)
        assertTrue(routineBelongsTo(linked, listOf(42 to dose)))
        assertFalse(routineBelongsTo(linked, listOf(7 to dose)))
    }

    @Test
    fun aPlannedRoutineBelongsToTheExperienceWithAnMdmaDoseNearItsDoseTime() {
        assertTrue(routineBelongsTo(routine(), listOf(7 to dose.plus(h(2)))))
        assertTrue(routineBelongsTo(routine(), listOf(7 to dose.minus(h(4)))))
        assertFalse(routineBelongsTo(routine(), listOf(7 to dose.plus(h(8)))))
        assertFalse(routineBelongsTo(routine(), emptyList()))
    }
}
