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

import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.WeightedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class FullTimelinesTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")

    private fun duration(onset: Float, comeup: Float, peak: Float, offset: Float) = RoaDuration(
        onset = DurationRange(onset, onset, DurationUnits.MINUTES),
        comeup = DurationRange(comeup, comeup, DurationUnits.MINUTES),
        peak = DurationRange(peak, peak, DurationUnits.MINUTES),
        offset = DurationRange(offset, offset, DurationUnits.MINUTES),
        total = null,
        afterglow = null
    )

    // fast: rises 10-20 min, peak 20-40 min, falls 40-60 min
    private val fast = duration(onset = 10f, comeup = 10f, peak = 20f, offset = 20f)

    // slow: rises 30-60 min, peak 60-120 min, falls 120-180 min
    private val slow = duration(onset = 30f, comeup = 30f, peak = 60f, offset = 60f)

    private fun line(minutes: Long, roaDuration: RoaDuration?, endMinutes: Long? = null) = WeightedLine(
        startTime = start.plus(minutes, ChronoUnit.MINUTES),
        endTime = endMinutes?.let { start.plus(it, ChronoUnit.MINUTES) },
        horizontalWeight = 0.5f,
        height = 1f,
        roaDuration = roaDuration
    )

    private fun seconds(minutes: Int) = minutes * 60f

    @Test
    fun linesWithDifferentDurationsAddUpWithTheirOwnTiming() {
        val timelines = listOf(line(0, fast), line(0, slow)).toFullTimelines(start)!!
        // 25 min: fast at peak (1), slow still in onset (0)
        assertEquals(1f, timelines.heightAt(seconds(25)), 0.001f)
        // 45 min: fast falling 5 of 20 min (0.75), slow rising 15 of 30 min (0.5)
        assertEquals(1.25f, timelines.heightAt(seconds(45)), 0.001f)
        // 90 min: fast gone, slow at peak
        assertEquals(1f, timelines.heightAt(seconds(90)), 0.001f)
    }

    @Test
    fun timeRangeLineUsesItsOwnDurations() {
        // a fast route sipped over 0-60 min and a slow point ingestion at 0
        val timelines = listOf(line(0, fast, endMinutes = 60), line(0, slow)).toFullTimelines(start)!!
        // after 2 h the fast range is fully worn off (ends at 60 + 10 + 10 + 20 + 20 = 120 min) and slow is at its peak end
        assertEquals(1f, timelines.heightAt(seconds(120)), 0.001f)
        // the fast range contributes while the slow line is still in its onset
        assertEquals(true, timelines.heightAt(seconds(25)) > 0f)
    }

    @Test
    fun noFullTimelinesWhenALineHasIncompleteDurations() {
        val incomplete = slow.copy(peak = null)
        assertNull(listOf(line(0, fast), line(0, incomplete)).toFullTimelines(start))
    }
}
