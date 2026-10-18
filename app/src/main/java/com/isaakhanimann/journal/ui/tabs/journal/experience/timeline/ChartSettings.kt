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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.ui.tabs.settings.SettingsButton
import com.isaakhanimann.journal.ui.tabs.settings.SwitchWithInfoRow
import com.isaakhanimann.journal.ui.theme.horizontalPadding

// the settings that change how the effect timeline is drawn, in every experience
data class ChartSettings(
    val areSubstanceHeightsIndependent: Boolean = false,
    val areRoutesMerged: Boolean = true,
    val isDurationRangeShown: Boolean = false,
    val isChartPinned: Boolean = false,
)

// shared with the settings screen, which keeps these switches too
internal val independentSubstanceHeightsInfo = """
    Enable this setting if you want the timeline of different substances and routes of administration (roas) to be independent. Then ingestions of different substances and roas will always take the full height of the timeline.

    If this setting is disabled then timelines of different substances have a height relative to each other. In that case the average of the common dose is used as the point to compare it to.
    E.g. if the oral average common dose of MDMA is 100mg and the average common dose of insufflated MDMA is 50mg then the timeline for 100mg of oral MDMA is the same height as for 50mg of insufflated MDMA.
    This is also applied across substances. E.g. if the common dose of oral 2C-B is 20mg then the timeline of 40mg oral 2C-B will be twice as high as 100mg of oral MDMA.
""".trimIndent()

internal const val MERGE_ROUTES_INFO = "When enabled, ingestions of the same substance through different routes (e.g. insufflated and oral ketamine) are shown as one combined line. Each route keeps its own onset and duration, and doses are made comparable using each route's common dose range, so the line shows the combined effect rather than the combined amount. Routes without dose ranges in the database are always shown separately."

internal const val CHART_PINNED_INFO = "Keeps the effect timeline and its current effects at the top of the experience, while the ingestions, notes and ratings scroll below it. In landscape or on short windows, such as split screen, the chart scrolls as usual, since it would leave too little room."

internal const val DURATION_RANGE_INFO = "Shades the area between the shortest and the longest durations known for each route, so you can see how much the timing can vary. The line itself is the estimate for your dose. Only timelines with complete durations have a range."

@Composable
fun ChartSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.Settings, contentDescription = "Chart settings")
    }
}

// a sheet rather than a screen, so the chart above it changes as the switches do
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartSettingsSheet(
    settings: ChartSettings,
    onChange: (ChartSettings) -> Unit,
    onExplain: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ChartSettingsContent(
            settings = settings,
            onChange = onChange,
            onExplain = {
                onDismiss()
                onExplain()
            }
        )
    }
}

@Composable
fun ChartSettingsContent(
    settings: ChartSettings,
    onChange: (ChartSettings) -> Unit,
    onExplain: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 15.dp)) {
        Text(
            text = "Chart settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
        Text(
            text = "Applies to all experiences",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SwitchWithInfoRow(
            title = "Independent substance heights",
            info = independentSubstanceHeightsInfo,
            checked = settings.areSubstanceHeightsIndependent,
            onCheckedChange = { onChange(settings.copy(areSubstanceHeightsIndependent = it)) }
        )
        HorizontalDivider()
        SwitchWithInfoRow(
            title = "Merge routes of same substance",
            info = MERGE_ROUTES_INFO,
            checked = settings.areRoutesMerged,
            onCheckedChange = { onChange(settings.copy(areRoutesMerged = it)) }
        )
        HorizontalDivider()
        SwitchWithInfoRow(
            title = "Show duration range",
            info = DURATION_RANGE_INFO,
            checked = settings.isDurationRangeShown,
            onCheckedChange = { onChange(settings.copy(isDurationRangeShown = it)) }
        )
        HorizontalDivider()
        SwitchWithInfoRow(
            title = "Keep chart in view",
            info = CHART_PINNED_INFO,
            checked = settings.isChartPinned,
            onCheckedChange = { onChange(settings.copy(isChartPinned = it)) }
        )
        HorizontalDivider()
        SettingsButton(imageVector = Icons.Outlined.Timeline, text = "How to read the chart", onClick = onExplain)
    }
}
