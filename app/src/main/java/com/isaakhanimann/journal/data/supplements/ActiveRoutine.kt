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

// the one running routine; lastPostedAt keeps a step from being shown twice, and linkedIngestionId is the
// logged dose it was started from, so deleting that dose stops it (null when planned on the MDMA page)
data class ActiveRoutine(
    val kind: RoutineKind,
    val doseTime: Instant,
    val mlPerHour: Int,
    val lastPostedAt: Instant?,
    val linkedIngestionId: Int? = null,
) {
    fun toPreferenceString(): String = "${kind.name}|$doseTime|$mlPerHour|${lastPostedAt ?: ""}|${linkedIngestionId ?: ""}"

    companion object {
        fun fromPreferenceString(value: String?): ActiveRoutine? {
            val parts = value?.split("|") ?: return null
            // routines saved before the link have 4 parts
            if (parts.size != 4 && parts.size != 5) return null
            val kind = RoutineKind.entries.firstOrNull { it.name == parts[0] } ?: return null
            val dose = runCatching { Instant.parse(parts[1]) }.getOrNull() ?: return null
            val rate = parts[2].toIntOrNull() ?: return null
            val posted = if (parts[3].isEmpty()) null else runCatching { Instant.parse(parts[3]) }.getOrNull() ?: return null
            val linked = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return null }
            return ActiveRoutine(kind, dose, rate, posted, linked)
        }
    }
}

fun shouldStopForDeletedDose(routine: ActiveRoutine?, linkedDoseExists: Boolean): Boolean =
    routine?.linkedIngestionId != null && !linkedDoseExists
