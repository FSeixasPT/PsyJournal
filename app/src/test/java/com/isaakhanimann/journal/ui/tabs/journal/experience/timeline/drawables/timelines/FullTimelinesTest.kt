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

    // rises 10-20 min, then 20-40 min at its peak and 20-40 min to fall
    private val ranged = RoaDuration(
        onset = DurationRange(10f, 20f, DurationUnits.MINUTES),
        comeup = DurationRange(10f, 20f, DurationUnits.MINUTES),
        peak = DurationRange(20f, 40f, DurationUnits.MINUTES),
        offset = DurationRange(20f, 40f, DurationUnits.MINUTES),
        total = null,
        afterglow = null
    )

    @Test
    fun rangeBandSpansTheShortestAndLongestCurve() {
        val band = listOf(line(0, ranged)).toFullTimelines(start, isRangeShown = true)!!.rangeBand!!
        // shortest: rises 10-20, peak until 40, falls until 60; longest: rises 20-40, peak until 80, falls until 120
        assertEquals(0f, band.lowerAt(seconds(15)), 0.001f)
        assertEquals(0.5f, band.upperAt(seconds(15)), 0.001f)
        assertEquals(0.5f, band.lowerAt(seconds(30)), 0.001f)
        assertEquals(1f, band.upperAt(seconds(30)), 0.001f)
        assertEquals(0.5f, band.lowerAt(seconds(50)), 0.001f)
        assertEquals(0f, band.lowerAt(seconds(100)), 0.001f)
        assertEquals(0.5f, band.upperAt(seconds(100)), 0.001f)
    }

    @Test
    fun rangeBandOfRedosesAddsUp() {
        val band = listOf(line(0, ranged), line(0, ranged)).toFullTimelines(start, isRangeShown = true)!!.rangeBand!!
        assertEquals(1f, band.lowerAt(seconds(30)), 0.001f)
        assertEquals(2f, band.upperAt(seconds(30)), 0.001f)
    }

    @Test
    fun rangeBandNeverReachesAboveTheCurvesHighestPoint() {
        // the longest curves of two ingestions 45 min apart overlap more than their average curves do
        val timelines = listOf(line(0, ranged), line(45, ranged)).toFullTimelines(start, isRangeShown = true)!!
        val band = timelines.rangeBand!!
        (0..150).forEach { minute ->
            assertEquals(true, band.upperAt(seconds(minute)) <= timelines.nonNormalisedHeight + 0.001f)
        }
    }

    @Test
    fun timelineIsWideEnoughForTheLongestCurveOnlyWhenTheRangeIsShown() {
        assertEquals(seconds(120), listOf(line(0, ranged)).toFullTimelines(start, isRangeShown = true)!!.endOfLineRelativeToStartInSeconds, 0.001f)
        // the average curve: 15 + 15 + 30 + 30 min
        assertEquals(seconds(90), listOf(line(0, ranged)).toFullTimelines(start)!!.endOfLineRelativeToStartInSeconds, 0.001f)
    }

    @Test
    fun noRangeBandUnlessShown() {
        assertNull(listOf(line(0, ranged)).toFullTimelines(start)!!.rangeBand)
    }

    @Test
    fun rangeBandFollowsTheCurvesWhereTheyCross() {
        val rising = listOf(FullTimelines.Point(0f, 0f), FullTimelines.Point(10f, 1f))
        val falling = listOf(FullTimelines.Point(0f, 1f), FullTimelines.Point(10f, 0f))
        val band = durationRangeBand(rising, falling, maxHeight = 1f)
        assertEquals(0.5f, band.lowerAt(5f), 0.001f)
        assertEquals(0.25f, band.lowerAt(2.5f), 0.001f)
        assertEquals(0.75f, band.upperAt(2.5f), 0.001f)
    }
}
