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

class PinnedRedoseTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")

    // onset 30m, comeup 30m, peak 60m, offset 60m
    private val exactDuration = RoaDuration(
        onset = DurationRange(30f, 30f, DurationUnits.MINUTES),
        comeup = DurationRange(30f, 30f, DurationUnits.MINUTES),
        peak = DurationRange(60f, 60f, DurationUnits.MINUTES),
        offset = DurationRange(60f, 60f, DurationUnits.MINUTES),
        total = null,
        afterglow = null
    )

    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)

    private fun line(minutes: Long, dose: Double, route: AdministrationRoute = AdministrationRoute.INSUFFLATED) =
        DataForOneEffectLine(
            substanceName = "Ketamine",
            route = route,
            roaDuration = exactDuration,
            height = (dose / 100.0).toFloat(),
            horizontalWeight = 0.5f,
            color = AdaptiveColor.PINK,
            startTime = at(minutes),
            endTime = null,
            referenceDose = 100.0,
            doseUnits = "mg"
        )

    private fun model(vararg lines: DataForOneEffectLine) = AllTimelinesModel(
        dataForLines = lines.toList(),
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false
    )

    private fun pin(
        dose: Double = 30.0,
        route: AdministrationRoute = AdministrationRoute.INSUFFLATED,
        pinnedAt: Instant = at(10),
    ) = PinnedRedose(
        experienceId = 7,
        substanceName = "Ketamine",
        route = route,
        dose = dose,
        units = "mg",
        pinnedAt = pinnedAt
    )

    @Test
    fun pinSurvivesSavingAndLoading() {
        val pin = pin(dose = 42.5)
        assertEquals(pin, PinnedRedose.fromPreferenceString(pin.toPreferenceString()))
    }

    @Test
    fun missingOrBrokenSavedValueGivesNoPin() {
        assertNull(PinnedRedose.fromPreferenceString(null))
        assertNull(PinnedRedose.fromPreferenceString(""))
        assertNull(PinnedRedose.fromPreferenceString("not a pin"))
        assertNull(PinnedRedose.fromPreferenceString(pin().toPreferenceString().replace("INSUFFLATED", "TELEPORTED")))
    }

    @Test
    fun validPinUsesThePinnedDose() {
        val option = model(line(0, dose = 40.0)).resolvePinnedPlan(pin(dose = 30.0), now = at(20))!!
        assertEquals(AdministrationRoute.INSUFFLATED, option.route)
        assertEquals(30.0, option.dose, 0.001)
    }

    @Test
    fun pinEndsWhenTheSubstanceIsLoggedAfterPinning() {
        val model = model(line(0, dose = 40.0), line(30, dose = 30.0))
        assertNull(model.resolvePinnedPlan(pin(pinnedAt = at(10)), now = at(40)))
    }

    @Test
    fun ingestionOfAnotherRouteDoesNotEndThePin() {
        val model = model(line(0, dose = 40.0), line(30, dose = 150.0, route = AdministrationRoute.ORAL))
        assertEquals(30.0, model.resolvePinnedPlan(pin(pinnedAt = at(10)), now = at(40))!!.dose, 0.001)
    }

    @Test
    fun pinEndsWhenNowIsPastTheEndOfTheTimeline() {
        // the single dose ends after 180 min; the timeline ends 10 min later
        val model = model(line(0, dose = 40.0))
        assertNull(model.resolvePinnedPlan(pin(), now = at(300)))
    }

    @Test
    fun pinSavedByThePreviousVersionStillLoads() {
        val saved = listOf("7", "Ketamine", "INSUFFLATED", "30.0", "mg", "2026-01-01T20:10:00Z").joinToString("\u001F")
        assertEquals(pin(dose = 30.0, pinnedAt = at(10)), PinnedRedose.fromPreferenceString(saved))
    }

    @Test
    fun pinOfAnotherSubstanceEndsOnlyWhenThatSubstanceIsLogged() {
        val mdmaPin = pin(pinnedAt = at(10)).copy(substanceName = "MDMA", route = AdministrationRoute.ORAL, dose = 100.0)
        val ketamineLater = model(line(0, dose = 40.0), line(30, dose = 30.0))
        assertEquals("MDMA", ketamineLater.resolvePinnedPlan(mdmaPin, now = at(40))!!.substanceName)
        val mdmaLater = model(
            line(0, dose = 40.0),
            line(30, dose = 100.0, route = AdministrationRoute.ORAL).copy(substanceName = "MDMA")
        )
        assertNull(mdmaLater.resolvePinnedPlan(mdmaPin, now = at(40)))
    }
}
