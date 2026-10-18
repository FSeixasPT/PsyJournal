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

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.theme.JournalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartSettingsSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val changes = mutableListOf<ChartSettings>()
    private var explanationsOpened = 0

    private fun showContent(settings: ChartSettings) {
        rule.setContent {
            JournalTheme {
                ChartSettingsContent(
                    settings = settings,
                    onChange = { changes.add(it) },
                    onExplain = { explanationsOpened++ }
                )
            }
        }
    }

    // the switches in the order of the rows: heights, merged routes, duration range, pinned chart
    private fun switch(index: Int) = rule.onAllNodes(isToggleable())[index]

    @Test
    fun switchesShowTheCurrentSettings() {
        showContent(ChartSettings(areSubstanceHeightsIndependent = true, areRoutesMerged = false, isDurationRangeShown = true, isChartPinned = false))
        rule.onNodeWithText("Independent substance heights").assertExists()
        rule.onNodeWithText("Merge routes of same substance").assertExists()
        rule.onNodeWithText("Show duration range").assertExists()
        rule.onNodeWithText("Keep chart in view").assertExists()
        switch(0).assertIsOn()
        switch(1).assertIsOff()
        switch(2).assertIsOn()
        switch(3).assertIsOff()
    }

    @Test
    fun eachSwitchChangesOnlyItsOwnSetting() {
        val settings = ChartSettings(areSubstanceHeightsIndependent = false, areRoutesMerged = true, isDurationRangeShown = false, isChartPinned = false)
        showContent(settings)
        switch(0).performClick()
        switch(1).performClick()
        switch(2).performClick()
        switch(3).performClick()
        assertEquals(
            listOf(
                settings.copy(areSubstanceHeightsIndependent = true),
                settings.copy(areRoutesMerged = false),
                settings.copy(isDurationRangeShown = true),
                settings.copy(isChartPinned = true),
            ),
            changes
        )
    }

    @Test
    fun saysTheSettingsApplyToAllExperiences() {
        showContent(ChartSettings())
        rule.onNodeWithText("Applies to all experiences").assertExists()
    }

    @Test
    fun howToReadTheChartOpensTheExplanation() {
        showContent(ChartSettings())
        rule.onNodeWithText("How to read the chart").performClick()
        assertEquals(1, explanationsOpened)
        assertTrue(changes.isEmpty())
    }
}
