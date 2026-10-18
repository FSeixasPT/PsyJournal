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
