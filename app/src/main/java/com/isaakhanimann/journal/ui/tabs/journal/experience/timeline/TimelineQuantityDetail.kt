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

data class TimelineQuantityDetail(
    val substanceName: String,
    val color: AdaptiveColor,
    // intensity at the selected time relative to the highest point of the curve, from 0 to 1
    val fractionOfPeak: Float,
    // for each route of the line: dose that would cause this intensity at its peak.
    // Empty if not all doses are known in consistent units.
    val doseEquivalents: List<RouteDoseEquivalent>,
    // like the live notification, e.g. "Coming up · peaks in 20 min"; only for now, null once it ended
    val phaseText: String? = null,
)

data class RouteDoseEquivalent(
    val route: AdministrationRoute,
    val dose: Double,
    val units: String
)
