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

package com.isaakhanimann.journal.ui.tabs.journal.experience

import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepositoryInterface
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine

// the lines of the experience screen's main timeline, for use outside the screen
fun myEffectLines(
    ingestionsWithCompanions: List<IngestionWithCompanionAndCustomUnit>,
    substanceRepo: SubstanceRepositoryInterface,
): List<DataForOneEffectLine> {
    val mine = ingestionsWithCompanions
        .filter { it.ingestion.consumerName == null }
        .sortedBy { it.ingestion.time }
        .map { ingestionWithCompanion ->
            val ingestion = ingestionWithCompanion.ingestion
            val roa = substanceRepo.getSubstance(ingestion.substanceName)?.getRoa(ingestion.administrationRoute)
            IngestionWithAssociatedData(
                ingestionWithCompanionAndCustomUnit = ingestionWithCompanion,
                roaDuration = roa?.roaDuration,
                roaDose = roa?.roaDose
            )
        }
    val elements = ExperienceViewModel.getIngestionElements(mine)
    val substances = elements.mapNotNull {
        substanceRepo.getSubstance(it.ingestionWithCompanionAndCustomUnit.ingestion.substanceName)
    }
    return ExperienceViewModel.getDataForEffectTimelines(ingestionElements = elements, substances = substances)
}
