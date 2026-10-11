/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.CustomColor
import com.isaakhanimann.journal.data.room.experiences.entities.colorsToOffer
import com.isaakhanimann.journal.data.room.experiences.entities.hexOf

@Preview
@Composable
fun ColorPickerPreview() {
    val alreadyUsedColors = listOf(AdaptiveColor.BLUE, AdaptiveColor.PINK)
    val otherColors = AdaptiveColor.entries.filter { color ->
        !alreadyUsedColors.contains(color)
    }
    ColorPicker(
        selectedColor = AdaptiveColor.BLUE,
        onChangeOfColor = {},
        alreadyUsedColors,
        otherColors
    )
}

@Composable
fun ColorPicker(
    selectedColor: AdaptiveColor,
    onChangeOfColor: (AdaptiveColor) -> Unit,
    alreadyUsedColors: List<AdaptiveColor>,
    otherColors: List<AdaptiveColor>,
    // shows a remove button with this explanation when not null; onRemove null keeps it disabled
    removeExplanation: String? = null,
    onRemove: (() -> Unit)? = null
) {
    val isDarkTheme = isSystemInDarkTheme()
    var isColorPickerVisible by remember {
        mutableStateOf(false)
    }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = selectedColor.getComposeColor(isDarkTheme),
        modifier = Modifier
            .size(50.dp)
            .clip(shape)
            .clickable(onClick = { isColorPickerVisible = true })
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit color",
            modifier = Modifier.padding(15.dp),
        )
    }
    if (isColorPickerVisible) {
        ColorDialog(
            dismiss = { isColorPickerVisible = false },
            onChangeOfColor = onChangeOfColor,
            selectedColor = selectedColor,
            alreadyUsedColors = alreadyUsedColors,
            otherColors = otherColors,
            removeExplanation = removeExplanation,
            onRemove = onRemove
        )
    }
}

@Preview
@Composable
fun ColorDialogPreview() {
    val alreadyUsedColors = listOf(AdaptiveColor.BLUE, AdaptiveColor.PINK, AdaptiveColor.AUBURN)
    val otherColors = AdaptiveColor.entries.filter { color ->
        !alreadyUsedColors.contains(color)
    }
    ColorDialog(
        dismiss = {},
        onChangeOfColor = {},
        alreadyUsedColors = alreadyUsedColors,
        otherColors = otherColors
    )
}

@Composable
fun ColorDialog(
    dismiss: () -> Unit,
    onChangeOfColor: (AdaptiveColor) -> Unit,
    alreadyUsedColors: List<AdaptiveColor>,
    otherColors: List<AdaptiveColor>,
    removeExplanation: String? = null,
    onRemove: (() -> Unit)? = null,
    selectedColor: AdaptiveColor = AdaptiveColor.BLUE
) {
    var isPickingCustomColor by remember { mutableStateOf(false) }
    val offeredColors = colorsToOffer(otherColors)
    if (isPickingCustomColor) {
        CustomColorDialog(
            initialColor = selectedColor,
            dismiss = dismiss,
            onPick = onChangeOfColor
        )
        return
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(text = "Pick a color", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (removeExplanation != null) {
                    Text(text = removeExplanation, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedButton(onClick = { isPickingCustomColor = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Palette, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Custom color…")
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (offeredColors.isEmpty()) {
                    Text(text = "No unused colors")
                } else {
                    Text(text = "Not yet used")
                    Spacer(modifier = Modifier.height(2.dp))
                    CircleColorButtons(colors = offeredColors) {
                        onChangeOfColor(it)
                        dismiss()
                    }
                }
                if (alreadyUsedColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(text = "Already used")
                    Spacer(modifier = Modifier.height(2.dp))
                    CircleColorButtons(colors = alreadyUsedColors) {
                        onChangeOfColor(it)
                        dismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                // alone it spans the dialog; next to Remove it takes its own width
                modifier = if (removeExplanation == null) Modifier.fillMaxWidth() else Modifier,
                onClick = dismiss
            ) {
                Text("Cancel", textAlign = TextAlign.Center)
            }
        },
        dismissButton = if (removeExplanation == null) null else {
            {
                TextButton(
                    onClick = {
                        onRemove?.invoke()
                        dismiss()
                    },
                    enabled = onRemove != null
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove")
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CircleColorButtons(
    colors: List<AdaptiveColor>,
    onTapOnColor: (AdaptiveColor) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val spacing = 5.dp
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        val shape = RoundedCornerShape(8.dp)
        colors.forEach { color ->
            Surface(
                shape = shape,
                color = color.getComposeColor(isDarkTheme),
                modifier = Modifier
                    .size(48.dp)
                    .clip(shape)
                    .clickable(
                        onClick = { onTapOnColor(color) }
                    )
                    .semantics {
                        contentDescription = if (color is CustomColor) "Custom ${hexOf(color.rgb)}" else color.name
                    }
            ) {}
        }
    }

}