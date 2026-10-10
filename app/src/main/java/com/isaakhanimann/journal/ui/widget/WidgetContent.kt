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

import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.SubstanceLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.buildLiveNotificationContent
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.hasActiveEffects
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import java.time.Instant

// an experience and the user's own effect lines in it, from myEffectLines
data class WidgetCandidate(val experience: Experience, val lines: List<DataForOneEffectLine>)

data class WidgetSettings(
    val areSubstanceHeightsIndependent: Boolean,
    val areRoutesMerged: Boolean,
    val savedTimeDisplayOption: SavedTimeDisplayOption,
)

sealed interface WidgetContent {
    data class Active(
        val experienceId: Int,
        val title: String,
        val lines: List<SubstanceLine>,
        val model: AllTimelinesModel,
        val nextRefreshAt: Instant,
    ) : WidgetContent

    data object NothingActive : WidgetContent
}

// the most recent experience whose estimated effects haven't ended, so an ended newer one doesn't hide a running older one
fun chooseWidgetExperience(candidatesNewestFirst: List<WidgetCandidate>, settings: WidgetSettings, now: Instant): WidgetCandidate? =
    candidatesNewestFirst.firstOrNull {
        hasActiveEffects(it.lines, settings.areSubstanceHeightsIndependent, settings.areRoutesMerged, now)
    }

fun buildWidgetContent(chosen: WidgetCandidate?, settings: WidgetSettings, now: Instant): WidgetContent {
    if (chosen == null || chosen.lines.isEmpty()) return WidgetContent.NothingActive
    val model = AllTimelinesModel(
        dataForLines = chosen.lines,
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = settings.areSubstanceHeightsIndependent,
        areRoutesMerged = settings.areRoutesMerged
    )
    // the widgets show no plan, see the spec
    val content = buildLiveNotificationContent(
        title = chosen.experience.title,
        model = model,
        lines = chosen.lines,
        plannedIngestion = null,
        savedTimeDisplayOption = settings.savedTimeDisplayOption,
        now = now
    )
    if (content.isFinished) return WidgetContent.NothingActive
    return WidgetContent.Active(chosen.experience.id, content.title, content.lines, model, content.nextRefreshAt)
}
