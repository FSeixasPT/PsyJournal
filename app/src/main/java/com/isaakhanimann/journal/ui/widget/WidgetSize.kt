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

enum class WidgetKind { FULL, STATUS }

private const val defaultWidthDp = 250
private const val defaultHeightDp = 110
private const val paddingDp = 24
private const val minimumWidthPx = 48
private const val minimumHeightPx = 24

private const val titleDp = 28
private const val linesMarginDp = 4
private const val lineDp = 18
// the full widget's lines are cut after this many, see widget_full.xml
private const val maxLines = 4

// the chart bitmap size for a full widget of this size in dp; drawing a 0 px bitmap would throw
fun chartSizePx(widthDp: Int?, heightDp: Int?, lineCount: Int, density: Float, fontScale: Float = 1f): Pair<Int, Int> {
    val width = widthDp ?: defaultWidthDp
    val height = heightDp ?: defaultHeightDp
    // the title row and the substance lines below the chart; their text grows with the system font size
    val textDp = (titleDp + lineDp * lineCount.coerceIn(1, maxLines)) * fontScale + linesMarginDp
    val widthPx = ((width - paddingDp) * density).roundToInt().coerceAtLeast(minimumWidthPx)
    val heightPx = ((height - paddingDp - textDp) * density).roundToInt().coerceAtLeast(minimumHeightPx)
    return Pair(widthPx, heightPx)
}

// the widget's size as the launcher shows it now; null for a size Android didn't report
fun widgetSizeDp(isPortrait: Boolean, minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int): Pair<Int?, Int?> {
    val width = if (isPortrait) minWidth else maxWidth
    val height = if (isPortrait) maxHeight else minHeight
    return Pair(width.takeIf { it > 0 }, height.takeIf { it > 0 })
}

// phone launchers stay in portrait, so a refresh while a landscape app is open must not size for landscape
fun isPortraitLauncher(isPortraitNow: Boolean, smallestScreenWidthDp: Int): Boolean =
    isPortraitNow || smallestScreenWidthDp < 600
