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

import kotlin.math.roundToInt

enum class WidgetKind { FULL, CHART, STATUS }

private const val defaultWidthDp = 250
private const val defaultHeightDp = 110
private const val paddingDp = 24
private const val minimumWidthPx = 48
private const val minimumHeightPx = 24

// the chart bitmap size for a widget of this size in dp; drawing a 0 px bitmap would throw
fun chartSizePx(kind: WidgetKind, widthDp: Int?, heightDp: Int?, density: Float): Pair<Int, Int> {
    val width = widthDp ?: defaultWidthDp
    val height = heightDp ?: defaultHeightDp
    // the title row, and for the full widget the substance lines below the chart
    val textDp = if (kind == WidgetKind.FULL) 92 else 28
    val widthPx = ((width - paddingDp) * density).roundToInt().coerceAtLeast(minimumWidthPx)
    val heightPx = ((height - paddingDp - textDp) * density).roundToInt().coerceAtLeast(minimumHeightPx)
    return Pair(widthPx, heightPx)
}
