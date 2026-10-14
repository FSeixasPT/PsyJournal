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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SupplementEntryPoint {
    fun supplementRoutineController(): SupplementRoutineController
}

// an entry point instead of field injection, like the live notification's receiver
private fun BroadcastReceiver.runController(context: Context, block: suspend SupplementRoutineController.() -> Unit) {
    val controller = EntryPointAccessors.fromApplication(context.applicationContext, SupplementEntryPoint::class.java)
        .supplementRoutineController()
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            controller.block()
        } finally {
            pendingResult.finish()
        }
    }
}

class SupplementReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM -> runController(context) { onAlarm() }
            ACTION_STOP -> runController(context) { stop() }
        }
    }

    companion object {
        private const val ACTION_ALARM = "com.isaakhanimann.journal.supplements.ALARM"
        private const val ACTION_STOP = "com.isaakhanimann.journal.supplements.STOP"
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 20, Intent(context, SupplementReminderReceiver::class.java).setAction(ACTION_ALARM), FLAGS
        )

        fun stopIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 21, Intent(context, SupplementReminderReceiver::class.java).setAction(ACTION_STOP), FLAGS
        )
    }
}

// alarms don't survive a restart, so the routine's next step is armed again
class SupplementBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) runController(context) { onAlarm() }
    }
}
