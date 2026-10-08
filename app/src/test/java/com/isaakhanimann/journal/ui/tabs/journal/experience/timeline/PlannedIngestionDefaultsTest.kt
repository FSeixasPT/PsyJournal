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
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class PlannedIngestionDefaultsTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)

    // MDMA has dose ranges but no durations, so it can't be previewed
    private val mdma = testSubstance(
        "MDMA",
        Roa(AdministrationRoute.ORAL, ketamineInsufflatedDose.copy(units = "mg"), null, null)
    )
    private val substances = mapOf("Ketamine" to ketamine, "MDMA" to mdma)

    private fun line(
        substance: String,
        route: AdministrationRoute,
        minutes: Long,
        dose: Double?,
        units: String = "mg",
        referenceDose: Double = 52.5
    ) = DataForOneEffectLine(
        substanceName = substance,
        route = route,
        roaDuration = ketamineInsufflatedDuration,
        height = if (dose != null) (dose / referenceDose).toFloat() else 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.PINK,
        startTime = at(minutes),
        endTime = null,
        referenceDose = if (dose != null) referenceDose else null,
        doseUnits = if (dose != null) units else null,
        isReferenceDoseFromDoseRanges = true
    )

    @Test
    fun defaultIsTheLatestPreviewableIngestion() {
        val lines = listOf(
            line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40.0),
            line("MDMA", AdministrationRoute.ORAL, 30, dose = 100.0)
        )
        val planned = defaultPlannedIngestion(lines) { substances[it] }!!
        assertEquals("Ketamine", planned.substanceName)
        assertEquals(AdministrationRoute.INSUFFLATED, planned.route)
        assertEquals(40.0, planned.dose, 0.01)
        assertEquals("mg", planned.units)
    }

    @Test
    fun defaultSkipsIngestionsWithUnknownDose() {
        val lines = listOf(
            line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40.0),
            line("Ketamine", AdministrationRoute.INSUFFLATED, 30, dose = null)
        )
        assertEquals(40.0, defaultPlannedIngestion(lines) { substances[it] }!!.dose, 0.01)
    }

    @Test
    fun doseLoggedInOtherUnitsFallsBackToTheStartOfTheCommonRange() {
        val lines = listOf(line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40000.0, units = "µg"))
        assertEquals(30.0, defaultPlannedIngestion(lines) { substances[it] }!!.dose, 0.01)
    }

    @Test
    fun noDefaultWhenNothingCanBePreviewed() {
        val lines = listOf(line("MDMA", AdministrationRoute.ORAL, 0, dose = 100.0))
        assertNull(defaultPlannedIngestion(lines) { substances[it] })
    }

    @Test
    fun suggestedDoseIsTheLastDoseInTheDatabaseUnit() {
        val lines = listOf(
            line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40.0),
            line("Ketamine", AdministrationRoute.INSUFFLATED, 30, dose = 25.0)
        )
        assertEquals(25.0, suggestedDose("Ketamine", AdministrationRoute.INSUFFLATED, lines, ketamineInsufflatedDose)!!, 0.01)
        assertEquals(30.0, suggestedDose("Ketamine", AdministrationRoute.INSUFFLATED, emptyList(), ketamineInsufflatedDose)!!, 0.01)
    }

    @Test
    fun chipTextAlwaysNamesTheRoute() {
        val planned = PlannedIngestion("Ketamine", AdministrationRoute.INSUFFLATED, 40.0, "mg")
        assertEquals("Ketamine · 40 mg insufflated", planned.chipText())
    }

    @Test
    fun changingThePinnedDoseKeepsThePinTime() {
        val first = PlannedIngestion("Ketamine", AdministrationRoute.INSUFFLATED, 40.0, "mg")
            .toPinnedRedose(experienceId = 7, currentPin = null, now = at(10))
        val changed = PlannedIngestion("Ketamine", AdministrationRoute.INSUFFLATED, 30.0, "mg")
            .toPinnedRedose(experienceId = 7, currentPin = first, now = at(20))
        assertEquals(at(10), changed.pinnedAt)
        assertEquals(30.0, changed.dose, 0.01)
        val other = PlannedIngestion("MDMA", AdministrationRoute.ORAL, 100.0, "mg")
            .toPinnedRedose(experienceId = 7, currentPin = first, now = at(20))
        assertEquals(at(20), other.pinnedAt)
    }

    @Test
    fun suggestedDoseHasNoFloatNoise() {
        // 40 mg with a reference dose of 50 mg is stored as the float height 0.8
        val lines = listOf(line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40.0, referenceDose = 50.0))
        assertEquals(40.0, suggestedDose("Ketamine", AdministrationRoute.INSUFFLATED, lines, ketamineInsufflatedDose)!!, 0.0)
    }
}
