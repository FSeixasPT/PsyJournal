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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.isaakhanimann.journal.MainActivity

sealed interface NotificationTarget {
    data class Experience(val experienceId: Int, val openQuickLog: Boolean) : NotificationTarget
    data object AddIngestion : NotificationTarget
}

object LiveNotificationIntents {
    const val ACTION_REFRESH = "com.isaakhanimann.journal.live.REFRESH"
    const val ACTION_STOP = "com.isaakhanimann.journal.live.STOP"
    const val ACTION_DISMISSED = "com.isaakhanimann.journal.live.DISMISSED"

    private const val EXTRA_OPEN_EXPERIENCE_ID = "live_open_experience_id"
    private const val EXTRA_OPEN_QUICK_LOG = "live_open_quick_log"
    private const val EXTRA_ADD_INGESTION = "live_add_ingestion"

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun openExperience(context: Context, experienceId: Int, openQuickLog: Boolean): PendingIntent {
        val intent = mainActivityIntent(context)
            .putExtra(EXTRA_OPEN_EXPERIENCE_ID, experienceId)
            .putExtra(EXTRA_OPEN_QUICK_LOG, openQuickLog)
        return PendingIntent.getActivity(context, if (openQuickLog) 2 else 1, intent, FLAGS)
    }

    fun addIngestion(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 3, mainActivityIntent(context).putExtra(EXTRA_ADD_INGESTION, true), FLAGS)

    fun broadcast(context: Context, action: String): PendingIntent {
        val intent = Intent(context, LiveNotificationReceiver::class.java).setAction(action)
        val requestCode = when (action) {
            ACTION_REFRESH -> 10
            ACTION_STOP -> 11
            else -> 12
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, FLAGS)
    }

    private fun mainActivityIntent(context: Context) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun target(intent: Intent?): NotificationTarget? {
        if (intent == null) return null
        if (intent.getBooleanExtra(EXTRA_ADD_INGESTION, false)) return NotificationTarget.AddIngestion
        val experienceId = intent.getIntExtra(EXTRA_OPEN_EXPERIENCE_ID, -1)
        if (experienceId < 0) return null
        return NotificationTarget.Experience(experienceId, intent.getBooleanExtra(EXTRA_OPEN_QUICK_LOG, false))
    }
}

fun Intent.notificationTarget(): NotificationTarget? = LiveNotificationIntents.target(this)
