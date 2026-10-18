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
package com.isaakhanimann.journal.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class PickerDateTextTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun thisYearLeavesTheYearOut() {
        assertEquals("Sun 18 Oct", pickerDateText(LocalDate.of(2026, 10, 18), today, Locale.ENGLISH))
    }

    // an ingestion edited long after it was logged still says which year it was
    @Test
    fun anotherYearKeepsTheYear() {
        assertEquals("Mon 3 Mar 2025", pickerDateText(LocalDate.of(2025, 3, 3), today, Locale.ENGLISH))
    }
}
