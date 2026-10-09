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

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RecentTimesTest {
    private val now = LocalDateTime.of(2026, 10, 9, 0, 10, 42)

    @Test
    fun labelsAreInOrder() {
        assertEquals(listOf("Now", "5 min ago", "15 min ago", "30 min ago", "1 hr ago"), recentTimes.map { it.label })
    }

    @Test
    fun aChoiceGoesBackFromNow() {
        val fifteen = recentTimes.first { it.minutesAgo == 15L }
        assertEquals(LocalDateTime.of(2026, 10, 8, 23, 55, 42), fifteen.timeBefore(now))
    }

    @Test
    fun aChoiceCanBeYesterday() {
        val hour = recentTimes.last()
        assertEquals(LocalDateTime.of(2026, 10, 8, 23, 10, 42), hour.timeBefore(now))
    }

    @Test
    fun theMatchingChoiceIsSelectedWithinTheSameMinute() {
        assertEquals(0L, selectedRecentTime(now.withSecond(3), now)?.minutesAgo)
        assertEquals(30L, selectedRecentTime(now.minusMinutes(30).withSecond(59), now)?.minutesAgo)
    }

    @Test
    fun otherTimesSelectNothing() {
        assertNull(selectedRecentTime(now.minusMinutes(7), now))
        assertNull(selectedRecentTime(now.minusDays(1), now))
    }
}
