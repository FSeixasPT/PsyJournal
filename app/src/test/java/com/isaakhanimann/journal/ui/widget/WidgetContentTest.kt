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

package com.isaakhanimann.journal.ui.widget

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.plannedTimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class WidgetContentTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)
    // 30 min onset, 30 come-up, 60 peak, 60 offset: active from 0 to 180 minutes
    private val duration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null)
    private val settings = WidgetSettings(
        areSubstanceHeightsIndependent = false,
        areRoutesMerged = false,
        savedTimeDisplayOption = SavedTimeDisplayOption.AUTO
    )

    private fun line(minutesAfterStart: Long) = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = duration,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = at(minutesAfterStart),
        endTime = null,
    )

    private fun candidate(id: Int, vararg lineStarts: Long) = WidgetCandidate(
        experience = Experience(id = id, title = "Experience $id", text = "", sortDate = at(lineStarts.minOrNull() ?: 0), location = null),
        lines = lineStarts.map { line(it) }
    )

    @Test
    fun anActiveExperienceIsChosen() {
        val chosen = chooseWidgetExperience(listOf(candidate(1, 0)), settings, at(90))
        assertEquals(1, chosen?.experience?.id)
    }

    @Test
    fun anEndedNewerExperienceIsSkipped() {
        // newest first: experience 2 started at -300 and ended at -120, experience 1 started at 0 and runs until 180
        val chosen = chooseWidgetExperience(listOf(candidate(2, -300), candidate(1, 0)), settings, at(90))
        assertEquals(1, chosen?.experience?.id)
    }

    @Test
    fun anExperienceWithoutLinesOfTheUserIsNotActive() {
        // myEffectLines drops friends' ingestions, so such an experience arrives with no lines
        assertNull(chooseWidgetExperience(listOf(candidate(1)), settings, at(90)))
    }

    @Test
    fun noExperiencesGiveNothingActive() {
        assertNull(chooseWidgetExperience(emptyList(), settings, at(90)))
        assertEquals(WidgetContent.NothingActive, buildWidgetContent(null, settings, at(90)))
    }

    @Test
    fun activeContentCarriesTheNotificationLines() {
        val content = buildWidgetContent(candidate(1, 0), settings, at(90)) as WidgetContent.Active
        assertEquals(1, content.experienceId)
        assertEquals("Experience 1", content.title)
        assertEquals(
            "TestSubstance · peaking · 100% · comes down " + plannedTimeText(at(120), at(90), at(0), TimeDisplayOption.REGULAR),
            content.lines.single().text
        )
        assertTrue(content.nextRefreshAt > at(90))
    }

    @Test
    fun anExperienceThatEndedGivesNothingActive() {
        assertEquals(WidgetContent.NothingActive, buildWidgetContent(candidate(1, 0), settings, at(200)))
    }

    @Test
    fun candidatesAreTheExperiencesWithRecentIngestionsLatestFirst() {
        // experience 5 was started days ago but got an ingestion an hour ago, so it is the most recent
        val ingestions = listOf(3 to at(-120), 5 to at(-60), 3 to at(-180), 5 to at(-4000))
        assertEquals(listOf(5, 3), candidateExperienceIdsLatestFirst(ingestions))
    }

    @Test
    fun widgetsRefreshAtLeastEveryTwoMinutes() {
        // the next phase change is 30 minutes away, so only the cap decides
        val content = buildWidgetContent(candidate(1, 0), settings, at(90)) as WidgetContent.Active
        assertEquals(at(92), content.nextRefreshAt)
    }

    @Test
    fun aNearerPhaseChangeStillComesFirst() {
        // comes down at 120, one minute from now
        val content = buildWidgetContent(candidate(1, 0), settings, at(119)) as WidgetContent.Active
        assertEquals(at(120), content.nextRefreshAt)
    }

    @Test
    fun timesRelativeToNowBecomeClockTimesSoTheyNeverGoStale() {
        val relativeToNow = settings.copy(savedTimeDisplayOption = SavedTimeDisplayOption.RELATIVE_TO_NOW)
        val content = buildWidgetContent(candidate(1, 0), relativeToNow, at(90)) as WidgetContent.Active
        assertEquals(
            "TestSubstance · peaking · 100% · comes down " + plannedTimeText(at(120), at(90), at(0), TimeDisplayOption.REGULAR),
            content.lines.single().text
        )
    }

    @Test
    fun timesRelativeToTheStartAreKept() {
        val relativeToStart = settings.copy(savedTimeDisplayOption = SavedTimeDisplayOption.RELATIVE_TO_START)
        val content = buildWidgetContent(candidate(1, 0), relativeToStart, at(90)) as WidgetContent.Active
        assertEquals(
            "TestSubstance · peaking · 100% · comes down " + plannedTimeText(at(120), at(90), at(0), TimeDisplayOption.RELATIVE_TO_START),
            content.lines.single().text
        )
    }

    @Test
    fun widgetLinesUseArrowsLikeTheCollapsedNotification() {
        // coming down at 150, half of the peak, ends at 180
        val content = buildWidgetContent(candidate(1, 0), settings, at(150)) as WidgetContent.Active
        assertEquals(
            "TestSubstance 50% ▼ · ends " + plannedTimeText(at(180), at(150), at(0), TimeDisplayOption.REGULAR),
            content.lines.single().compactText.replace(' ', ' ')
        )
    }
}
