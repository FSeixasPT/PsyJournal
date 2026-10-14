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
import java.time.Instant

class ActiveRoutineTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")

    @Test
    fun roundTrips() {
        val routine = ActiveRoutine(RoutineKind.MINIMAL, dose, 500, lastPostedAt = dose.minusSeconds(60))
        assertEquals(routine, ActiveRoutine.fromPreferenceString(routine.toPreferenceString()))
        val fresh = ActiveRoutine(RoutineKind.REGULAR, dose, 250, lastPostedAt = null)
        assertEquals(fresh, ActiveRoutine.fromPreferenceString(fresh.toPreferenceString()))
    }

    @Test
    fun brokenValuesGiveNull() {
        assertNull(ActiveRoutine.fromPreferenceString(null))
        assertNull(ActiveRoutine.fromPreferenceString("garbage"))
        assertNull(ActiveRoutine.fromPreferenceString("UNKNOWN|2026-10-16T22:00:00Z|250|"))
    }
}
