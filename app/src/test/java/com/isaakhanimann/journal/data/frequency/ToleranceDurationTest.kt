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

package com.isaakhanimann.journal.data.frequency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class ToleranceDurationTest {
    @Test
    fun singleValues() {
        assertEquals(Duration.ofDays(5), parseToleranceDuration("5 days"))
        assertEquals(Duration.ofDays(14), parseToleranceDuration("2 weeks"))
        assertEquals(Duration.ofDays(30), parseToleranceDuration("1 month"))
        assertEquals(Duration.ofHours(2), parseToleranceDuration("2 hours"))
    }

    @Test
    fun rangesUseTheUpperEnd() {
        assertEquals(Duration.ofDays(10), parseToleranceDuration("7-10 days"))
        assertEquals(Duration.ofDays(14), parseToleranceDuration("7 - 14 days"))
        assertEquals(Duration.ofDays(14), parseToleranceDuration("1 - 2 weeks"))
    }

    @Test
    fun decimals() {
        assertEquals(Duration.ofDays(75), parseToleranceDuration("2.5 months"))
    }

    @Test
    fun unreadableTextGivesNull() {
        assertNull(parseToleranceDuration("almost immediately after ingestion"))
        assertNull(parseToleranceDuration(""))
        assertNull(parseToleranceDuration(null))
    }
}
