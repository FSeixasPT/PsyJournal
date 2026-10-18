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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.rememberLiveNotificationToggleState
import com.isaakhanimann.journal.ui.theme.JournalTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EffectTimelineHeaderTest {
    @get:Rule
    val rule = createComposeRule()

    private val cardWidth = 340.dp

    private var settingsOpened = 0

    private fun showHeader(fontScale: Float, isLive: Boolean = true) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JournalTheme {
                    // the card's width on a common 360 dp phone, inside the screen's 10 dp margins
                    Box(modifier = Modifier.width(cardWidth)) {
                        EffectTimelineHeader(
                            isLiveNotificationAvailable = true,
                            isLive = isLive,
                            liveToggleState = rememberLiveNotificationToggleState {},
                            onSettings = { settingsOpened++ },
                            onExpand = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    fun titleFitsOnOneLineOnACommonPhone() {
        showHeader(fontScale = 1f, isLive = false)
        assertTitleOnOneLine()
    }

    // the live button is widest when on, with its check mark
    @Test
    fun titleFitsOnOneLineOnACommonPhoneWithTheLiveNotificationOn() {
        showHeader(fontScale = 1f, isLive = true)
        assertTitleOnOneLine()
    }

    private fun assertTitleOnOneLine() {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Effect timeline").fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val title = rule.onNodeWithText("Effect timeline").getBoundsInRoot()
        val live = rule.onNodeWithText("Live notification").getBoundsInRoot()
        val settings = rule.onNodeWithContentDescription("Chart settings").getBoundsInRoot()
        val expand = rule.onNodeWithContentDescription("Expand timeline").getBoundsInRoot()
        assertEquals(
            "title ${title.width} (one line: ${with(rule.density) { layouts.single().multiParagraph.intrinsics.maxIntrinsicWidth.toDp() }}), live ${live.width}, settings ${settings.width}, expand ${expand.width}",
            1, layouts.single().lineCount
        )
    }

    @Test
    fun buttonsShareTheTitlesLineOnACommonPhone() {
        showHeader(fontScale = 1f)
        val title = rule.onNodeWithText("Effect timeline").getBoundsInRoot()
        val expand = rule.onNodeWithContentDescription("Expand timeline").getBoundsInRoot()
        assertTrue("expand at ${expand.top}, title ends at ${title.bottom}", expand.top < title.bottom)
    }

    @Test
    fun buttonsStayVisibleWithALargeFont() {
        showHeader(fontScale = 2f)
        listOf(
            rule.onNodeWithText("Live notification").getBoundsInRoot(),
            rule.onNodeWithContentDescription("Chart settings").getBoundsInRoot(),
            rule.onNodeWithContentDescription("Expand timeline").getBoundsInRoot(),
        ).forEach { bounds ->
            assertTrue("squeezed: $bounds", bounds.width > 20.dp)
            assertTrue("outside the card: $bounds", bounds.right <= cardWidth + 0.5.dp) // allows for rounding to pixels
        }
    }

    @Test
    fun theGearOpensTheChartSettingsInsteadOfAnInfoButton() {
        showHeader(fontScale = 1f)
        rule.onNodeWithText("Info").assertDoesNotExist()
        rule.onNodeWithContentDescription("Chart settings").performClick()
        assertEquals(1, settingsOpened)
    }
}
