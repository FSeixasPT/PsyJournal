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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import java.time.Instant

data class RedosePreviewState(
    // the model to draw: with the held or pinned redose, or the real one
    val displayedModel: AllTimelinesModel,
    // the pinned redose if it still applies to this timeline
    val pinnedOption: RedoseOption?,
    val isRedosePossibleNow: Boolean,
    val now: Instant
)

@Composable
fun rememberCurrentTime(): Instant {
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
    return currentTime
}

/**
 * The held chip wins over the pinned one. A pin that no longer applies to this timeline is reported once
 * with [onPinNoLongerApplies] so that it can be removed.
 */
@Composable
fun rememberRedosePreviewState(
    realModel: AllTimelinesModel,
    heldOption: RedoseOption?,
    pin: PinnedRedose?,
    onPinNoLongerApplies: () -> Unit,
): RedosePreviewState {
    val now = rememberCurrentTime()
    val pinnedOption = pin?.let { realModel.resolvePinnedRedose(it, now) }
    val currentOnPinNoLongerApplies by rememberUpdatedState(onPinNoLongerApplies)
    val isPinInvalid = pin != null && pinnedOption == null
    LaunchedEffect(isPinInvalid) {
        if (isPinInvalid) currentOnPinNoLongerApplies()
    }
    val option = heldOption ?: pinnedOption
    val displayedModel = remember(realModel, option, now) {
        option?.let { realModel.withPlannedIngestion(it, dose = it.dose, time = now) } ?: realModel
    }
    val timelineEnd = realModel.startTime.plusSeconds(realModel.widthInSeconds.toLong())
    return RedosePreviewState(
        displayedModel = displayedModel,
        pinnedOption = pinnedOption,
        isRedosePossibleNow = now < timelineEnd,
        now = now
    )
}
