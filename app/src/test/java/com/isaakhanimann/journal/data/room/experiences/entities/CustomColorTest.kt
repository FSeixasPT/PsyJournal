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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColorTest {
    @Test
    fun presetsKeepTheirNamesSoSavedDataStillReads() {
        assertEquals("RED", AdaptiveColor.RED.name)
        assertEquals(AdaptiveColor.PINK, AdaptiveColor.fromName("PINK"))
    }

    @Test
    fun aCustomColorIsSavedAsItsHexCode() {
        val custom = CustomColor(0x3FA7D6)
        assertEquals("CUSTOM:#3FA7D6", custom.name)
        assertEquals(custom, AdaptiveColor.fromName("CUSTOM:#3FA7D6"))
    }

    @Test
    fun unreadableNamesGiveNoColor() {
        assertNull(AdaptiveColor.fromName("NOT_A_COLOR"))
        assertNull(AdaptiveColor.fromName("CUSTOM:#3FA7D"))
    }

    @Test
    fun theDatabaseStoresTheName() {
        val converter = AdaptiveColorConverter()
        assertEquals("CUSTOM:#3FA7D6", converter.fromColor(CustomColor(0x3FA7D6)))
        assertEquals(AdaptiveColor.BLUE, converter.toColor("BLUE"))
    }

    @Test
    fun backupsWriteTheSameNames() {
        val companions = listOf(
            SubstanceCompanion("MDMA", AdaptiveColor.PINK),
            SubstanceCompanion("LSD", CustomColor(0x3FA7D6))
        )
        val json = Json.encodeToString(companions)
        assertTrue(json.contains("\"color\":\"PINK\""))
        assertTrue(json.contains("\"color\":\"CUSTOM:#3FA7D6\""))
        assertEquals(companions, Json.decodeFromString<List<SubstanceCompanion>>(json))
    }

    @Test
    fun hexCodesAreReadWithOrWithoutTheHash() {
        assertEquals(0x3FA7D6, parseHexColor("#3fa7d6"))
        assertEquals(0x3FA7D6, parseHexColor("3FA7D6"))
        assertNull(parseHexColor("#3FA7D"))
        assertNull(parseHexColor("#GGGGGG"))
        assertEquals("#3FA7D6", hexOf(0x3FA7D6))
    }

    @Test
    fun hueSaturationAndBrightnessRoundTrip() {
        for (rgb in listOf(0x3FA7D6, 0xFF0000, 0x00FF00, 0x808080, 0x000000, 0xFFFFFF)) {
            val (h, s, v) = rgbToHsv(rgb)
            assertEquals(hexOf(rgb), hexOf(hsvToRgb(h, s, v)))
        }
        assertEquals(0xFF0000, hsvToRgb(0f, 1f, 1f))
    }

    @Test
    fun aVeryDarkColorIsLightenedInDarkMode() {
        assertTrue(lightnessOf(themeAdjusted(0x101040, isDarkTheme = true)) >= 0.45f)
    }

    @Test
    fun aVeryLightColorIsDarkenedInLightMode() {
        assertTrue(lightnessOf(themeAdjusted(0xF0F0B0, isDarkTheme = false)) <= 0.62f)
    }

    @Test
    fun anOrdinaryColorIsUsedAsPicked() {
        assertEquals(hexOf(0x3FA7D6), hexOf(themeAdjusted(0x3FA7D6, isDarkTheme = true)))
        assertEquals(hexOf(0x3FA7D6), hexOf(themeAdjusted(0x3FA7D6, isDarkTheme = false)))
    }
}
