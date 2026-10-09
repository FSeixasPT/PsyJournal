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

package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import java.time.Duration
import java.time.Instant

// the timeline as the experience screen draws it, with a line at the current time; null if drawing fails
fun drawLiveChart(
    model: AllTimelinesModel,
    now: Instant,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    isDarkTheme: Boolean,
): Bitmap? = try {
    val image = ImageBitmap(widthPx, heightPx)
    val size = Size(widthPx.toFloat(), heightPx.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), size) {
        val pixelsPerSec = size.width / model.widthInSeconds
        // keeps the thick line inside the image at its highest point
        val verticalInset = 4 * density.density
        inset(left = 0f, top = verticalInset, right = 0f, bottom = verticalInset) {
            model.groupDrawables.forEach { group ->
                group.drawTimeLine(
                    drawScope = this,
                    canvasHeight = this.size.height,
                    pixelsPerSec = pixelsPerSec,
                    color = group.color.getComposeColor(isDarkTheme),
                    density = density
                )
            }
        }
        val nowX = Duration.between(model.startTime, now).seconds * pixelsPerSec
        drawLine(
            color = if (isDarkTheme) Color.White else Color.Black,
            start = Offset(nowX, 0f),
            end = Offset(nowX, size.height),
            strokeWidth = 2 * density.density
        )
    }
    image.asAndroidBitmap()
} catch (e: Exception) {
    null
}
