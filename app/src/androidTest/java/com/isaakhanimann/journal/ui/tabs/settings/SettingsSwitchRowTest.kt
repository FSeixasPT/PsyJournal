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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.theme.JournalTheme
import androidx.compose.ui.unit.width
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSwitchRowTest {
    @get:Rule
    val rule = createComposeRule()

    // the settings card's width on a common 360 dp phone
    private val cardWidth = 340.dp

    @Test
    fun theLongestSettingKeepsItsSwitchInsideTheCard() {
        rule.setContent {
            JournalTheme {
                Box(modifier = Modifier.width(cardWidth)) {
                    SwitchWithInfoRow(
                        title = "Live notification details on lock screen",
                        info = "",
                        checked = false,
                        onCheckedChange = {}
                    )
                }
            }
        }
        val switch = rule.onNode(isToggleable()).getBoundsInRoot()
        assertTrue("switch at $switch", switch.right <= cardWidth + 0.5.dp)
        // Material's switch track is 52 dp wide; less means it was squeezed by the label
        assertTrue("switch squeezed to ${switch.right - switch.left}", switch.right - switch.left >= 52.dp)
    }
}
