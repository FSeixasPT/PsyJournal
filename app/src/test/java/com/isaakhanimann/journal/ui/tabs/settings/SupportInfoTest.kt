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

package com.isaakhanimann.journal.ui.tabs.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportInfoTest {
    @Test
    fun listsAppAndroidAndDevice() {
        val text = supportInfoText(
            versionName = "11.11", versionCode = 62, isDebug = false,
            androidRelease = "15", sdkInt = 35, manufacturer = "Google", model = "Pixel 8"
        )
        assertEquals("PsyJournal 11.11 (62), release\nAndroid 15 (API 35)\nDevice: Google Pixel 8", text)
    }

    @Test
    fun theManufacturerIsNotRepeatedAndIsCapitalized() {
        val text = supportInfoText(
            versionName = "11.11", versionCode = 62, isDebug = true,
            androidRelease = "13", sdkInt = 33, manufacturer = "samsung", model = "samsung SM-S911B"
        )
        assertEquals("PsyJournal 11.11 (62), debug\nAndroid 13 (API 33)\nDevice: Samsung SM-S911B", text)
    }
}
