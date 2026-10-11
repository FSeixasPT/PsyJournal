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

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines

import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.FullTimelines.Point
import kotlin.math.max
import kotlin.math.min

// the area between the shortest and the longest possible curve, so the chart shows how much the timing can vary
data class DurationRangeBand(
    // both edges are sorted by x and share the same x values
    val lower: List<Point>,
    val upper: List<Point>,
) {
    fun lowerAt(x: Float): Float = lower.heightAt(x)

    fun upperAt(x: Float): Float = upper.heightAt(x)
}

// the edges at every corner of either curve and wherever the curves cross, so the band follows them exactly
fun durationRangeBand(shortest: List<Point>, longest: List<Point>, maxHeight: Float): DurationRangeBand {
    val corners = (shortest.map { it.x } + longest.map { it.x }).distinct().sorted()
    val crossings = corners.zipWithNext().mapNotNull { (x0, x1) ->
        val difference0 = shortest.heightAt(x0) - longest.heightAt(x0)
        val difference1 = shortest.heightAt(x1) - longest.heightAt(x1)
        if (difference0 * difference1 < 0f) x0 + (x1 - x0) * difference0 / (difference0 - difference1) else null
    }
    val xs = (corners + crossings).sorted()
    return DurationRangeBand(
        lower = xs.map { Point(it, min(shortest.heightAt(it), longest.heightAt(it))) },
        // redoses overlap more on their longest curves; the band stays within the chart's height
        upper = xs.map { Point(it, min(max(shortest.heightAt(it), longest.heightAt(it)), maxHeight)) },
    )
}

// height of a curve given by its corners, zero outside of it
private fun List<Point>.heightAt(x: Float): Float {
    val segment = zipWithNext().firstOrNull { (start, end) -> start.x <= x && x <= end.x } ?: return 0f
    val (start, end) = segment
    if (end.x == start.x) return max(start.y, end.y)
    return start.y + (end.y - start.y) * (x - start.x) / (end.x - start.x)
}
