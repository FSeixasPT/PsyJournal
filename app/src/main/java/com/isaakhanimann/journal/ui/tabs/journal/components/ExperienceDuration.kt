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

package com.isaakhanimann.journal.ui.tabs.journal.components

import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullDurationRange
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullDurations
import java.time.Duration
import kotlin.math.roundToInt

// from the first ingestion until the last effect ends, as estimated by the timeline;
// null if an ingestion's end can't be estimated, because the duration would be too short
fun experienceDuration(lines: List<DataForOneEffectLine>): Duration? {
    if (lines.isEmpty() || !lines.all { it.hasKnownEnd() }) return null
    val model = AllTimelinesModel(
        dataForLines = lines,
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false
    )
    val endInSeconds = model.groupDrawables.maxOf { it.endOfLineRelativeToStartInSeconds }
    return Duration.ofSeconds(endInSeconds.toLong())
}

private fun DataForOneEffectLine.hasKnownEnd(): Boolean {
    val duration = roaDuration ?: return false
    return duration.toFullDurations() != null || duration.total?.toFullDurationRange() != null
}

// rounded to the nearest half hour, as the estimate isn't more precise than that
fun Duration.toHoursText(): String {
    val halfHours = maxOf(1, (toMinutes() / 30.0).roundToInt())
    val hours = halfHours / 2
    return if (halfHours % 2 == 0) "$hours h" else "$hours.5 h"
}
