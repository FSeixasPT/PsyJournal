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
import java.time.Instant

class LinkedDoseTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")

    @Test
    fun theLinkIsStored() {
        val routine = ActiveRoutine(RoutineKind.REGULAR, dose, 250, lastPostedAt = null, linkedIngestionId = 42)
        assertEquals(routine, ActiveRoutine.fromPreferenceString(routine.toPreferenceString()))
    }

    @Test
    fun routinesSavedBeforeTheLinkStillLoad() {
        val old = ActiveRoutine.fromPreferenceString("REGULAR|2026-10-16T22:00:00Z|250|")
        assertEquals(ActiveRoutine(RoutineKind.REGULAR, dose, 250, lastPostedAt = null, linkedIngestionId = null), old)
    }

    @Test
    fun onlyALinkedRoutineStopsWhenItsDoseIsGone() {
        val linked = ActiveRoutine(RoutineKind.REGULAR, dose, 250, null, linkedIngestionId = 42)
        assertTrue(shouldStopForDeletedDose(linked, linkedDoseExists = false))
        assertFalse(shouldStopForDeletedDose(linked, linkedDoseExists = true))
        val planned = ActiveRoutine(RoutineKind.REGULAR, dose, 250, null, linkedIngestionId = null)
        assertFalse(shouldStopForDeletedDose(planned, linkedDoseExists = false))
        assertFalse(shouldStopForDeletedDose(null, linkedDoseExists = false))
    }

    @Test
    fun theOfferedDoseIsTheLatestRecentOwnMdma() {
        val now = dose.plusSeconds(600)
        val doses = listOf(7 to dose.minusSeconds(3 * 3600), 9 to dose)
        assertEquals(9, offeredIngestionId(doses, now))
        assertNull(offeredIngestionId(emptyList(), now))
    }
}
