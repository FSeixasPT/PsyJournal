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

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.CustomColor
import com.isaakhanimann.journal.data.room.experiences.entities.hexOf
import com.isaakhanimann.journal.data.room.experiences.entities.hsvToRgb
import com.isaakhanimann.journal.data.room.experiences.entities.parseHexColor
import com.isaakhanimann.journal.data.room.experiences.entities.rgbToHsv

// hue, saturation and brightness sliders with a hex field, starting from the current color
@Composable
fun CustomColorDialog(
    initialColor: AdaptiveColor,
    dismiss: () -> Unit,
    onPick: (CustomColor) -> Unit,
) {
    val initialRgb = remember(initialColor) {
        (initialColor as? CustomColor)?.rgb ?: (initialColor.getComposeColor(isDarkTheme = false).toArgb() and 0xFFFFFF)
    }
    val (initialHue, initialSaturation, initialValue) = remember(initialRgb) { rgbToHsv(initialRgb) }
    var hue by remember { mutableFloatStateOf(initialHue) }
    var saturation by remember { mutableFloatStateOf(initialSaturation) }
    var brightness by remember { mutableFloatStateOf(initialValue) }
    val rgb = hsvToRgb(hue, saturation, brightness)
    var hexText by remember { mutableStateOf(hexOf(initialRgb)) }
    fun setFromSliders(h: Float = hue, s: Float = saturation, v: Float = brightness) {
        hue = h
        saturation = s
        brightness = v
        hexText = hexOf(hsvToRgb(h, s, v))
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(text = "Custom color", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ColorPreview(color = CustomColor(rgb), isDarkTheme = false, modifier = Modifier.weight(1f))
                    ColorPreview(color = CustomColor(rgb), isDarkTheme = true, modifier = Modifier.weight(1f))
                }
                Text(text = "Hue", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Slider(value = hue, onValueChange = { setFromSliders(h = it) }, valueRange = 0f..359.9f)
                Text(text = "Saturation", style = MaterialTheme.typography.labelLarge)
                Slider(value = saturation, onValueChange = { setFromSliders(s = it) })
                Text(text = "Brightness", style = MaterialTheme.typography.labelLarge)
                Slider(value = brightness, onValueChange = { setFromSliders(v = it) })
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        parseHexColor(text)?.let { parsed ->
                            val (h, s, v) = rgbToHsv(parsed)
                            hue = h
                            saturation = s
                            brightness = v
                        }
                    },
                    label = { Text("Hex") },
                    isError = parseHexColor(hexText) == null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(CustomColor(rgb))
                dismiss()
            }) {
                Text("Use color")
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text("Cancel")
            }
        }
    )
}

// the color as it will look on the light or the dark background
@Composable
private fun ColorPreview(color: AdaptiveColor, isDarkTheme: Boolean, modifier: Modifier = Modifier) {
    val background = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF5F5F5)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.getComposeColor(isDarkTheme))
                .align(Alignment.TopCenter)
        )
        Text(
            text = if (isDarkTheme) "Dark" else "Light",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
