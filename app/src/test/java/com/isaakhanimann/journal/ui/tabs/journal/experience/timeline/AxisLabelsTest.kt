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

import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.AxisDrawable
import com.isaakhanimann.journal.ui.utils.getInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class AxisLabelsTest {
    private val defaultLocale = Locale.getDefault()

    @Before
    fun useUsLocale() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    // 10:20 to 15:20, drawn 600 pixels wide
    private val axis = AxisDrawable(
        startTime = getInstant(2026, 10, day = 11, hourOfDay = 10, minute = 20)!!,
        widthInSeconds = 5 * 3600f
    )
    private val pixelsPerSec = 600f / (5 * 3600f)

    @Test
    fun hoursAreTwoDigitsInTheTwentyFourHourFormat() {
        val labels = axis.getFullHours(pixelsPerSec, widthInPixels = 600f, is24HourFormat = true).map { it.label }
        assertEquals(listOf("11", "12", "13", "14", "15"), labels)
    }

    @Test
    fun hoursHaveAmOrPmInTheTwelveHourFormat() {
        val labels = axis.getFullHours(pixelsPerSec, widthInPixels = 600f, is24HourFormat = false).map { it.label }
        assertEquals(listOf("11 AM", "12 PM", "1 PM", "2 PM", "3 PM"), labels)
    }

    @Test
    fun widerLabelsSkipHoursSoTheyDontOverlap() {
        // 120 pixels per hour, but each label needs 200
        val labels = axis.getFullHours(pixelsPerSec, widthInPixels = 600f, is24HourFormat = false, minWidthPerLabel = 200f)
            .map { it.label }
        assertEquals(listOf("11 AM", "1 PM", "3 PM"), labels)
    }
}
