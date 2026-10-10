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

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.runSafely
import com.isaakhanimann.journal.ui.tabs.journal.experience.myEffectLines
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    private val userPreferences: UserPreferences,
) {
    private val mutex = Mutex()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w("Widgets", "Widget update failed", error)
        }
    )

    // a failed refresh is tried again later, so the widgets never stay frozen
    suspend fun refreshAll() = runSafely(
        onFailure = { error ->
            Log.w("Widgets", "Widget refresh failed", error)
            scheduleRefresh(Instant.now() + retryAfterFailure)
        }
    ) {
        mutex.withLock { refreshLocked() }
    }

    // keeps the widgets in step with the journal while the app process runs
    @OptIn(FlowPreview::class)
    fun observe() {
        scope.launch {
            combine(
                experienceRepo.getSortedExperiencesWithIngestionsFlow(),
                userPreferences.savedTimeDisplayOptionFlow,
                userPreferences.areRoutesMergedFlow,
                userPreferences.areSubstanceHeightsIndependentFlow,
            ) { _, _, _, _ -> }
                .debounce(500)
                .collect { refreshAll() }
        }
    }

    private suspend fun refreshLocked() {
        val manager = AppWidgetManager.getInstance(context)
        val idsByKind = WidgetKind.entries.associateWith { manager.getAppWidgetIds(ComponentName(context, providerClass(it))) }
        val hasWidgets = idsByKind.values.any { it.isNotEmpty() }
        if (!hasWidgets) {
            cancelRefresh()
            return
        }
        val now = Instant.now()
        val content = loadContent(now)
        val configuration = context.resources.configuration
        idsByKind.forEach { (kind, ids) ->
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val (widthDp, heightDp) = widgetSizeDp(
                    isPortrait = isPortraitLauncher(
                        isPortraitNow = configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
                        smallestScreenWidthDp = configuration.smallestScreenWidthDp
                    ),
                    minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                    maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                    minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                    maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                )
                manager.updateAppWidget(id, renderTimelineWidget(context, kind, content, widthDp, heightDp, now))
            }
        }
        val at = widgetRefreshAt(content, hasWidgets)
        if (at != null) scheduleRefresh(at) else cancelRefresh()
    }

    // inexact, so no exact-alarm permission is needed
    private fun scheduleRefresh(at: Instant) {
        alarmManager().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WidgetRefreshReceiver.pendingIntent(context))
    }

    private fun cancelRefresh() = alarmManager().cancel(WidgetRefreshReceiver.pendingIntent(context))

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)

    private suspend fun loadContent(now: Instant): WidgetContent {
        val settings = WidgetSettings(
            areSubstanceHeightsIndependent = userPreferences.areSubstanceHeightsIndependentFlow.first(),
            areRoutesMerged = userPreferences.areRoutesMergedFlow.first(),
            savedTimeDisplayOption = userPreferences.savedTimeDisplayOptionFlow.first(),
        )
        // 3 days is longer than any substance stays active
        val recentIngestions = experienceRepo.getIngestionsWithCompanions(
            fromInstant = now - Duration.ofDays(3),
            toInstant = now + Duration.ofDays(1)
        ).map { it.ingestion.experienceId to it.ingestion.time }
        // loaded one at a time, latest first, and stops at the first active one
        var chosen: WidgetCandidate? = null
        for (experienceId in candidateExperienceIdsLatestFirst(recentIngestions)) {
            val experience = experienceRepo.getExperience(experienceId) ?: continue
            val candidate = WidgetCandidate(
                experience,
                myEffectLines(experienceRepo.getIngestionsWithCompanions(experienceId), substanceRepo)
            )
            if (chooseWidgetExperience(listOf(candidate), settings, now) != null) {
                chosen = candidate
                break
            }
        }
        return buildWidgetContent(chosen, settings, now)
    }
}

fun providerClass(kind: WidgetKind): Class<out TimelineWidgetProvider> = when (kind) {
    WidgetKind.FULL -> FullTimelineWidget::class.java
    WidgetKind.STATUS -> StatusTimelineWidget::class.java
}

private val retryAfterFailure: Duration = Duration.ofMinutes(5)
