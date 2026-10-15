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

import java.time.Instant
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

// one notification for the whole routine: it quietly shows the next step, and makes a sound and vibrates
// when a step is due
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
    // opens the routine's experience, or the MDMA page before the dose is logged; Done only for a due step;
    // ongoing while the routine runs, so only the last reminder can be swiped away on older Android
    fun post(
        doseTime: Instant,
        offsetMinutes: Long,
        experienceId: Int?,
        title: String,
        lines: List<String>,
        showDetailsOnLockScreen: Boolean,
        isDue: Boolean = true,
        alert: Boolean = true,
        ongoing: Boolean = false,
    ) {
        if (!canPost()) return
        ensureChannel()
        // the lock screen shows this version unless details are allowed there
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_timeline)
            .setContentTitle("PsyJournal reminder")
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_timeline)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setSilent(!alert)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(
                if (experienceId != null) {
                    // its own request code, so it never replaces the live notification's tap target
                    LiveNotificationIntents.openFromWidget(context, OPEN_EXPERIENCE_REQUEST_CODE, experienceId)
                } else {
                    LiveNotificationIntents.openSubstance(context, "MDMA")
                }
            )
            .setVisibility(if (showDetailsOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
        if (isDue) builder.addAction(0, "Done", SupplementReminderReceiver.doneIntent(context, doseTime, offsetMinutes))
        builder.addAction(0, "Stop routine", SupplementReminderReceiver.stopIntent(context))
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
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
        // not the live notification's 4711, so both can show at once
        const val NOTIFICATION_ID = 4712
        private const val OPEN_EXPERIENCE_REQUEST_CODE = 30
    }
}
