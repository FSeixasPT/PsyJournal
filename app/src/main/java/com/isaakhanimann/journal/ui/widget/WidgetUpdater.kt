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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.runSafely
import com.isaakhanimann.journal.ui.tabs.journal.experience.myEffectLines
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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

    // renders every placed widget of every kind from one content
    suspend fun refreshAll() = runSafely(onFailure = { Log.w("Widgets", "Widget refresh failed", it) }) {
        mutex.withLock { refreshLocked() }
    }

    private suspend fun refreshLocked() {
        val manager = AppWidgetManager.getInstance(context)
        val idsByKind = WidgetKind.entries.associateWith { manager.getAppWidgetIds(ComponentName(context, providerClass(it))) }
        if (idsByKind.values.all { it.isEmpty() }) return
        val now = Instant.now()
        val content = loadContent(now)
        idsByKind.forEach { (kind, ids) ->
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).takeIf { it > 0 }
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 }
                manager.updateAppWidget(id, renderTimelineWidget(context, kind, content, widthDp, heightDp, now))
            }
        }
    }

    private suspend fun loadContent(now: Instant): WidgetContent {
        val settings = WidgetSettings(
            areSubstanceHeightsIndependent = userPreferences.areSubstanceHeightsIndependentFlow.first(),
            areRoutesMerged = userPreferences.areRoutesMergedFlow.first(),
            savedTimeDisplayOption = userPreferences.savedTimeDisplayOptionFlow.first(),
        )
        val experiences = experienceRepo.getSortedExperiencesWithIngestionsWithSortDateBetween(
            fromInstant = now - Duration.ofDays(3),
            toInstant = now + Duration.ofDays(1)
        ).sortedByDescending { it.experience.sortDate }
        // loaded one at a time, newest first, and stops at the first active one
        var chosen: WidgetCandidate? = null
        for (experience in experiences) {
            val candidate = WidgetCandidate(
                experience.experience,
                myEffectLines(experienceRepo.getIngestionsWithCompanions(experience.experience.id), substanceRepo)
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
    WidgetKind.CHART -> ChartTimelineWidget::class.java
    WidgetKind.STATUS -> StatusTimelineWidget::class.java
}
