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

package com.isaakhanimann.journal.data.supplements

import java.time.Instant

// the one running routine; lastPostedAt keeps a step from being shown twice
data class ActiveRoutine(val kind: RoutineKind, val doseTime: Instant, val mlPerHour: Int, val lastPostedAt: Instant?) {
    fun toPreferenceString(): String = "${kind.name}|$doseTime|$mlPerHour|${lastPostedAt ?: ""}"

    companion object {
        fun fromPreferenceString(value: String?): ActiveRoutine? {
            val parts = value?.split("|") ?: return null
            if (parts.size != 4) return null
            val kind = RoutineKind.entries.firstOrNull { it.name == parts[0] } ?: return null
            val dose = runCatching { Instant.parse(parts[1]) }.getOrNull() ?: return null
            val rate = parts[2].toIntOrNull() ?: return null
            val posted = if (parts[3].isEmpty()) null else runCatching { Instant.parse(parts[3]) }.getOrNull() ?: return null
            return ActiveRoutine(kind, dose, rate, posted)
        }
    }
}
