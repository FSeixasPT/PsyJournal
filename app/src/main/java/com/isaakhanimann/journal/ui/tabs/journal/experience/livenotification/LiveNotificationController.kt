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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.Density
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.experience.myEffectLines
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.belongsTo
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveNotificationController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    private val userPreferences: UserPreferences,
    private val notifier: LiveNotificationNotifier,
) {
    // a failure in this scope must never crash the app
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w("LiveNotification", "Refresh failed", error)
        }
    )
    private val mutex = Mutex()

    val liveExperienceFlow: Flow<LiveExperience?> = userPreferences.liveExperienceFlow

    // keeps the notification in step with the journal while the app process runs
    fun observe() {
        scope.launch {
            userPreferences.liveExperienceFlow.collectLatest { live ->
                if (live == null) return@collectLatest
                // anything the notification shows: the ingestions, the pinned plan and the display settings
                combine(
                    experienceRepo.getIngestionsWithCompanionsFlow(live.experienceId),
                    userPreferences.pinnedRedoseFlow,
                    userPreferences.savedTimeDisplayOptionFlow,
                    userPreferences.areRoutesMergedFlow,
                    userPreferences.areSubstanceHeightsIndependentFlow,
                ) { _, _, _, _, _ -> }.collect { refresh() }
            }
        }
    }

    suspend fun start(experienceId: Int) {
        runSafely(onFailure = ::logFailure) {
            val experience = experienceRepo.getExperience(experienceId) ?: return@runSafely
            userPreferences.saveLiveExperience(LiveExperience(experience.id, experience.creationDate))
        }
        refresh()
    }

    suspend fun stop() = runSafely(onFailure = ::logFailure) { mutex.withLock { stopLocked() } }

    // starts the notification after logging an ingestion of the user taken around now, if the user chose so
    fun onIngestionLogged(experienceId: Int, ingestionTime: Instant, consumerName: String?) {
        scope.launch {
            if (!shouldAutoStart(consumerName, ingestionTime, Instant.now())) return@launch
            if (!userPreferences.isLiveAutoStartFlow.first()) return@launch
            if (!notifier.canPost()) return@launch
            start(experienceId)
        }
    }

    // a failed refresh is tried again later, so the notification never stays frozen
    suspend fun refresh() = runSafely(
        onFailure = { error ->
            logFailure(error)
            scheduleRefresh(Instant.now() + retryAfterFailure)
        }
    ) {
        mutex.withLock { refreshLocked() }
    }

    private suspend fun refreshLocked() {
        val live = userPreferences.liveExperienceFlow.first()
        val experience = live?.let { experienceRepo.getExperience(it.experienceId) }
        val now = Instant.now()
        when (val decision = decideRefresh(live, experience, notifier.canPost()) { build(it, now) }) {
            RefreshDecision.Stop -> stopLocked()
            is RefreshDecision.Post -> {
                val chart = drawLiveChart(
                    model = decision.model,
                    now = now,
                    widthPx = 600,
                    heightPx = 160,
                    // the notification shows a small chart, so its lines, dots and now line are drawn thinner
                    density = Density(context.resources.displayMetrics.density * notificationLineScale),
                    isDarkTheme = notifier.isDarkTheme
                )
                notifier.post(
                    experienceId = decision.experienceId,
                    content = decision.content,
                    chart = chart,
                    showDetailsOnLockScreen = userPreferences.isLiveShownOnLockScreenFlow.first()
                )
                scheduleRefresh(decision.content.nextRefreshAt)
            }
        }
    }

    private suspend fun stopLocked() {
        if (userPreferences.liveExperienceFlow.first() != null) userPreferences.saveLiveExperience(null)
        notifier.cancel()
        alarmManager().cancel(LiveNotificationIntents.broadcast(context, LiveNotificationIntents.ACTION_REFRESH))
    }

    private suspend fun build(experience: Experience, now: Instant): RefreshDecision.Post? {
        val lines = myEffectLines(experienceRepo.getIngestionsWithCompanions(experience.id), substanceRepo)
        if (lines.isEmpty()) return null
        val model = AllTimelinesModel(
            dataForLines = lines,
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = userPreferences.areSubstanceHeightsIndependentFlow.first(),
            areRoutesMerged = userPreferences.areRoutesMergedFlow.first()
        )
        val planned = userPreferences.pinnedRedoseFlow.first()
            ?.takeIf { it.belongsTo(experience.id, experience.creationDate) }
            ?.let { model.resolvePinnedPlan(it, now) }
        val content = buildLiveNotificationContent(
            title = experience.title,
            model = model,
            lines = lines,
            plannedIngestion = planned,
            savedTimeDisplayOption = userPreferences.savedTimeDisplayOptionFlow.first(),
            now = now
        )
        return RefreshDecision.Post(experience.id, content, model)
    }

    // inexact, so no exact-alarm permission is needed; the countdown stays live if Android delays it
    private fun scheduleRefresh(at: Instant) {
        alarmManager().setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toEpochMilli(),
            LiveNotificationIntents.broadcast(context, LiveNotificationIntents.ACTION_REFRESH)
        )
    }

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)

    private fun logFailure(error: Throwable) {
        Log.w("LiveNotification", "Live notification update failed", error)
    }
}

private val retryAfterFailure: Duration = Duration.ofMinutes(5)

// runs a step of the live notification without letting a failure crash the app; cancellation still cancels
suspend fun runSafely(onFailure: (Throwable) -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
    }
}

// a friend's ingestion doesn't belong to the user's own timeline, so it never starts the notification
fun shouldAutoStart(consumerName: String?, ingestionTime: Instant, now: Instant): Boolean =
    consumerName == null && isRecentForAutoStart(ingestionTime, now)

// auto-start only for ingestions taken around now, not for entries about the past or plans for later
fun isRecentForAutoStart(ingestionTime: Instant, now: Instant): Boolean =
    ingestionTime >= now - Duration.ofMinutes(60) && ingestionTime <= now + Duration.ofMinutes(5)

private const val notificationLineScale = 0.6f
