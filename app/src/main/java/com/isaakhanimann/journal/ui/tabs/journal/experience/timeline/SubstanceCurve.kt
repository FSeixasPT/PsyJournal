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

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.FullTimelines
import java.time.Duration
import java.time.Instant

// a line of the chart: a substance, or the merged routes of a substance
data class SubstanceCurve(
    val substanceName: String,
    val routes: List<AdministrationRoute>,
    val color: AdaptiveColor,
    // null if the durations are too incomplete to draw the summed line
    val line: CurveLine?,
)

// the summed line of all ingestions of a curve, as drawn on the chart
class CurveLine(private val startTime: Instant, private val fullTimelines: FullTimelines) {
    val maxHeight: Float get() = fullTimelines.nonNormalisedHeight

    val endTime: Instant get() = startTime.plusSeconds(fullTimelines.endOfLineRelativeToStartInSeconds.toLong())

    fun heightAt(time: Instant): Float =
        fullTimelines.heightAt(Duration.between(startTime, time).toMillis() / 1000f)
}
