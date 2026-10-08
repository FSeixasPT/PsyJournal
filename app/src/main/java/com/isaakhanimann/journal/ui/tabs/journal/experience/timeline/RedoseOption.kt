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

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString

// a substance and route that can be previewed as taken again, with the last dose taken as default
data class RedoseOption(
    val substanceName: String,
    val route: AdministrationRoute,
    val dose: Double,
    val units: String,
    val color: AdaptiveColor
)

// names the route only if the substance has chips for several routes, because then they share one line
fun RedoseOption.chipText(allOptions: List<RedoseOption>): String {
    val isRouteShown = allOptions.count { it.substanceName == substanceName } > 1
    val route = if (isRouteShown) " ${route.displayText.lowercase()}" else ""
    return "$substanceName · ${dose.toReadableString()} $units$route"
}
