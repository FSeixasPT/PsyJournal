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
package com.isaakhanimann.journal.ui.tabs.safer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.theme.JournalTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestingServiceItemTest {
    @get:Rule
    val rule = createComposeRule()

    private fun overflows(text: String): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().hasVisualOverflow
    }

    @Test
    fun aLongCityLeavesRoomForTheName() {
        rule.setContent {
            JournalTheme {
                // the card's width on a common 360 dp phone
                Box(modifier = Modifier.width(340.dp)) {
                    TestingServiceItem(name = "Ai Laket", city = "Vitoria-Gasteiz, Pamplona, Bilbao and Donostia", url = "")
                }
            }
        }
        assertFalse("name cut off", overflows("Ai Laket"))
        assertFalse("city cut off", overflows("Vitoria-Gasteiz, Pamplona, Bilbao and Donostia"))
    }
}
