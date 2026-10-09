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

import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel

sealed interface RefreshDecision {
    data object Stop : RefreshDecision
    data class Post(
        val experienceId: Int,
        val content: LiveNotificationContent,
        val model: AllTimelinesModel,
    ) : RefreshDecision
}

// stops for a deleted or replaced experience, blocked notifications and effects that have ended
suspend fun decideRefresh(
    live: LiveExperience?,
    experience: Experience?,
    canPost: Boolean,
    build: suspend (Experience) -> RefreshDecision.Post?,
): RefreshDecision {
    if (live == null || experience == null || !live.isFor(experience) || !canPost) return RefreshDecision.Stop
    val post = build(experience) ?: return RefreshDecision.Stop
    return if (post.content.isFinished) RefreshDecision.Stop else post
}
