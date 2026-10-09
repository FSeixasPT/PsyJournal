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

package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.PlannedIngestion
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.chipText
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.plannedTimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class LiveNotificationContentTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)
    private val exactDuration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null)

    private fun line(
        name: String = "TestSubstance",
        route: AdministrationRoute = AdministrationRoute.ORAL,
        minutesAfterStart: Long = 0,
        duration: RoaDuration? = exactDuration,
    ) = DataForOneEffectLine(
        substanceName = name,
        route = route,
        roaDuration = duration,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = at(minutesAfterStart),
        endTime = null,
    )

    private fun content(
        lines: List<DataForOneEffectLine>,
        now: Instant,
        option: SavedTimeDisplayOption = SavedTimeDisplayOption.AUTO,
        planned: PlannedIngestion? = null,
        areRoutesMerged: Boolean = false,
    ) = buildLiveNotificationContent(
        title = "Experience",
        model = AllTimelinesModel(lines, emptyList(), emptyList(), false, areRoutesMerged),
        lines = lines,
        plannedIngestion = planned,
        savedTimeDisplayOption = option,
        now = now
    )

    @Test
    fun autoTimesAreRelativeToNow() {
        assertEquals(
            "TestSubstance · peaking · 100% · comes down in 30 min",
            content(listOf(line()), at(90)).lines.single().text
        )
    }

    @Test
    fun regularTimesAreClockTimes() {
        val text = content(listOf(line()), at(90), SavedTimeDisplayOption.REGULAR).lines.single().text
        assertEquals("TestSubstance · peaking · 100% · comes down " + plannedTimeText(at(120), at(90), at(0), TimeDisplayOption.REGULAR), text)
    }

    @Test
    fun timesRelativeToStartCountFromTheFirstIngestion() {
        val text = content(listOf(line()), at(90), SavedTimeDisplayOption.RELATIVE_TO_START).lines.single().text
        assertEquals("TestSubstance · peaking · 100% · comes down " + plannedTimeText(at(120), at(90), at(0), TimeDisplayOption.RELATIVE_TO_START), text)
    }

    @Test
    fun thePercentIsTheShareOfTheHighestPoint() {
        assertEquals(50, content(listOf(line()), at(45)).lines.single().percentOfPeak)
    }

    @Test
    fun unmergedRoutesOfOneSubstanceNameTheirRoute() {
        val lines = listOf(line(route = AdministrationRoute.ORAL), line(route = AdministrationRoute.INSUFFLATED, minutesAfterStart = 10))
        assertEquals(listOf("TestSubstance (oral)", "TestSubstance (insufflated)"), content(lines, at(90)).lines.map { it.label })
    }

    @Test
    fun aPinnedPlanAddsALine() {
        val planned = PlannedIngestion("TestSubstance", AdministrationRoute.ORAL, 50.0, "mg")
        assertEquals("Planned: " + planned.chipText(), content(listOf(line()), at(90), planned = planned).plannedLine)
        assertNull(content(listOf(line()), at(90)).plannedLine)
    }

    @Test
    fun itEndsWhenEveryLineHasEnded() {
        val during = content(listOf(line()), at(90))
        assertFalse(during.isFinished)
        assertEquals(at(180), during.endsAt)
        assertTrue(content(listOf(line()), at(200)).isFinished)
    }

    @Test
    fun substancesWithoutTimelinesDontKeepItAlive() {
        val result = content(listOf(line(name = "Custom", duration = null)), at(10))
        assertEquals(Phase.NO_TIMELINE, result.lines.single().phase)
        assertTrue(result.isFinished)
    }

    @Test
    fun theNextRefreshIsWithinFiveMinutesOrAtTheNextChange() {
        assertEquals(at(95), content(listOf(line()), at(90)).nextRefreshAt)
        assertEquals(at(120), content(listOf(line()), at(118)).nextRefreshAt)
        assertEquals(at(120), content(listOf(line()), at(119)).nextRefreshAt)
    }

    @Test
    fun collapsedShowsThreeLinesAndCountsTheRest() {
        val lines = (1..5).map { line(name = "Substance $it", minutesAfterStart = it.toLong()) }
        val (shown, more) = content(lines, at(90)).collapsedLines()
        assertEquals(listOf("Substance 1", "Substance 2", "Substance 3"), shown.map { it.label })
        assertEquals(2, more)
    }

    @Test
    fun activeEffectsNeedALineThatHasntEnded() {
        assertFalse(hasActiveEffects(emptyList(), false, true, at(90)))
        assertTrue(hasActiveEffects(listOf(line()), false, true, at(90)))
        assertFalse(hasActiveEffects(listOf(line()), false, true, at(200)))
    }
}
