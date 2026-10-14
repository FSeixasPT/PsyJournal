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

import com.isaakhanimann.journal.data.supplements.initialLastPostedAt
import com.isaakhanimann.journal.data.supplements.nextCheckpoint
import android.app.AlarmManager
import android.content.Context
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.supplements.ActiveRoutine
import com.isaakhanimann.journal.data.supplements.RoutineKind
import com.isaakhanimann.journal.data.supplements.isFinished
import com.isaakhanimann.journal.data.supplements.reminderLines
import com.isaakhanimann.journal.data.supplements.reminderTitle
import com.isaakhanimann.journal.data.supplements.remainingSteps
import com.isaakhanimann.journal.data.supplements.stepTime
import com.isaakhanimann.journal.data.supplements.stepToPost
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.utils.getStringOfPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

// runs the one active supplement routine: one alarm is always armed for its next step
@Singleton
class SupplementRoutineController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val experienceRepo: ExperienceRepository,
    private val notifier: SupplementReminderNotifier,
) {
    private val mutex = Mutex()
    val activeRoutineFlow: Flow<ActiveRoutine?> = userPreferences.activeRoutineFlow

    fun canPost(): Boolean = notifier.canPost()

    // steps more than half an hour past when starting are never shown; one from the last half hour,
    // such as the dose step when starting right after the dose, is shown straight away
    suspend fun start(kind: RoutineKind, doseTime: Instant, mlPerHour: Int) = mutex.withLock {
        val now = Instant.now()
        notifier.cancel()
        userPreferences.saveActiveRoutine(ActiveRoutine(kind, doseTime, mlPerHour, initialLastPostedAt(kind, doseTime, now)))
        syncLocked()
    }

    suspend fun change(doseTime: Instant, mlPerHour: Int) = mutex.withLock {
        val current = userPreferences.activeRoutineFlow.first() ?: return@withLock
        val now = Instant.now()
        userPreferences.saveActiveRoutine(
            current.copy(doseTime = doseTime, mlPerHour = mlPerHour, lastPostedAt = initialLastPostedAt(current.kind, doseTime, now))
        )
        syncLocked()
    }

    suspend fun stop() = mutex.withLock { stopLocked() }

    suspend fun onAlarm() = mutex.withLock { syncLocked() }

    // when the app starts: a battery saver or force-stop may have removed the alarm
    suspend fun resync() = mutex.withLock { syncLocked() }

    // the alarm, a restart, a late Doze wake-up or a checkpoint: show the most recent due step once, then arm the next
    private suspend fun syncLocked() {
        val routine = userPreferences.activeRoutineFlow.first() ?: return
        val now = Instant.now()
        stepToPost(routine.kind, routine.doseTime, now, routine.lastPostedAt)?.let { step ->
            // a redose pushes the 5-HTP date later
            val lastMdma = experienceRepo.getPastIngestions(setOf("MDMA"), routine.doseTime.minus(Duration.ofHours(2)))
                .maxOfOrNull { it.time }?.takeIf { it.isAfter(routine.doseTime) } ?: routine.doseTime
            notifier.post(
                title = reminderTitle(step, routine.doseTime, ::formatTime),
                lines = reminderLines(routine.kind, step, routine.doseTime, routine.mlPerHour, lastMdma, ::formatTime, ::formatDayTime),
                showDetailsOnLockScreen = userPreferences.isLiveShownOnLockScreenFlow.first()
            )
            userPreferences.saveActiveRoutine(routine.copy(lastPostedAt = stepTime(routine.doseTime, step)))
        }
        if (isFinished(routine.kind, routine.doseTime, now)) {
            // also ends a routine that was started or changed with every step already past
            userPreferences.saveActiveRoutine(null)
            cancelAlarm()
        } else {
            // a second later, so the step just shown isn't armed again
            armNext(now.plusSeconds(1))
        }
    }

    private suspend fun stopLocked() {
        userPreferences.saveActiveRoutine(null)
        cancelAlarm()
        notifier.cancel()
    }

    private suspend fun armNext(now: Instant) {
        val routine = userPreferences.activeRoutineFlow.first() ?: return
        val next = remainingSteps(routine.kind, routine.doseTime, now).firstOrNull() ?: return
        // inexact, so no exact-alarm permission is needed; checkpoints keep Android's delay to a few minutes
        alarmManager().setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextCheckpoint(now, stepTime(routine.doseTime, next)).toEpochMilli(),
            SupplementReminderReceiver.alarmIntent(context)
        )
    }

    private fun cancelAlarm() = alarmManager().cancel(SupplementReminderReceiver.alarmIntent(context))

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)

    private fun formatTime(instant: Instant): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(instant.atZone(ZoneId.systemDefault()))

    private fun formatDayTime(instant: Instant): String = instant.getStringOfPattern("EEE ") + formatTime(instant)
}
