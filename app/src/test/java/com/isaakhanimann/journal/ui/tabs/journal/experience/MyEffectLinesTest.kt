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

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Category
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.SubstanceWithCategories
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepositoryInterface
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MyEffectLinesTest {
    private object NoSubstances : SubstanceRepositoryInterface {
        override fun getAllSubstances(): List<Substance> = emptyList()
        override fun getAllSubstancesWithCategories(): List<SubstanceWithCategories> = emptyList()
        override fun getAllCategories(): List<Category> = emptyList()
        override fun getSubstance(substanceName: String): Substance? = null
        override fun getCategory(categoryName: String): Category? = null
        override fun getSubstanceWithCategories(substanceName: String): SubstanceWithCategories? = null
    }

    private fun ingestion(
        name: String,
        time: String,
        consumer: String? = null,
        dose: Double = 10.0,
        isPartOfTimeline: Boolean = true,
        companion: SubstanceCompanion? = null,
    ) = IngestionWithCompanionAndCustomUnit(
        ingestion = Ingestion(
            substanceName = name,
            time = Instant.parse(time),
            administrationRoute = AdministrationRoute.ORAL,
            dose = dose,
            isDoseAnEstimate = false,
            estimatedDoseStandardDeviation = null,
            units = "mg",
            experienceId = 1,
            notes = null,
            stomachFullness = null,
            consumerName = consumer,
            customUnitId = null,
            isPartOfTimeline = isPartOfTimeline
        ),
        substanceCompanion = companion,
        customUnit = null
    )

    @Test
    fun onlyTheUsersOwnIngestionsInTimeOrder() {
        val lines = myEffectLines(
            listOf(
                ingestion("B", "2026-01-01T21:00:00Z"),
                ingestion("A", "2026-01-01T20:00:00Z"),
                ingestion("C", "2026-01-01T20:30:00Z", consumer = "Friend"),
            ),
            NoSubstances
        )
        assertEquals(listOf("A", "B"), lines.map { it.substanceName })
    }

    @Test
    fun hiddenIngestionsAreLeftOut() {
        val lines = myEffectLines(
            listOf(
                ingestion("A", "2026-01-01T20:00:00Z"),
                ingestion("B", "2026-01-01T21:00:00Z", isPartOfTimeline = false),
            ),
            NoSubstances
        )
        assertEquals(listOf("A"), lines.map { it.substanceName })
    }

    @Test
    fun substancesHiddenFromEveryExperienceAreLeftOut() {
        val hidden = SubstanceCompanion("B", AdaptiveColor.BLUE, isPartOfTimeline = false)
        val lines = myEffectLines(
            listOf(
                ingestion("A", "2026-01-01T20:00:00Z", companion = SubstanceCompanion("A", AdaptiveColor.RED)),
                ingestion("B", "2026-01-01T21:00:00Z", companion = hidden),
                ingestion("B", "2026-01-01T22:00:00Z", companion = hidden),
            ),
            NoSubstances
        )
        assertEquals(listOf("A"), lines.map { it.substanceName })
    }

    @Test
    fun hidingARedoseKeepsTheHeightOfTheOtherDoses() {
        // without dose ranges a dose's height is relative to the average dose: 10 / ((10 + 30) / 2)
        val lines = myEffectLines(
            listOf(
                ingestion("A", "2026-01-01T20:00:00Z", dose = 10.0),
                ingestion("A", "2026-01-01T21:00:00Z", dose = 30.0, isPartOfTimeline = false),
            ),
            NoSubstances
        )
        assertEquals(listOf(0.5f), lines.map { it.height })
    }
}
