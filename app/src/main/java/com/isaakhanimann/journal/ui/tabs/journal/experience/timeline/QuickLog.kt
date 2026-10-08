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
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.Interaction
import java.time.Instant

// the planned ingestion as a real ingestion taken now by you in this experience
// with a custom unit the count and the unit name are saved, like the normal add-ingestion flow does
fun PlannedIngestion.toIngestion(experienceId: Int, now: Instant): Ingestion {
    val unit = customUnit?.takeIf { it.unit.isNotEmpty() }
    return Ingestion(
        substanceName = substanceName,
        time = now,
        endTime = null,
        creationDate = now,
        administrationRoute = route,
        dose = unit?.count ?: dose,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = unit?.unit ?: units,
        experienceId = experienceId,
        notes = null,
        stomachFullness = null,
        consumerName = null,
        customUnitId = unit?.id
    )
}

fun companionToSave(substanceName: String, existing: SubstanceCompanion?, color: AdaptiveColor): SubstanceCompanion =
    existing ?: SubstanceCompanion(substanceName = substanceName, color = color)

fun interactionsWith(
    plannedSubstanceName: String,
    otherSubstanceNames: List<String>,
    lookup: (String, String) -> Interaction?
): List<Interaction> = otherSubstanceNames
    .distinct()
    .filter { it != plannedSubstanceName }
    .mapNotNull { lookup(plannedSubstanceName, it) }

// saves the planned ingestion, keeping the color the substance already has in the journal
suspend fun logPlannedIngestion(
    planned: PlannedIngestion,
    experienceId: Int,
    previewColor: AdaptiveColor,
    now: Instant,
    loadCompanions: suspend () -> List<SubstanceCompanion>,
    save: suspend (Ingestion, SubstanceCompanion) -> Unit,
) {
    val existing = loadCompanions().firstOrNull { it.substanceName == planned.substanceName }
    save(planned.toIngestion(experienceId, now), companionToSave(planned.substanceName, existing, previewColor))
}
