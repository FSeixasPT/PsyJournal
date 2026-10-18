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

import com.isaakhanimann.journal.ui.theme.horizontalPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ElevatedCard
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// below this the pinned chart would leave too little room to scroll, e.g. in landscape or split screen
private const val MIN_PINNED_WINDOW_HEIGHT_DP = 600

fun isChartPinned(isSettingOn: Boolean, windowHeightDp: Int) =
    isSettingOn && windowHeightDp >= MIN_PINNED_WINDOW_HEIGHT_DP

// the chart either scrolls away with the content below it, or stays at the top while only the content scrolls
@Composable
fun PinnableChartLayout(
    isPinned: Boolean,
    chart: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isPinned) {
        Column(modifier = modifier) {
            chart()
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                content()
            }
        }
    } else {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).then(modifier)) {
            chart()
            content()
        }
    }
}

// what's below the chart in its card, such as the planned dose, when the chart is pinned without it
@Composable
fun PinnedExtrasCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content
        )
    }
}
