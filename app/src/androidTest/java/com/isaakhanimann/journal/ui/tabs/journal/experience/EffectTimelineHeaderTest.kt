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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.rememberLiveNotificationToggleState
import com.isaakhanimann.journal.ui.theme.JournalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EffectTimelineHeaderTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showHeader(fontScale: Float) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JournalTheme {
                    // a common phone width
                    Box(modifier = Modifier.width(360.dp)) {
                        EffectTimelineHeader(
                            isLiveNotificationAvailable = true,
                            isLive = true,
                            liveToggleState = rememberLiveNotificationToggleState {},
                            onInfo = {},
                            onExpand = {}
                        )
                    }
                }
            }
        }
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
            rule.onNodeWithText("Info").getBoundsInRoot(),
            rule.onNodeWithContentDescription("Expand timeline").getBoundsInRoot(),
        ).forEach { bounds ->
            assertTrue("squeezed: $bounds", bounds.width > 20.dp)
            assertTrue("outside the card: $bounds", bounds.right <= 360.dp)
        }
    }
}
