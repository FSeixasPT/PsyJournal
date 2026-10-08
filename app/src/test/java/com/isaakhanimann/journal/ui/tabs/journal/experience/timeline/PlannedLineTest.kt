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
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.horizontalWeightFromDots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PlannedLineTest {

    private val time = Instant.parse("2026-01-01T21:00:00Z")

    private fun planned(dose: Double = 52.5, route: AdministrationRoute = AdministrationRoute.INSUFFLATED, units: String = "mg") =
        PlannedIngestion(substanceName = "Ketamine", route = route, dose = dose, units = units)

    @Test
    fun plannedLineIsScaledByTheReferenceDoseOfItsRoute() {
        val line = buildPlannedLine(planned(dose = 52.5), ketamine, AdaptiveColor.PINK, time)!!
        assertEquals("Ketamine", line.substanceName)
        assertEquals(AdministrationRoute.INSUFFLATED, line.route)
        assertEquals(1f, line.height, 0.001f)
        assertEquals(52.5, line.referenceDose!!, 0.001)
        assertEquals("mg", line.doseUnits)
        assertEquals(true, line.isReferenceDoseFromDoseRanges)
        assertEquals(ketamineInsufflatedDuration, line.roaDuration)
        assertEquals(AdaptiveColor.PINK, line.color)
        assertEquals(time, line.startTime)
        assertNull(line.endTime)
        assertEquals(horizontalWeightFromDots(ketamineInsufflatedDose.getNumDots(52.5, "mg")), line.horizontalWeight, 0.001f)
    }

    @Test
    fun noLineWithoutFullDurations() {
        assertNull(buildPlannedLine(planned(dose = 150.0, route = AdministrationRoute.ORAL), ketamine, AdaptiveColor.PINK, time))
    }

    @Test
    fun noLineWithoutDoseRanges() {
        assertNull(buildPlannedLine(planned(route = AdministrationRoute.SUBLINGUAL), ketamine, AdaptiveColor.PINK, time))
    }

    @Test
    fun noLineWhenUnitsDifferFromTheDatabase() {
        assertNull(buildPlannedLine(planned(units = "µg"), ketamine, AdaptiveColor.PINK, time))
    }

    @Test
    fun noLineForARouteTheSubstanceDoesNotHave() {
        assertNull(buildPlannedLine(planned(route = AdministrationRoute.RECTAL), ketamine, AdaptiveColor.PINK, time))
    }

    @Test
    fun previewProblemOfEachRoute() {
        assertNull(ketamine.getRoa(AdministrationRoute.INSUFFLATED)!!.previewProblem())
        assertEquals(PreviewProblem.NO_FULL_DURATION, ketamine.getRoa(AdministrationRoute.ORAL)!!.previewProblem())
        assertEquals(PreviewProblem.NO_DOSE_RANGES, ketamine.getRoa(AdministrationRoute.SUBLINGUAL)!!.previewProblem())
    }

    @Test
    fun horizontalWeightGrowsWithDotsUpToFour() {
        assertEquals(0.5f, horizontalWeightFromDots(null), 0.001f)
        assertEquals(0.25f, horizontalWeightFromDots(1), 0.001f)
        assertEquals(0.5f, horizontalWeightFromDots(2), 0.001f)
        assertEquals(1f, horizontalWeightFromDots(4), 0.001f)
        assertEquals(1f, horizontalWeightFromDots(6), 0.001f)
    }

    @Test
    fun colorIsTheCompanionColorIfThereIsOne() {
        val companions = listOf(SubstanceCompanion("Ketamine", AdaptiveColor.TEAL))
        assertEquals(AdaptiveColor.TEAL, choosePlannedColor("Ketamine", companions, listOf(AdaptiveColor.RED)))
    }

    @Test
    fun colorIsTheFirstColorNotOnTheChartOtherwise() {
        val expected = AdaptiveColor.entries.first { it != AdaptiveColor.RED }
        assertEquals(expected, choosePlannedColor("Ketamine", emptyList(), listOf(AdaptiveColor.RED)))
        assertEquals(AdaptiveColor.entries.first(), choosePlannedColor("Ketamine", emptyList(), AdaptiveColor.entries.toList()))
    }
}
