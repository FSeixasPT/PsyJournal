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

package com.isaakhanimann.journal.data.room.experiences.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerColorsTest {
    @Test
    fun thePickerOffersTwentyPresetsIncludingAllPreferredOnes() {
        assertEquals(20, AdaptiveColor.pickerColors.size)
        assertTrue(AdaptiveColor.pickerColors.containsAll(AdaptiveColor.entries.filter { it.isPreferred }))
    }

    @Test
    fun presetsLeftOutOfThePickerAreNotOfferedAsUnused() {
        val offered = colorsToOffer(listOf(AdaptiveColor.RED, AdaptiveColor.TOMATO, CustomColor(0x3FA7D6)))
        assertEquals(listOf(AdaptiveColor.RED, CustomColor(0x3FA7D6)), offered)
    }

    @Test
    fun aLeftOutPresetStillReads() {
        assertEquals(AdaptiveColor.TOMATO, AdaptiveColor.fromName("TOMATO"))
    }
}
