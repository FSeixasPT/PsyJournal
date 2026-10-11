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

package com.isaakhanimann.journal.ui.tabs.search.substance

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion

// what the substance page needs to change the substance's journal color, like in Settings → Substance colors
data class JournalColorChoice(
    val color: AdaptiveColor,
    val alreadyUsedColors: List<AdaptiveColor>,
    val otherColors: List<AdaptiveColor>,
    // false when the substance is hidden from the timelines of every experience
    val isPartOfTimeline: Boolean,
)

// null for a substance that was never logged, its color is chosen when it is first logged
fun journalColorChoice(companions: List<SubstanceCompanion>, substanceName: String): JournalColorChoice? {
    val companion = companions.firstOrNull { it.substanceName == substanceName } ?: return null
    val used = companions.map { it.color }.distinct()
    return JournalColorChoice(
        color = companion.color,
        alreadyUsedColors = used,
        otherColors = AdaptiveColor.entries.filter { it !in used },
        isPartOfTimeline = companion.isPartOfTimeline,
    )
}

// a new color must not show a substance the user hid from timelines
fun companionWithColor(substanceName: String, existing: SubstanceCompanion?, color: AdaptiveColor): SubstanceCompanion =
    existing?.copy(color = color) ?: SubstanceCompanion(substanceName = substanceName, color = color)
