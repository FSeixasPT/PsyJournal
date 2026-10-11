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

package com.isaakhanimann.journal.ui.tabs.settings.colors

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddSubstanceTest {
    private val configured = listOf(SubstanceCompanion("MDMA", AdaptiveColor.PINK))

    @Test
    fun configuredSubstancesAreNotOffered() {
        assertEquals(listOf("2C-B", "LSD"), substancesToAdd(listOf("LSD", "MDMA", "2C-B"), configured, query = ""))
    }

    @Test
    fun theSearchMatchesAnyPartOfTheNameIgnoringCase() {
        assertEquals(listOf("2C-B", "2C-E"), substancesToAdd(listOf("2C-B", "LSD", "2C-E"), configured, query = "2c"))
    }

    @Test
    fun theExactMatchComesFirst() {
        val names = listOf("1P-LSD", "LSD", "1cP-LSD")
        assertEquals(listOf("LSD", "1cP-LSD", "1P-LSD"), substancesToAdd(names, configured, query = "lsd"))
    }

    @Test
    fun namesStartingWithTheSearchComeBeforeOtherMatches() {
        val names = listOf("2-Fluorodeschloroketamine", "Methoxetamine", "Ketamine")
        assertEquals(listOf("Ketamine", "2-Fluorodeschloroketamine"), substancesToAdd(names, configured, query = "keta"))
    }

    @Test
    fun aNameIsOfferedOnceEvenIfItIsBothAPsychonautWikiAndACustomSubstance() {
        assertEquals(listOf("LSD"), substancesToAdd(listOf("LSD", "LSD"), configured, query = ""))
    }

    @Test
    fun anAddedSubstanceGetsAColorNoOtherSubstanceUses() {
        val used = AdaptiveColor.entries.drop(1).map { SubstanceCompanion(it.name, it) }
        val added = newCompanion("LSD", used)
        assertEquals(SubstanceCompanion("LSD", AdaptiveColor.entries.first()), added)
    }

    @Test
    fun anAddedSubstanceIsShownInTimelines() {
        val added = newCompanion("LSD", configured)
        assertTrue(added.isPartOfTimeline)
        assertFalse(added.color == AdaptiveColor.PINK)
    }

    @Test
    fun aSubstanceWithoutIngestionsCanBeRemoved() {
        assertEquals(
            "Removes Caffeine from this list. It gets a new color if you log it later.",
            removeColorExplanation("Caffeine", loggedSubstanceNames = setOf("2C-B"))
        )
        assertTrue(canRemoveColor("Caffeine", loggedSubstanceNames = setOf("2C-B")))
    }

    @Test
    fun aSubstanceInTheJournalKeepsItsColor() {
        assertFalse(canRemoveColor("2C-B", loggedSubstanceNames = setOf("2C-B")))
        assertEquals(
            "2C-B is in your journal, so it keeps a color. To leave it out of timelines, turn it off instead.",
            removeColorExplanation("2C-B", loggedSubstanceNames = setOf("2C-B"))
        )
    }
}
