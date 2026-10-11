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

package com.isaakhanimann.journal.ui.tabs.settings.colors

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion

// the substances the "+" button can add: not configured yet and matching the search,
// the exact match first, then names starting with the search, each group alphabetical
fun substancesToAdd(allNames: List<String>, configured: List<SubstanceCompanion>, query: String): List<String> {
    val configuredNames = configured.map { it.substanceName }.toSet()
    val search = query.trim()
    return allNames
        .distinct()
        .filter { it !in configuredNames && it.contains(search, ignoreCase = true) }
        .sortedWith(
            compareBy<String> { name ->
                when {
                    search.isEmpty() -> 0
                    name.equals(search, ignoreCase = true) -> 0
                    name.startsWith(search, ignoreCase = true) -> 1
                    else -> 2
                }
            }.then(String.CASE_INSENSITIVE_ORDER)
        )
}

// a color no other substance uses, preferring the ones picked for good contrast, like when logging an ingestion
fun newCompanion(substanceName: String, configured: List<SubstanceCompanion>): SubstanceCompanion {
    val used = configured.map { it.color }.toSet()
    val unused = AdaptiveColor.entries.filter { it !in used }
    val color = unused.filter { it.isPreferred }.randomOrNull() ?: unused.randomOrNull() ?: AdaptiveColor.entries.random()
    return SubstanceCompanion(substanceName = substanceName, color = color)
}

// a substance in the journal needs its color for its timeline lines, so only unused ones can be removed
fun canRemoveColor(substanceName: String, loggedSubstanceNames: Set<String>): Boolean =
    substanceName !in loggedSubstanceNames

fun removeColorExplanation(substanceName: String, loggedSubstanceNames: Set<String>): String =
    if (canRemoveColor(substanceName, loggedSubstanceNames)) {
        "Removes $substanceName from this list. It gets a new color if you log it later."
    } else {
        "$substanceName is in your journal, so it keeps a color. To leave it out of timelines, turn it off instead."
    }
