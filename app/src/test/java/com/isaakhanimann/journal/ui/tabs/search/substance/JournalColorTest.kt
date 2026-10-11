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

package com.isaakhanimann.journal.ui.tabs.search.substance

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class JournalColorTest {
    private val companions = listOf(
        SubstanceCompanion("MDMA", AdaptiveColor.PINK),
        SubstanceCompanion("2C-B", AdaptiveColor.BLUE, isPartOfTimeline = false),
    )

    @Test
    fun aLoggedSubstanceHasItsColor() {
        val choice = journalColorChoice(companions, "2C-B")!!
        assertEquals(AdaptiveColor.BLUE, choice.color)
        assertEquals(listOf(AdaptiveColor.PINK, AdaptiveColor.BLUE), choice.alreadyUsedColors)
        assertFalse(AdaptiveColor.PINK in choice.otherColors)
        assertEquals(AdaptiveColor.entries.size - 2, choice.otherColors.size)
    }

    @Test
    fun aSubstanceNeverLoggedHasNoChoice() {
        assertNull(journalColorChoice(companions, "LSD"))
    }

    @Test
    fun theChoiceShowsWhetherTheSubstanceIsHiddenFromTimelines() {
        assertEquals(true, journalColorChoice(companions, "MDMA")?.isPartOfTimeline)
        assertEquals(false, journalColorChoice(companions, "2C-B")?.isPartOfTimeline)
    }

    @Test
    fun changingTheColorKeepsTheSubstanceHidden() {
        val hidden = SubstanceCompanion("2C-B", AdaptiveColor.BLUE, isPartOfTimeline = false)
        assertEquals(
            SubstanceCompanion("2C-B", AdaptiveColor.RED, isPartOfTimeline = false),
            companionWithColor("2C-B", existing = hidden, color = AdaptiveColor.RED)
        )
    }

    @Test
    fun aNewSubstanceIsShownInTimelines() {
        assertEquals(
            SubstanceCompanion("LSD", AdaptiveColor.RED, isPartOfTimeline = true),
            companionWithColor("LSD", existing = null, color = AdaptiveColor.RED)
        )
    }
}
