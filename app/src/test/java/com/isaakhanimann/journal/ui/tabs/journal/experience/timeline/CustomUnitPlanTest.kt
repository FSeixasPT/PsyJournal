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
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.main.navigation.graphs.ChooseDoseCustomUnitRoute
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose.customunit.initialCountText
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class CustomUnitPlanTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)

    private fun unit(
        id: Int = 3,
        dose: Double? = 1.5,
        originalUnit: String = "mg",
        isArchived: Boolean = false,
        route: AdministrationRoute = AdministrationRoute.INSUFFLATED,
        substanceName: String = "Ketamine",
    ) = CustomUnit(
        id = id,
        substanceName = substanceName,
        name = "Nasex",
        administrationRoute = route,
        dose = dose,
        estimatedDoseStandardDeviation = null,
        isEstimate = false,
        isArchived = isArchived,
        unit = "spray",
        unitPlural = null,
        originalUnit = originalUnit,
        note = ""
    )

    private fun planned(count: Double = 5.0, dose: Double = 7.5) = PlannedIngestion(
        substanceName = "Ketamine",
        route = AdministrationRoute.INSUFFLATED,
        dose = dose,
        units = "mg",
        customUnit = PlannedCustomUnit(id = 3, count = count)
    )

    @Test
    fun customUnitPlanTakesItsDoseFromTheCurrentUnit() {
        val resolved = planned(count = 5.0, dose = 1.0).withCurrentCustomUnit(listOf(unit(dose = 1.5)))
        assertEquals(7.5, resolved.dose, 0.0)
        assertEquals("spray", resolved.customUnit!!.unit)
        assertEquals("sprays", resolved.customUnit!!.unitPlural)
    }

    @Test
    fun customUnitPlanFallsBackToThePlainDoseWhenTheUnitCannotBeUsed() {
        assertNull(planned().withCurrentCustomUnit(emptyList()).customUnit)
        assertNull(planned().withCurrentCustomUnit(listOf(unit(dose = null))).customUnit)
        assertNull(planned().withCurrentCustomUnit(listOf(unit(originalUnit = "µg"))).customUnit)
        assertNull(planned().withCurrentCustomUnit(listOf(unit(isArchived = true))).customUnit)
        assertEquals(7.5, planned().withCurrentCustomUnit(emptyList()).dose, 0.0)
    }

    @Test
    fun chipTextShowsTheCountAndThePureDose() {
        val resolved = planned(count = 5.0).withCurrentCustomUnit(listOf(unit()))
        assertEquals("Ketamine · 5 sprays (7.5 mg) insufflated", resolved.chipText())
        val one = planned(count = 1.0).withCurrentCustomUnit(listOf(unit()))
        assertEquals("Ketamine · 1 spray (1.5 mg) insufflated", one.chipText())
    }

    @Test
    fun offeredUnitsAreUsableUnitsOfThatSubstanceAndRoute() {
        val usable = unit(id = 1)
        val units = listOf(
            usable,
            unit(id = 2, dose = null),
            unit(id = 4, originalUnit = "µg"),
            unit(id = 5, isArchived = true),
            unit(id = 6, route = AdministrationRoute.ORAL),
            unit(id = 7, substanceName = "MDMA"),
        )
        assertEquals(listOf(usable), offeredCustomUnits("Ketamine", AdministrationRoute.INSUFFLATED, "mg", units))
    }

    @Test
    fun defaultUsesTheCustomUnitOfTheFirstIngestion() {
        val line = DataForOneEffectLine(
            substanceName = "Ketamine",
            route = AdministrationRoute.INSUFFLATED,
            roaDuration = ketamineInsufflatedDuration,
            height = (4.5 / 52.5).toFloat(),
            horizontalWeight = 0.5f,
            color = AdaptiveColor.PINK,
            startTime = at(0),
            endTime = null,
            referenceDose = 52.5,
            doseUnits = "mg",
            isReferenceDoseFromDoseRanges = true,
            customUnitId = 3,
            customUnitCount = 3.0
        )
        val plan = defaultPlannedIngestion(listOf(line)) { ketamine }!!
        assertEquals(PlannedCustomUnit(id = 3, count = 3.0), plan.customUnit)
        assertEquals(4.5, plan.dose, 0.0)
    }

    @Test
    fun quickLogOfACustomUnitSavesTheCountAndTheUnit() {
        val ingestion = planned(count = 5.0).withCurrentCustomUnit(listOf(unit())).toIngestion(experienceId = 7, now = at(0))
        assertEquals(5.0, ingestion.dose!!, 0.0)
        assertEquals("spray", ingestion.units)
        assertEquals(3, ingestion.customUnitId)
    }

    @Test
    fun pinOfACustomUnitSurvivesSavingAndLoading() {
        val pin = planned(count = 5.0).toPinnedRedose(experienceId = 7, currentPin = null, now = at(0))
        val loaded = PinnedRedose.fromPreferenceString(pin.toPreferenceString())!!
        assertEquals(3, loaded.customUnitId)
        assertEquals(5.0, loaded.customUnitCount!!, 0.0)
    }

    @Test
    fun customUnitDoseScreenIsPrefilledWithTheCount() {
        assertEquals("", ChooseDoseCustomUnitRoute(customUnitId = 3).initialCountText())
        assertEquals("5", ChooseDoseCustomUnitRoute(customUnitId = 3, count = 5.0).initialCountText())
    }
}
