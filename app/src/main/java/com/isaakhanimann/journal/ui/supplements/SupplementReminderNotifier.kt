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

package com.isaakhanimann.journal.ui.supplements

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isaakhanimann.journal.R
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.LiveNotificationIntents
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.isChannelUsable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// unlike the silent live notification, these remind: they make a sound and vibrate
@Singleton
class SupplementReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun canPost(): Boolean {
        val isGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        return isGranted && manager.areNotificationsEnabled() &&
                isChannelUsable(manager.getNotificationChannelCompat(CHANNEL_ID)?.importance)
    }

    @SuppressLint("MissingPermission") // canPost() checks the permission
    fun post(title: String, lines: List<String>, showDetailsOnLockScreen: Boolean) {
        if (!canPost()) return
        ensureChannel()
        // the lock screen shows this version unless details are allowed there
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_timeline)
            .setContentTitle("PsyJournal reminder")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_timeline)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(LiveNotificationIntents.openSubstance(context, "MDMA"))
            .addAction(0, "Stop routine", SupplementReminderReceiver.stopIntent(context))
            .setVisibility(if (showDetailsOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Supplement reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Hourly reminders of the supplement routine you started"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "supplement_reminders"
        private const val NOTIFICATION_ID = 4711
    }
}
