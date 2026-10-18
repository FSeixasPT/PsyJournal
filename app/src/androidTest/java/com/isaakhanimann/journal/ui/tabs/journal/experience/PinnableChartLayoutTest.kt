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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.theme.JournalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinnableChartLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showLayout(isPinned: Boolean) {
        rule.setContent {
            JournalTheme {
                Box(modifier = Modifier.height(600.dp)) {
                    PinnableChartLayout(
                        isPinned = isPinned,
                        chart = { Box(modifier = Modifier.fillMaxWidth().height(250.dp).testTag("chart")) },
                        content = {
                            // far taller than the room left below the chart
                            (1..40).forEach { Text(text = "Ingestion $it", modifier = Modifier.height(50.dp)) }
                        }
                    )
                }
            }
        }
    }

    // swipes on whatever scrolls: the whole layout, or only the part below a pinned chart
    private fun scrollDown() {
        rule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        rule.waitForIdle()
    }

    @Test
    fun pinnedChartStaysWhileTheIngestionsScroll() {
        showLayout(isPinned = true)
        val chartBefore = rule.onNodeWithTag("chart").getBoundsInRoot()
        val firstBefore = rule.onNodeWithText("Ingestion 1").getBoundsInRoot()
        scrollDown()
        assertEquals(chartBefore, rule.onNodeWithTag("chart").getBoundsInRoot())
        val firstAfter = rule.onNodeWithText("Ingestion 1").getBoundsInRoot()
        assertTrue("ingestions didn't scroll: $firstBefore -> $firstAfter", firstAfter.top < firstBefore.top)
    }

    @Test
    fun unpinnedChartScrollsAwayWithTheIngestions() {
        showLayout(isPinned = false)
        val chartBefore = rule.onNodeWithTag("chart").getBoundsInRoot()
        scrollDown()
        val chartAfter = rule.onNodeWithTag("chart").getBoundsInRoot()
        // a chart scrolled fully out of view has empty bounds, so this compares its bottom edge
        assertTrue("chart stayed: $chartBefore -> $chartAfter", chartAfter.bottom < chartBefore.bottom)
    }
}
