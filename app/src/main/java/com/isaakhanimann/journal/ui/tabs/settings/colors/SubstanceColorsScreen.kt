/*
 * Copyright (c) 2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.settings.colors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.ColorPicker
import com.isaakhanimann.journal.ui.theme.horizontalPadding

@Composable
fun SubstanceColorsScreen(
    viewModel: SubstanceColorsViewModel = hiltViewModel(),
) {
    SubstanceColorsScreenContent(
        substanceCompanions = viewModel.substanceCompanionsFlow.collectAsState().value,
        updateColor = viewModel::updateColor,
        updateIsPartOfTimeline = viewModel::updateIsPartOfTimeline,
        alreadyUsedColors = viewModel.alreadyUsedColorsFlow.collectAsState().value,
        otherColors = viewModel.otherColorsFlow.collectAsState().value,
        allSubstanceNames = viewModel.allSubstanceNamesFlow.collectAsState().value,
        addSubstance = viewModel::addSubstance,
        loggedSubstanceNames = viewModel.loggedSubstanceNamesFlow.collectAsState().value,
        removeSubstance = viewModel::removeSubstance
    )
}

@Preview
@Composable
fun SubstanceColorsScreenPreview() {
    val alreadyUsedColors = listOf(AdaptiveColor.OLIVE, AdaptiveColor.AUBURN)
    val otherColors = AdaptiveColor.entries.filter { !alreadyUsedColors.contains(it) }
    SubstanceColorsScreenContent(
        substanceCompanions = listOf(
            SubstanceCompanion(substanceName = "Substance 1", color = AdaptiveColor.AUBURN),
            SubstanceCompanion(substanceName = "Substance 2", color = AdaptiveColor.BLUE, isPartOfTimeline = false),
            SubstanceCompanion(
                substanceName = "Substance with longer 3",
                color = AdaptiveColor.YELLOW
            ),
            SubstanceCompanion(substanceName = "Substance 4", color = AdaptiveColor.OLIVE),
        ),
        updateColor = { _, _ -> },
        alreadyUsedColors = alreadyUsedColors,
        otherColors = otherColors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstanceColorsScreenContent(
    substanceCompanions: List<SubstanceCompanion>,
    updateColor: (color: AdaptiveColor, substanceName: String) -> Unit,
    updateIsPartOfTimeline: (isPartOfTimeline: Boolean, substanceName: String) -> Unit = { _, _ -> },
    alreadyUsedColors: List<AdaptiveColor>,
    otherColors: List<AdaptiveColor>,
    allSubstanceNames: List<String> = emptyList(),
    addSubstance: (substanceName: String) -> Unit = {},
    loggedSubstanceNames: Set<String> = emptySet(),
    removeSubstance: (substanceName: String) -> Unit = {}
) {
    var isAddingSubstance by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Substance colors and visibility") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { isAddingSubstance = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add substance")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .fillMaxSize(),
            // room for the add button below the last substance
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                Text(
                    text = "Turn a substance off to hide it from the timelines of all experiences. Its ingestions stay in your journal. Use + to set up a substance you haven't logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(substanceCompanions) { substanceCompanion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorPicker(
                        selectedColor = substanceCompanion.color,
                        onChangeOfColor = {
                            updateColor(it, substanceCompanion.substanceName)
                        },
                        alreadyUsedColors = alreadyUsedColors,
                        otherColors = otherColors,
                        removeExplanation = removeColorExplanation(substanceCompanion.substanceName, loggedSubstanceNames),
                        onRemove = if (canRemoveColor(substanceCompanion.substanceName, loggedSubstanceNames)) {
                            { removeSubstance(substanceCompanion.substanceName) }
                        } else {
                            null
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = substanceCompanion.substanceName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!substanceCompanion.isPartOfTimeline) {
                            Text(text = "Hidden from timelines", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = substanceCompanion.isPartOfTimeline,
                        onCheckedChange = { updateIsPartOfTimeline(it, substanceCompanion.substanceName) },
                        modifier = Modifier.semantics {
                            contentDescription = "Show ${substanceCompanion.substanceName} in timelines"
                        }
                    )
                }
                HorizontalDivider()
            }
        }
    }
    if (isAddingSubstance) {
        val configuredNames = substanceCompanions.map { it.substanceName }.toSet()
        SubstancePickerSheet(
            substanceNames = allSubstanceNames.filter { it !in configuredNames },
            onDismiss = { isAddingSubstance = false },
            onPick = {
                addSubstance(it)
                isAddingSubstance = false
            }
        )
    }
}
