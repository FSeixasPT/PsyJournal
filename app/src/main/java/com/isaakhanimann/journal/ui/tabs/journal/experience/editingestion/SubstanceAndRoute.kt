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

package com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion

import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.search.suggestion.models.CustomUnitDose
import com.isaakhanimann.journal.ui.tabs.settings.colors.newCompanion

// the routes PsychonautWiki documents for the substance first, then the others
fun routesInOrder(documented: List<AdministrationRoute>): List<AdministrationRoute> =
    documented.distinct() + AdministrationRoute.entries.filter { it !in documented }

// the dose part of an ingestion being edited
data class DoseFields(
    val dose: Double?,
    val isEstimate: Boolean,
    val estimatedDoseStandardDeviation: Double?,
    val units: String,
    val customUnit: CustomUnit?,
) {
    // a custom unit only belongs to its own substance and route, so with another one it becomes the dose it stood for
    fun afterChangeTo(substanceName: String, route: AdministrationRoute): DoseFields {
        val unit = customUnit ?: return this
        if (unit.substanceName == substanceName && unit.administrationRoute == route) return this
        val count = dose ?: return copy(units = unit.originalUnit, customUnit = null)
        val unitDose = CustomUnitDose(
            dose = count,
            isEstimate = isEstimate,
            estimatedDoseStandardDeviation = estimatedDoseStandardDeviation,
            customUnit = unit
        )
        val isConvertedEstimate = isEstimate || unit.isEstimate
        return DoseFields(
            dose = unitDose.calculatedDose,
            isEstimate = isConvertedEstimate,
            estimatedDoseStandardDeviation = if (isConvertedEstimate) unitDose.calculatedDoseStandardDeviation else null,
            units = unit.originalUnit,
            customUnit = null
        )
    }
}

// the substance's color and visibility, or an unused color for a substance never logged before
fun companionFor(substanceName: String, configured: List<SubstanceCompanion>): SubstanceCompanion =
    configured.firstOrNull { it.substanceName == substanceName } ?: newCompanion(substanceName, configured)
