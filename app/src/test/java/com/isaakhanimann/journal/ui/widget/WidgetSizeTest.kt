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

package com.isaakhanimann.journal.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun theChartFillsTheWidthAndTheSpaceLeftByTheText() {
        // 300 x 180 dp at density 2, 24 dp of padding; the full widget keeps 32 dp for the title and 18 dp per line
        assertEquals(Pair(552, 212), chartSizePx(300, 180, lineCount = 1, density = 2f))
    }

    @Test
    fun moreLinesLeaveLessRoomForTheChart() {
        assertEquals(Pair(552, 140), chartSizePx(300, 180, lineCount = 3, density = 2f))
        // the lines are cut after 4, so they never take more room than that
        assertEquals(chartSizePx(300, 180, 4, 2f), chartSizePx(300, 180, 9, 2f))
    }

    @Test
    fun anUnknownSizeUsesTheDefaultSize() {
        // the default is 4 x 2 cells, about 250 x 110 dp
        assertEquals(chartSizePx(250, 110, 1, 2f), chartSizePx(null, null, 1, 2f))
    }

    @Test
    fun theChartIsNeverEmpty() {
        val (width, height) = chartSizePx(40, 40, 1, 1f)
        assertTrue(width >= 48 && height >= 24)
    }

    @Test
    fun portraitUsesTheNarrowWidthAndTheTallHeight() {
        // Android reports the portrait size as the min width and the max height, the landscape one the other way round
        assertEquals(Pair(300, 210), widgetSizeDp(isPortrait = true, minWidth = 300, maxWidth = 420, minHeight = 134, maxHeight = 210))
        assertEquals(Pair(420, 134), widgetSizeDp(isPortrait = false, minWidth = 300, maxWidth = 420, minHeight = 134, maxHeight = 210))
    }

    @Test
    fun missingOptionsGiveNoSize() {
        assertEquals(Pair(null, null), widgetSizeDp(isPortrait = true, minWidth = 0, maxWidth = 0, minHeight = 0, maxHeight = 0))
    }

    @Test
    fun aPhoneLauncherIsPortraitEvenWhenALandscapeAppIsOpen() {
        // a refresh can run while a video plays in landscape; the home screen of a phone is still portrait
        assertEquals(true, isPortraitLauncher(isPortraitNow = false, smallestScreenWidthDp = 411))
        assertEquals(true, isPortraitLauncher(isPortraitNow = true, smallestScreenWidthDp = 411))
        // tablets rotate their home screen, so the current orientation counts
        assertEquals(false, isPortraitLauncher(isPortraitNow = false, smallestScreenWidthDp = 800))
    }
}
