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
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SubstanceCurveTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)

    // onset 30m, comeup 30m, peak 60m, offset 60m
    private val exactDuration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null)

    private fun line(route: AdministrationRoute, minutesAfterStart: Long, roaDuration: RoaDuration = exactDuration) =
        DataForOneEffectLine(
            substanceName = "TestSubstance",
            route = route,
            roaDuration = roaDuration,
            height = 1f,
            horizontalWeight = 0.5f,
            color = AdaptiveColor.BLUE,
            startTime = at(minutesAfterStart),
            endTime = null,
        )

    private fun model(vararg lines: DataForOneEffectLine, areRoutesMerged: Boolean = false) = AllTimelinesModel(
        dataForLines = lines.toList(),
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false,
        areRoutesMerged = areRoutesMerged
    )

    @Test
    fun aFullLineGivesItsHeightOverTime() {
        val curve = model(line(AdministrationRoute.ORAL, 0)).substanceCurves().single()
        val line = curve.line!!
        assertEquals("TestSubstance", curve.substanceName)
        assertEquals(listOf(AdministrationRoute.ORAL), curve.routes)
        assertEquals(0f, line.heightAt(at(20)), 0.001f)
        assertEquals(0.5f, line.heightAt(at(45)) / line.maxHeight, 0.001f)
        assertEquals(1f, line.heightAt(at(90)) / line.maxHeight, 0.001f)
        assertEquals(at(180), line.endTime)
    }

    @Test
    fun unmergedRoutesAreSeparateCurves() {
        val curves = model(line(AdministrationRoute.ORAL, 0), line(AdministrationRoute.INSUFFLATED, 10)).substanceCurves()
        assertEquals(listOf(listOf(AdministrationRoute.ORAL), listOf(AdministrationRoute.INSUFFLATED)), curves.map { it.routes })
    }

    @Test
    fun aDurationWithoutOffsetHasNoLine() {
        val withoutOffset = exactDuration.copy(offset = null)
        val curve = model(line(AdministrationRoute.ORAL, 0, withoutOffset)).substanceCurves().single()
        assertNull(curve.line)
    }
}
