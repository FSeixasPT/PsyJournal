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

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isaakhanimann.journal.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveNotificationNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val isDarkTheme: Boolean
        get() = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun canPost(): Boolean {
        val isGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        return isGranted && manager.areNotificationsEnabled() &&
                isChannelUsable(manager.getNotificationChannelCompat(CHANNEL_ID)?.importance)
    }

    @SuppressLint("MissingPermission") // canPost() checks the permission
    fun post(experienceId: Int, content: LiveNotificationContent, chart: Bitmap?, showDetailsOnLockScreen: Boolean) {
        if (!canPost()) return
        ensureChannel()
        val collapsed = RemoteViews(context.packageName, R.layout.live_notification_collapsed).apply {
            // the collapsed view only has room for one line, so its substances share it in short form
            setTextViewText(R.id.live_lines, linesText(content.lines, { it.shortText }, plannedLine = null, separator = " · "))
        }
        val expanded = RemoteViews(context.packageName, R.layout.live_notification_expanded).apply {
            if (chart != null) setImageViewBitmap(R.id.live_chart, chart) else setViewVisibility(R.id.live_chart, View.GONE)
            setTextViewText(R.id.live_lines, linesText(content.lines, { it.text }, content.plannedLine, separator = "\n"))
        }
        // the lock screen shows this version unless details are allowed there
        val publicVersion = baseBuilder()
            .setContentTitle("Experience in progress")
            .build()
        val builder = baseBuilder()
            .setContentTitle(content.title)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(LiveNotificationIntents.openExperience(context, experienceId, openQuickLog = false))
            .setDeleteIntent(LiveNotificationIntents.broadcast(context, LiveNotificationIntents.ACTION_DISMISSED))
            .addAction(0, "Add ingestion", LiveNotificationIntents.addIngestion(context))
            .setVisibility(if (showDetailsOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
        if (content.plannedLine != null) {
            builder.addAction(0, "Log planned", LiveNotificationIntents.openExperience(context, experienceId, openQuickLog = true))
        }
        builder.addAction(0, "Stop", LiveNotificationIntents.broadcast(context, LiveNotificationIntents.ACTION_STOP))
        // Android keeps this countdown running between refreshes
        content.endsAt?.let {
            builder.setWhen(it.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun baseBuilder() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_live_timeline)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    private fun linesText(
        lines: List<SubstanceLine>,
        textOf: (SubstanceLine) -> String,
        plannedLine: String?,
        separator: String,
    ): CharSequence {
        val text = SpannableStringBuilder()
        // first, so a long list can't push it out of the expanded view's limited height
        if (plannedLine != null) text.append(plannedLine)
        lines.forEach { line ->
            if (text.isNotEmpty()) text.append(separator)
            val dotStart = text.length
            text.append("● ")
            text.setSpan(
                ForegroundColorSpan(line.color.getComposeColor(isDarkTheme).toArgb()),
                dotStart,
                dotStart + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.append(textOf(line))
        }
        return text
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Live timeline", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Follows the estimated timeline of the current experience"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "live_timeline"
        const val NOTIFICATION_ID = 4711
    }
}
