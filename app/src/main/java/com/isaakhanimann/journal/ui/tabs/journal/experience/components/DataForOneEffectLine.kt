/*
 * Copyright (c) 2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.experience.components

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import java.time.Instant

data class DataForOneEffectLine(
    val substanceName: String,
    val route: AdministrationRoute,
    val roaDuration: RoaDuration?,
    val height: Float,
    val horizontalWeight: Float,
    val color: AdaptiveColor,
    val startTime: Instant,
    val endTime: Instant?,
    // the dose that corresponds to a height of 1, null if the dose of this ingestion is unknown
    val referenceDose: Double? = null,
    val doseUnits: String? = null,
    // true if referenceDose comes from the dose ranges of this route; only then are routes comparable.
    // Describes the route, so it is also set when this ingestion's dose is unknown.
    val isReferenceDoseFromDoseRanges: Boolean = false,
    // set if the ingestion was logged with a custom unit, e.g. 3 sprays
    val customUnitId: Int? = null,
    val customUnitCount: Double? = null
)