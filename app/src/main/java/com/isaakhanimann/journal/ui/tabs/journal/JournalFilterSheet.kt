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

package com.isaakhanimann.journal.ui.tabs.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// changes apply straight away, so the list behind the sheet shows what the filter does
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalFilterSheet(
    filter: JournalFilter,
    onChange: (JournalFilter) -> Unit,
    // the user's own substances, most taken first
    substances: List<String>,
    // the slider's right end in hours, meaning "or longer"
    sliderMaxHours: Float,
    onDismiss: () -> Unit,
) {
    // fully open, so Clear and Done aren't cut off at the bottom
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Filter experiences", style = MaterialTheme.typography.titleLarge)

            Text("Substances you took", style = MaterialTheme.typography.titleSmall)
            Text("Only experiences with all of the chosen ones", style = MaterialTheme.typography.bodySmall)
            if (substances.isEmpty()) {
                Text("No substances logged yet", style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                substances.forEach { name ->
                    val isSelected = name in filter.substances
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onChange(filter.copy(substances = if (isSelected) filter.substances - name else filter.substances + name))
                        },
                        label = { Text(name) }
                    )
                }
            }

            Text("Number of substances", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter.substanceCounts.isEmpty(),
                    onClick = { onChange(filter.copy(substanceCounts = emptySet())) },
                    label = { Text("Any") }
                )
                // several can be chosen, e.g. 1 or 2
                SubstanceCount.entries.forEach { count ->
                    val isSelected = count in filter.substanceCounts
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onChange(filter.copy(substanceCounts = if (isSelected) filter.substanceCounts - count else filter.substanceCounts + count))
                        },
                        label = { Text(count.label) }
                    )
                }
            }

            Text("Duration", style = MaterialTheme.typography.titleSmall)
            val from = filter.minHours ?: 0f
            val to = filter.maxHours ?: sliderMaxHours
            Text(
                when {
                    filter.minHours == null && filter.maxHours == null -> "Any duration"
                    filter.maxHours == null -> "At least ${hoursText(from)} h"
                    filter.minHours == null -> "Up to ${hoursText(to)} h"
                    else -> "${hoursText(from)}–${hoursText(to)} h"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            RangeSlider(
                value = from..to,
                onValueChange = { range ->
                    // half-hour steps; the ends of the slider mean no limit
                    val start = (range.start * 2).roundToInt() / 2f
                    val end = (range.endInclusive * 2).roundToInt() / 2f
                    onChange(
                        filter.copy(
                            minHours = start.takeIf { it > 0f },
                            maxHours = end.takeIf { it < sliderMaxHours }
                        )
                    )
                },
                valueRange = 0f..sliderMaxHours,
                steps = (sliderMaxHours * 2).roundToInt() - 1,
                // the ends sit near the screen edges, where a drag would otherwise be the back gesture
                modifier = Modifier.systemGestureExclusion(),
            )
            Text("Uses each experience's estimated duration, like the clock in the list", style = MaterialTheme.typography.bodySmall)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                TextButton(onClick = { onChange(JournalFilter()) }, enabled = filter.isActive) { Text("Clear") }
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}
