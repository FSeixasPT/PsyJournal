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

class RoutineOfferTest {
    private val now = Instant.parse("2026-10-16T23:00:00Z")

    @Test
    fun offeredForARecentDose() {
        val dose = now.minus(Duration.ofMinutes(30))
        assertEquals(dose, routineOfferDose(listOf(dose), now, isRoutineRunning = false, isDismissed = false))
    }

    @Test
    fun notOfferedWhenOldRunningOrDismissed() {
        val dose = now.minus(Duration.ofMinutes(30))
        assertNull(routineOfferDose(listOf(now.minus(Duration.ofHours(3))), now, false, false))
        assertNull(routineOfferDose(listOf(dose), now, isRoutineRunning = true, isDismissed = false))
        assertNull(routineOfferDose(listOf(dose), now, isRoutineRunning = false, isDismissed = true))
        assertNull(routineOfferDose(emptyList(), now, false, false))
    }

    @Test
    fun notOfferedWhenSuggestionsAreTurnedOff() {
        val dose = now.minus(Duration.ofMinutes(30))
        assertNull(routineOfferDose(listOf(dose), now, isRoutineRunning = false, isDismissed = false, isSuggestionOn = false))
    }

    @Test
    fun startingFromTheMenuUsesTheLatestOwnMdmaDoseHoweverOld() {
        val old = now.minus(Duration.ofHours(5))
        val older = now.minus(Duration.ofHours(6))
        assertEquals(8 to old, latestDose(listOf(7 to older, 8 to old)))
        assertNull(latestDose(emptyList()))
    }

    @Test
    fun menuItemIsHiddenWithoutADoseWhileThisRoutineRunsOrOnceItWouldBeOver() {
        val dose = now.minus(Duration.ofHours(1))
        val mine = ActiveRoutine(RoutineKind.REGULAR, dose, 250, now, linkedIngestionId = 8)
        assertNull(routineMenuState(emptyList(), null, now))
        assertNull(routineMenuState(listOf(8 to dose), mine, now))
        assertNull(routineMenuState(listOf(8 to now.minus(Duration.ofHours(8))), null, now))
    }

    @Test
    fun menuItemAsksBeforeReplacingAnotherExperiencesRoutine() {
        val dose = now.minus(Duration.ofHours(1))
        // planned from the MDMA page for a dose in 5 hours, so not about this experience's dose
        val other = ActiveRoutine(RoutineKind.REGULAR, now.plus(Duration.ofHours(5)), 250, null)
        assertEquals(false, routineMenuState(listOf(8 to dose), null, now))
        assertEquals(true, routineMenuState(listOf(8 to dose), other, now))
    }

    @Test
    fun dismissingIsRememberedForTheDosesNotTheExperienceId() {
        val dose = now.minus(Duration.ofMinutes(30))
        // a redose in the same experience stays dismissed
        assertEquals(true, isRoutineOfferDismissed(listOf(8 to dose.minus(Duration.ofHours(1)), 9 to dose), setOf(8)))
        // a new experience that got a deleted experience's id has new doses, so it's offered again
        assertEquals(false, isRoutineOfferDismissed(listOf(12 to dose), setOf(8, 9)))
    }
}
