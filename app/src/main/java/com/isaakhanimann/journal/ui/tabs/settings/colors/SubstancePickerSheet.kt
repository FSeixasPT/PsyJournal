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

package com.isaakhanimann.journal.ui.tabs.settings.colors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.ui.theme.horizontalPadding

// a searchable list of substance names in a bottom sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstancePickerSheet(
    substanceNames: List<String>,
    onDismiss: () -> Unit,
    onPick: (substanceName: String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val names = remember(substanceNames, query) {
        substancesToAdd(substanceNames, configured = emptyList(), query = query)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search substances") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(names) { name ->
                ListItem(
                    headlineContent = { Text(name) },
                    modifier = Modifier.clickable { onPick(name) }
                )
            }
            if (names.isEmpty()) {
                item {
                    Text(
                        text = "No substances found",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 16.dp)
                    )
                }
            }
        }
    }
}
