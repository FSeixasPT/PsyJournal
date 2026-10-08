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

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class PlannedTimeTextTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)

    @Test
    fun relativeToNowSaysInHowLong() {
        assertEquals("in 45 min", plannedTimeText(at(45), now = at(0), experienceStart = at(0), TimeDisplayOption.RELATIVE_TO_NOW))
        assertEquals("30 min ago", plannedTimeText(at(0), now = at(30), experienceStart = at(0), TimeDisplayOption.RELATIVE_TO_NOW))
    }

    @Test
    fun relativeToStartSaysHowLongIntoTheExperience() {
        assertEquals("2.5 hr in", plannedTimeText(at(150), now = at(0), experienceStart = at(0), TimeDisplayOption.RELATIVE_TO_START))
    }

    @Test
    fun regularTimeAndTimeBetweenShowTheClockTime() {
        assertTrue(plannedTimeText(at(45), now = at(0), experienceStart = at(0), TimeDisplayOption.REGULAR).startsWith("at "))
        assertTrue(plannedTimeText(at(45), now = at(0), experienceStart = at(0), TimeDisplayOption.TIME_BETWEEN).startsWith("at "))
    }

    @Test
    fun rangeKeepsTheWordsOutside() {
        assertEquals("in 45 min–2.8 hr", plannedTimeRangeText(at(45), at(165), now = at(0), experienceStart = at(0), TimeDisplayOption.RELATIVE_TO_NOW))
        assertEquals("45 min–2.8 hr in", plannedTimeRangeText(at(45), at(165), now = at(0), experienceStart = at(0), TimeDisplayOption.RELATIVE_TO_START))
        assertTrue(plannedTimeRangeText(at(45), at(165), now = at(0), experienceStart = at(0), TimeDisplayOption.REGULAR).contains("–"))
    }

    @Test
    fun autoIsAlwaysRelativeToNowForTheChip() {
        assertEquals(TimeDisplayOption.RELATIVE_TO_NOW, chipTimeDisplayOption(SavedTimeDisplayOption.AUTO, resolved = TimeDisplayOption.REGULAR))
        assertEquals(TimeDisplayOption.RELATIVE_TO_START, chipTimeDisplayOption(SavedTimeDisplayOption.RELATIVE_TO_START, resolved = TimeDisplayOption.RELATIVE_TO_START))
        assertEquals(TimeDisplayOption.REGULAR, chipTimeDisplayOption(SavedTimeDisplayOption.REGULAR, resolved = TimeDisplayOption.REGULAR))
    }
}
