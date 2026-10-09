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
import java.time.Instant
import java.time.temporal.ChronoUnit

// experience ids are reused after deleting an experience, so the creation date tells them apart
data class LiveExperience(val experienceId: Int, val experienceCreatedAt: Instant) {
    fun toPreferenceString(): String = "$experienceId|$experienceCreatedAt"

    fun isFor(experience: Experience): Boolean =
        experience.id == experienceId &&
                experience.creationDate.truncatedTo(ChronoUnit.MILLIS) == experienceCreatedAt.truncatedTo(ChronoUnit.MILLIS)

    companion object {
        fun fromPreferenceString(value: String?): LiveExperience? {
            val parts = value?.split("|") ?: return null
            if (parts.size != 2) return null
            val id = parts[0].toIntOrNull() ?: return null
            val createdAt = runCatching { Instant.parse(parts[1]) }.getOrNull() ?: return null
            return LiveExperience(id, createdAt)
        }
    }
}
