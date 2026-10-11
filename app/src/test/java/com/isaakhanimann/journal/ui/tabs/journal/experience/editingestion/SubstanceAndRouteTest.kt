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

package com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubstanceAndRouteTest {
    private val spray = CustomUnit(
        id = 3,
        substanceName = "2C-B",
        name = "Nasex",
        administrationRoute = AdministrationRoute.INSUFFLATED,
        dose = 1.5,
        estimatedDoseStandardDeviation = null,
        isEstimate = false,
        isArchived = false,
        unit = "spray",
        originalUnit = "mg",
        note = ""
    )
    private val fiveSprays = DoseFields(dose = 5.0, isEstimate = false, estimatedDoseStandardDeviation = null, units = "spray", customUnit = spray)

    @Test
    fun theSubstancesDocumentedRoutesComeFirstThenTheRest() {
        val routes = routesInOrder(listOf(AdministrationRoute.INSUFFLATED, AdministrationRoute.ORAL))
        assertEquals(listOf(AdministrationRoute.INSUFFLATED, AdministrationRoute.ORAL), routes.take(2))
        assertEquals(AdministrationRoute.entries.toSet(), routes.toSet())
        assertEquals(AdministrationRoute.entries.size, routes.size)
    }

    @Test
    fun theCustomUnitStaysWhenSubstanceAndRouteDontChange() {
        assertEquals(fiveSprays, fiveSprays.afterChangeTo("2C-B", AdministrationRoute.INSUFFLATED))
    }

    @Test
    fun anotherSubstanceTurnsTheCustomUnitIntoItsDose() {
        assertEquals(
            DoseFields(dose = 7.5, isEstimate = false, estimatedDoseStandardDeviation = null, units = "mg", customUnit = null),
            fiveSprays.afterChangeTo("2C-E", AdministrationRoute.INSUFFLATED)
        )
    }

    @Test
    fun anotherRouteTurnsTheCustomUnitIntoItsDose() {
        val changed = fiveSprays.afterChangeTo("2C-B", AdministrationRoute.ORAL)
        assertEquals(7.5, changed.dose)
        assertNull(changed.customUnit)
    }

    @Test
    fun aCustomUnitWithoutADoseLeavesTheDoseUnknown() {
        val unknown = fiveSprays.copy(customUnit = spray.copy(dose = null))
        val changed = unknown.afterChangeTo("2C-E", AdministrationRoute.INSUFFLATED)
        assertNull(changed.dose)
        assertEquals("mg", changed.units)
        assertNull(changed.customUnit)
    }

    @Test
    fun anEstimatedCustomUnitMakesTheDoseAnEstimate() {
        val estimated = fiveSprays.copy(customUnit = spray.copy(isEstimate = true, estimatedDoseStandardDeviation = 0.5))
        val changed = estimated.afterChangeTo("2C-E", AdministrationRoute.INSUFFLATED)
        assertEquals(true, changed.isEstimate)
        assertEquals(2.5, changed.estimatedDoseStandardDeviation!!, 0.001)
    }

    @Test
    fun plainDosesStayAsTyped() {
        val plain = DoseFields(dose = 100.0, isEstimate = false, estimatedDoseStandardDeviation = null, units = "mg", customUnit = null)
        assertEquals(plain, plain.afterChangeTo("MDMA", AdministrationRoute.ORAL))
    }

    @Test
    fun aSubstanceWithAColorKeepsIt() {
        val hidden = SubstanceCompanion("MDMA", AdaptiveColor.PINK, isPartOfTimeline = false)
        assertEquals(hidden, companionFor("MDMA", listOf(hidden)))
    }

    @Test
    fun aSubstanceNeverLoggedGetsAnUnusedColor() {
        val used = AdaptiveColor.entries.drop(1).map { SubstanceCompanion(it.name, it) }
        assertEquals(SubstanceCompanion("MDMA", AdaptiveColor.entries.first()), companionFor("MDMA", used))
    }
}
