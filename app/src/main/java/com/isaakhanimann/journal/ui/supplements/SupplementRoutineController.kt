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

import com.isaakhanimann.journal.data.supplements.routineExperienceId
import com.isaakhanimann.journal.data.supplements.stepsOf
import com.isaakhanimann.journal.data.supplements.routineNotice
import com.isaakhanimann.journal.data.supplements.withStepDone
import android.util.Log
import com.isaakhanimann.journal.data.supplements.shouldStopForDeletedDose
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.isaakhanimann.journal.data.supplements.initialLastPostedAt
import com.isaakhanimann.journal.data.supplements.nextCheckpoint
import android.app.AlarmManager
import android.content.Context
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.supplements.ActiveRoutine
import com.isaakhanimann.journal.data.supplements.RoutineKind
import com.isaakhanimann.journal.data.supplements.isFinished
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

    // a failure in this scope must never crash the app
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w("SupplementRoutine", "Observer failed", error)
        }
    )

    // while the app runs: deleting the dose a routine was started from (the ingestion, its experience,
    // or the whole journal) stops the routine and removes its reminder
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe() {
        scope.launch {
            userPreferences.activeRoutineFlow
                .flatMapLatest { routine ->
                    val id = routine?.linkedIngestionId ?: return@flatMapLatest flowOf(false)
                    experienceRepo.getIngestionFlow(id).map { shouldStopForDeletedDose(routine, linkedDoseExists = it != null) }
                }
                .catch { Log.w("SupplementRoutine", "Observer failed", it) }
                .collect { isGone -> if (isGone) stop() }
        }
    }

    fun canPost(): Boolean = notifier.canPost()

    // steps more than half an hour past when starting are never shown; one from the last half hour,
    // such as the dose step when starting right after the dose, is shown straight away
    suspend fun start(kind: RoutineKind, doseTime: Instant, mlPerHour: Int, linkedIngestionId: Int? = null) = mutex.withLock {
        val now = Instant.now()
        notifier.cancel()
        userPreferences.saveActiveRoutine(
            initialLastPostedAt(kind, doseTime, now).let { skipped ->
                ActiveRoutine(kind, doseTime, mlPerHour, skipped, linkedIngestionId, skippedBefore = skipped)
            }
        )
        syncLocked()
    }

    suspend fun change(doseTime: Instant, mlPerHour: Int) = mutex.withLock {
        val current = userPreferences.activeRoutineFlow.first() ?: return@withLock
        val now = Instant.now()
        userPreferences.saveActiveRoutine(
            initialLastPostedAt(current.kind, doseTime, now).let { skipped ->
                // a new dose time moves every step, so earlier ticks no longer match
                val doneOffsets = if (doseTime == current.doseTime) current.doneOffsets else emptySet()
                current.copy(doseTime = doseTime, mlPerHour = mlPerHour, lastPostedAt = skipped, skippedBefore = skipped, doneOffsets = doneOffsets)
            }
        )
        syncLocked()
    }

    suspend fun stop() = mutex.withLock { stopLocked() }

    // from the app's checkboxes, or the Done button of a reminder (which names the routine's dose time,
    // so a button left over from another routine does nothing)
    suspend fun markDone(offsetMinutes: Long, isDone: Boolean, forDoseTime: Instant? = null) = mutex.withLock {
        val routine = userPreferences.activeRoutineFlow.first()
        if (routine == null) {
            // Done on the last reminder, after the routine ended
            if (forDoseTime != null) notifier.cancel()
            return@withLock
        }
        if (forDoseTime != null && forDoseTime != routine.doseTime) return@withLock
        val step = stepsOf(routine.kind).firstOrNull { it.offset.toMinutes() == offsetMinutes } ?: return@withLock
        val updated = routine.withStepDone(step, isDone)
        userPreferences.saveActiveRoutine(updated)
        // ticked in the app or from the reminder: the notification moves on to the next step, quietly
        val now = Instant.now()
        if (!isFinished(updated.kind, updated.doseTime, now)) showLocked(updated, now, alert = false, ongoing = true)
    }

    suspend fun onAlarm() = mutex.withLock { syncLocked() }

    // when the app starts: a battery saver or force-stop may have removed the alarm
    suspend fun resync() = mutex.withLock { syncLocked() }

    // the alarm, a restart, a late Doze wake-up or a checkpoint: a newly due step alerts once, otherwise the
    // notification is refreshed quietly (the water so far changes); then the next alarm is armed
    private suspend fun syncLocked() {
        val routine = userPreferences.activeRoutineFlow.first() ?: return
        // also when the dose was deleted while the app wasn't running
        val linkedId = routine.linkedIngestionId
        if (linkedId != null && shouldStopForDeletedDose(routine, experienceRepo.getIngestionFlow(linkedId).first() != null)) {
            stopLocked()
            return
        }
        val now = Instant.now()
        val newlyDue = stepToPost(routine.kind, routine.doseTime, now, routine.lastPostedAt)
        val current = newlyDue?.let { routine.copy(lastPostedAt = stepTime(routine.doseTime, it)) } ?: routine
        if (newlyDue != null) userPreferences.saveActiveRoutine(current)
        val isOver = isFinished(routine.kind, routine.doseTime, now)
        // once over, only the last step's reminder is left, and it can be swiped away
        if (!isOver || newlyDue != null) showLocked(current, now, alert = newlyDue != null, ongoing = !isOver)
        if (isOver) {
            // also ends a routine that was started or changed with every step already past
            userPreferences.saveActiveRoutine(null)
            cancelAlarm()
        } else {
            // a second later, so the step just shown isn't armed again
            armNext(now.plusSeconds(1))
        }
    }

    private suspend fun showLocked(routine: ActiveRoutine, now: Instant, alert: Boolean, ongoing: Boolean) {
        // a redose pushes the 5-HTP date later
        val lastMdma = experienceRepo.getPastIngestions(setOf("MDMA"), routine.doseTime.minus(Duration.ofHours(2)))
            .maxOfOrNull { it.time }?.takeIf { it.isAfter(routine.doseTime) } ?: routine.doseTime
        val notice = routineNotice(routine, now, lastMdma, ::formatTime, ::formatDayTime)
        if (notice == null) {
            notifier.cancel()
            return
        }
        val ownDoses = experienceRepo.getOwnDoses(setOf("MDMA"), routine.doseTime.minus(Duration.ofHours(5)))
        notifier.post(
            doseTime = routine.doseTime,
            offsetMinutes = notice.step.offset.toMinutes(),
            experienceId = routineExperienceId(routine, ownDoses),
            title = notice.title,
            lines = notice.lines,
            showDetailsOnLockScreen = userPreferences.isLiveShownOnLockScreenFlow.first(),
            isDue = notice.isDue,
            alert = alert && notice.isDue,
            ongoing = ongoing
        )
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
