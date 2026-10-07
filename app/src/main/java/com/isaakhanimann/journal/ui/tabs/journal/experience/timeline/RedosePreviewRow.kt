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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import com.isaakhanimann.journal.ui.utils.getShortTimeText

/**
 * Chips to preview taking a substance again now. The preview is shown only while a chip is held.
 * Edited doses are kept until the screen is left and are never saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RedosePreviewRow(
    options: List<RedoseOption>,
    // peak of the currently previewed redose, shown below the chips so they don't move while held
    peak: TimelinePeak?,
    onPreviewChange: (RedoseOption?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    var editedDoses by remember { mutableStateOf(mapOf<Pair<String, AdministrationRoute>, Double>()) }
    var optionBeingEdited by remember { mutableStateOf<RedoseOption?>(null) }
    val optionsWithEditedDoses = options.map { option ->
        editedDoses[Pair(option.substanceName, option.route)]?.let { option.copy(dose = it) } ?: option
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Hold to preview taking now:",
            style = MaterialTheme.typography.labelMedium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            optionsWithEditedDoses.forEach { option ->
                RedoseChip(
                    option = option,
                    onPreviewChange = onPreviewChange,
                    onEdit = { optionBeingEdited = option }
                )
            }
        }
        // the line is always laid out so that nothing moves when it appears
        Text(
            text = peak?.let {
                "Peak after redose: ≈ ${it.doseEquivalent.toReadableString()} ${it.units} equivalent at ${it.time.getShortTimeText()}"
            } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            minLines = 1
        )
    }
    optionBeingEdited?.let { option ->
        EditDoseDialog(
            option = option,
            onDismiss = { optionBeingEdited = null },
            onConfirm = { newDose ->
                editedDoses = editedDoses + (Pair(option.substanceName, option.route) to newDose)
                optionBeingEdited = null
            }
        )
    }
}

@Composable
private fun RedoseChip(
    option: RedoseOption,
    onPreviewChange: (RedoseOption?) -> Unit,
    onEdit: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val currentOption by rememberUpdatedState(option)
    val currentOnPreviewChange by rememberUpdatedState(onPreviewChange)
    var isPressed by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = if (isPressed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                currentOnPreviewChange(currentOption)
                                tryAwaitRelease()
                                isPressed = false
                                currentOnPreviewChange(null)
                            }
                        )
                    }
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(option.color.getComposeColor(isDarkTheme), CircleShape)
                )
                Text(
                    text = "${option.substanceName} · ${option.dose.toReadableString()} ${option.units}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Change dose to preview",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EditDoseDialog(
    option: RedoseOption,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(option.dose.toReadableString()) }
    val dose = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dose to preview") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("${option.substanceName} (${option.route.displayText.lowercase()})") },
                suffix = { Text(option.units) },
                isError = dose == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(onClick = { dose?.let(onConfirm) }, enabled = dose != null) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
