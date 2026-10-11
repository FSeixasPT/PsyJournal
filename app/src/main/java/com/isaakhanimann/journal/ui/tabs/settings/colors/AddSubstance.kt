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

// the substances the "+" button can add: not configured yet and matching the search
fun substancesToAdd(allNames: List<String>, configured: List<SubstanceCompanion>, query: String): List<String> {
    val configuredNames = configured.map { it.substanceName }.toSet()
    return allNames
        .distinct()
        .filter { it !in configuredNames && it.contains(query.trim(), ignoreCase = true) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}

// a color no other substance uses, preferring the ones picked for good contrast, like when logging an ingestion
fun newCompanion(substanceName: String, configured: List<SubstanceCompanion>): SubstanceCompanion {
    val used = configured.map { it.color }.toSet()
    val unused = AdaptiveColor.entries.filter { it !in used }
    val color = unused.filter { it.isPreferred }.randomOrNull() ?: unused.randomOrNull() ?: AdaptiveColor.entries.random()
    return SubstanceCompanion(substanceName = substanceName, color = color)
}
