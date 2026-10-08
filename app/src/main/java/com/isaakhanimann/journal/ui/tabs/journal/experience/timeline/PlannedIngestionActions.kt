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

import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.repositories.SearchRepository
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.Interaction
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.InteractionChecker
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

// everything the planned-ingestion chip needs from outside the chart
interface PlannedIngestionActions {
    val pinnedRedoseFlow: StateFlow<PinnedRedose?>
    fun pin(planned: PlannedIngestion?)
    fun getSubstance(substanceName: String): Substance?
    fun buildLine(planned: PlannedIngestion, time: Instant, colorsOnChart: List<AdaptiveColor>): DataForOneEffectLine?
    fun defaultPlan(realLines: List<DataForOneEffectLine>): PlannedIngestion?
    fun searchSubstances(searchText: String, experienceSubstanceNamesByRecency: List<String>): List<PickerEntry>
    fun companionColor(substanceName: String): AdaptiveColor?
    fun interactions(planned: PlannedIngestion, otherSubstanceNames: List<String>): List<Interaction>
    fun log(planned: PlannedIngestion, color: AdaptiveColor)
}

class PlannedIngestionHelper @Inject constructor(
    private val substanceRepo: SubstanceRepository,
    private val searchRepo: SearchRepository,
    private val experienceRepo: ExperienceRepository,
    private val interactionChecker: InteractionChecker,
    private val userPreferences: UserPreferences,
) {
    fun actionsFor(experienceId: Int, scope: CoroutineScope): PlannedIngestionActions {
        val started = SharingStarted.WhileSubscribed(5000)
        // read with .value and never collected by the UI, so they have to be collected eagerly
        val companions: StateFlow<List<SubstanceCompanion>> =
            experienceRepo.getAllSubstanceCompanionsFlow().stateIn(scope, SharingStarted.Eagerly, emptyList())
        val recentNames: StateFlow<List<String>> =
            experienceRepo.getSortedLastUsedSubstanceNamesFlow(limit = 200).stateIn(scope, SharingStarted.Eagerly, emptyList())
        val pinFlow: StateFlow<PinnedRedose?> = userPreferences.pinnedRedoseFlow
            .map { pin -> pin?.takeIf { it.experienceId == experienceId } }
            .stateIn(scope, started, null)

        return object : PlannedIngestionActions {
            override val pinnedRedoseFlow = pinFlow

            override fun pin(planned: PlannedIngestion?) {
                scope.launch {
                    userPreferences.savePinnedRedose(
                        planned?.toPinnedRedose(experienceId, currentPin = pinFlow.value, now = Instant.now())
                    )
                }
            }

            override fun getSubstance(substanceName: String) = substanceRepo.getSubstance(substanceName)

            override fun buildLine(planned: PlannedIngestion, time: Instant, colorsOnChart: List<AdaptiveColor>): DataForOneEffectLine? {
                val substance = substanceRepo.getSubstance(planned.substanceName) ?: return null
                val color = choosePlannedColor(planned.substanceName, companions.value, colorsOnChart)
                return buildPlannedLine(planned, substance, color, time)
            }

            override fun defaultPlan(realLines: List<DataForOneEffectLine>) =
                defaultPlannedIngestion(realLines) { substanceRepo.getSubstance(it) }

            override fun searchSubstances(searchText: String, experienceSubstanceNamesByRecency: List<String>): List<PickerEntry> {
                val matching = searchRepo.getMatchingSubstances(
                    searchText = searchText,
                    filterCategories = emptyList(),
                    recentlyUsedSubstanceNamesSorted = recentNames.value
                ).map { it.substance }
                return buildPickerEntries(matching, experienceSubstanceNamesByRecency)
            }

            override fun companionColor(substanceName: String) =
                companions.value.firstOrNull { it.substanceName == substanceName }?.color

            override fun interactions(planned: PlannedIngestion, otherSubstanceNames: List<String>) =
                interactionsWith(planned.substanceName, otherSubstanceNames, interactionChecker::getInteractionBetween)

            override fun log(planned: PlannedIngestion, color: AdaptiveColor) {
                scope.launch {
                    logPlannedIngestion(
                        planned = planned,
                        experienceId = experienceId,
                        previewColor = color,
                        now = Instant.now(),
                        // read from the database, so an existing color is never replaced
                        loadCompanions = { experienceRepo.getAllSubstanceCompanionsFlow().first() },
                        save = { ingestion, companion -> experienceRepo.insertIngestionAndCompanion(ingestion, companion) }
                    )
                }
            }
        }
    }
}
