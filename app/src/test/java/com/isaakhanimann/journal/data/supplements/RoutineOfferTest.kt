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
}
