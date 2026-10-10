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
        // 300 x 180 dp at density 2; the full widget keeps 92 dp for the title and lines and 24 dp of padding
        assertEquals(Pair(552, 128), chartSizePx(WidgetKind.FULL, 300, 180, 2f))
        // the chart widget only keeps 28 dp for the title and 24 dp of padding
        assertEquals(Pair(552, 256), chartSizePx(WidgetKind.CHART, 300, 180, 2f))
    }

    @Test
    fun anUnknownSizeUsesTheDefaultSize() {
        // the default is 4 x 2 cells, about 250 x 110 dp
        assertEquals(chartSizePx(WidgetKind.FULL, 250, 110, 2f), chartSizePx(WidgetKind.FULL, null, null, 2f))
    }

    @Test
    fun theChartIsNeverEmpty() {
        val (width, height) = chartSizePx(WidgetKind.FULL, 40, 40, 1f)
        assertTrue(width >= 48 && height >= 24)
    }
}
