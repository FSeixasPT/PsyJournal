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
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.InteractionType
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.Interaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

class QuickLogTest {

    private val now = Instant.parse("2026-01-01T21:34:00Z")
    private val planned = PlannedIngestion("Ketamine", AdministrationRoute.INSUFFLATED, 40.0, "mg")

    @Test
    fun ingestionIsTakenNowByYouInThisExperience() {
        val ingestion = planned.toIngestion(experienceId = 7, now = now)
        assertEquals(0, ingestion.id)
        assertEquals("Ketamine", ingestion.substanceName)
        assertEquals(AdministrationRoute.INSUFFLATED, ingestion.administrationRoute)
        assertEquals(40.0, ingestion.dose!!, 0.001)
        assertEquals("mg", ingestion.units)
        assertEquals(now, ingestion.time)
        assertNull(ingestion.endTime)
        assertEquals(now, ingestion.creationDate)
        assertEquals(false, ingestion.isDoseAnEstimate)
        assertNull(ingestion.estimatedDoseStandardDeviation)
        assertEquals(7, ingestion.experienceId)
        assertNull(ingestion.consumerName)
        assertNull(ingestion.notes)
        assertNull(ingestion.stomachFullness)
        assertNull(ingestion.customUnitId)
    }

    @Test
    fun existingCompanionIsKept() {
        val existing = SubstanceCompanion("Ketamine", AdaptiveColor.PINK)
        assertEquals(existing, companionToSave("Ketamine", existing, AdaptiveColor.BLUE))
    }

    @Test
    fun newCompanionUsesThePreviewColor() {
        assertEquals(SubstanceCompanion("Ketamine", AdaptiveColor.BLUE), companionToSave("Ketamine", null, AdaptiveColor.BLUE))
    }

    @Test
    fun interactionsListEachOtherSubstanceOnce() {
        val lookups = mutableListOf<Pair<String, String>>()
        val interactions = interactionsWith("Ketamine", listOf("MDMA", "Ketamine", "MDMA", "Cannabis")) { a, b ->
            lookups.add(Pair(a, b))
            if (b == "MDMA") Interaction(a, b, InteractionType.DANGEROUS) else null
        }
        assertEquals(listOf(Pair("Ketamine", "MDMA"), Pair("Ketamine", "Cannabis")), lookups)
        assertEquals(listOf(Interaction("Ketamine", "MDMA", InteractionType.DANGEROUS)), interactions)
    }

    @Test
    fun loggingKeepsTheColorTheSubstanceAlreadyHas() {
        var saved: SubstanceCompanion? = null
        runBlocking {
            logPlannedIngestion(
                planned = planned,
                experienceId = 7,
                previewColor = AdaptiveColor.RED,
                now = now,
                loadCompanions = { listOf(SubstanceCompanion("Ketamine", AdaptiveColor.BLUE)) },
                save = { _, companion -> saved = companion }
            )
        }
        assertEquals(SubstanceCompanion("Ketamine", AdaptiveColor.BLUE), saved)
    }
}
