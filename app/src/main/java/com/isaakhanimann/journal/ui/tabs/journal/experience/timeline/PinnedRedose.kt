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

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.AdministrationRoute
import java.time.Instant

// keeps the original pin time if the same substance and route stays pinned, e.g. when only its dose changes
fun RedoseOption.toPinnedRedose(experienceId: Int, currentPin: PinnedRedose?, now: Instant): PinnedRedose {
    val isSameChip = currentPin != null && currentPin.experienceId == experienceId &&
            currentPin.substanceName == substanceName && currentPin.route == route
    return PinnedRedose(
        experienceId = experienceId,
        substanceName = substanceName,
        route = route,
        dose = dose,
        units = units,
        pinnedAt = if (isSameChip) currentPin!!.pinnedAt else now
    )
}

// a redose preview that stays visible until it is unpinned or no longer applies
data class PinnedRedose(
    val experienceId: Int,
    val substanceName: String,
    val route: AdministrationRoute,
    val dose: Double,
    val units: String,
    val pinnedAt: Instant
) {
    fun toPreferenceString(): String = listOf(
        experienceId.toString(),
        substanceName,
        route.name,
        dose.toString(),
        units,
        pinnedAt.toString()
    ).joinToString(SEPARATOR)

    companion object {
        // unit separator, which can't be part of a substance name or unit
        private const val SEPARATOR = "\u001F"

        fun fromPreferenceString(value: String?): PinnedRedose? {
            val parts = value?.split(SEPARATOR) ?: return null
            if (parts.size != 6) return null
            return try {
                PinnedRedose(
                    experienceId = parts[0].toInt(),
                    substanceName = parts[1],
                    route = AdministrationRoute.valueOf(parts[2]),
                    dose = parts[3].toDouble(),
                    units = parts[4],
                    pinnedAt = Instant.parse(parts[5])
                )
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: java.time.format.DateTimeParseException) {
                null
            }
        }
    }
}
