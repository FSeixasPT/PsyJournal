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

import android.content.Context
import android.content.res.Configuration
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.isaakhanimann.journal.R
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.LiveNotificationIntents
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.SubstanceLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.drawLiveChart
import java.time.Instant

// request codes 20+ so they never collide with the notification's 1-12
private const val REQUEST_OPEN = 20
private const val REQUEST_ADD = 21

fun renderTimelineWidget(
    context: Context,
    kind: WidgetKind,
    content: WidgetContent,
    widthDp: Int?,
    heightDp: Int?,
    now: Instant,
): RemoteViews {
    val layout = when (kind) {
        WidgetKind.FULL -> R.layout.widget_full
        WidgetKind.CHART -> R.layout.widget_chart
        WidgetKind.STATUS -> R.layout.widget_status
    }
    val isDarkTheme = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val views = RemoteViews(context.packageName, layout)
    val experienceId = (content as? WidgetContent.Active)?.experienceId
    views.setOnClickPendingIntent(R.id.widget_root, LiveNotificationIntents.openFromWidget(context, REQUEST_OPEN, experienceId))
    if (kind != WidgetKind.CHART) {
        views.setOnClickPendingIntent(R.id.widget_add, LiveNotificationIntents.addIngestionFromWidget(context, REQUEST_ADD))
    }
    when (content) {
        WidgetContent.NothingActive -> {
            val text = context.getString(R.string.widget_nothing_active)
            if (kind == WidgetKind.STATUS) {
                views.setTextViewText(R.id.widget_lines, text)
            } else {
                views.setTextViewText(R.id.widget_title, text)
                views.setViewVisibility(R.id.widget_chart, View.GONE)
                if (kind == WidgetKind.FULL) views.setTextViewText(R.id.widget_lines, "")
            }
        }

        is WidgetContent.Active -> {
            if (kind != WidgetKind.STATUS) {
                views.setTextViewText(R.id.widget_title, content.title)
                val density = context.resources.displayMetrics.density
                val (widthPx, heightPx) = chartSizePx(kind, widthDp, heightDp, content.lines.size, density)
                val chart = drawLiveChart(content.model, now, widthPx, heightPx, Density(context), isDarkTheme)
                if (chart != null) {
                    views.setViewVisibility(R.id.widget_chart, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_chart, chart)
                } else {
                    views.setViewVisibility(R.id.widget_chart, View.GONE)
                }
            }
            if (kind != WidgetKind.CHART) {
                views.setTextViewText(R.id.widget_lines, linesText(content.lines, isDarkTheme))
            }
        }
    }
    return views
}

// a colored dot before each substance, like the notification's expanded view
private fun linesText(lines: List<SubstanceLine>, isDarkTheme: Boolean): CharSequence {
    val text = SpannableStringBuilder()
    lines.forEach { line ->
        if (text.isNotEmpty()) text.append("\n")
        val dotStart = text.length
        text.append("● ")
        text.setSpan(
            ForegroundColorSpan(line.color.getComposeColor(isDarkTheme).toArgb()),
            dotStart,
            dotStart + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // the same arrows as the collapsed notification
        text.append(line.compactText)
    }
    return text
}
