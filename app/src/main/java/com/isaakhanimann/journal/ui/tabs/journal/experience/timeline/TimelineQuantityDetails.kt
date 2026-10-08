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

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Shows how strong each substance is at [selectedTime], or at the current time if nothing is selected.
 */
@Composable
fun TimelineQuantityDetails(
    model: AllTimelinesModel,
    selectedTime: Instant?,
    modifier: Modifier = Modifier,
) {
    var currentTime by remember { mutableStateOf(Instant.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        currentTime = Instant.now()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            currentTime = Instant.now()
        }
    }
    val time = selectedTime ?: currentTime
    val endTime = model.startTime.plusSeconds(model.widthInSeconds.toLong())
    if (time < model.startTime || time > endTime) return
    val details = model.getQuantityDetails(time)
    if (details.isEmpty()) return
    val isDarkTheme = isSystemInDarkTheme()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        details.forEach { detail ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(detail.color.getComposeColor(isDarkTheme), CircleShape)
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = detail.toText(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (detail.doseEquivalents.size > 1) {
                        Text(
                            text = detail.doseEquivalents.joinToString(separator = " / ") {
                                "≈ ${it.dose.toReadableString()} ${it.units} ${it.route.displayText.lowercase()}"
                            } + " equivalent",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun TimelineQuantityDetail.toText(): String {
    val percent = (fractionOfPeak * 100).roundToInt()
    val singleEquivalent = doseEquivalents.singleOrNull()
    val dose = if (singleEquivalent != null) {
        " · ≈ ${singleEquivalent.dose.toReadableString()} ${singleEquivalent.units} equivalent"
    } else {
        ""
    }
    return "$substanceName · $percent% of peak$dose"
}
