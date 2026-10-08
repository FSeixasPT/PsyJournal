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

import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility
import org.junit.Test

class SubstancePickerTest {

    private val fullDuration = exactDuration(10f, 10f, 20f, 20f)
    private val dose = RoaDose("mg", 5.0, 30.0, 75.0, 175.0)

    private val cocaine = testSubstance("Cocaine", Roa(AdministrationRoute.INSUFFLATED, dose, fullDuration, null))
    private val mdma = testSubstance("MDMA", Roa(AdministrationRoute.ORAL, dose, null, null))
    private val lsd = testSubstance("LSD", Roa(AdministrationRoute.SUBLINGUAL, null, fullDuration, null))
    private val mixed = testSubstance(
        "Mixed",
        Roa(AdministrationRoute.ORAL, null, fullDuration, null),
        Roa(AdministrationRoute.INSUFFLATED, dose, null, null)
    )

    @Test
    fun substancesOfTheExperienceComeFirstInOrderOfRecency() {
        val entries = buildPickerEntries(
            matchingSubstances = listOf(cocaine, ketamine, lsd, mdma),
            experienceSubstanceNamesByRecency = listOf("Ketamine", "MDMA")
        )
        assertEquals(listOf("Ketamine", "MDMA", "Cocaine", "LSD"), entries.map { it.substanceName })
        assertEquals(listOf(true, true, false, false), entries.map { it.isInExperience })
        assertEquals(listOf(null, PreviewProblem.NO_FULL_DURATION, null, PreviewProblem.NO_DOSE_RANGES), entries.map { it.problem })
    }

    @Test
    fun substanceOfTheExperienceThatDoesNotMatchTheSearchIsLeftOut() {
        val entries = buildPickerEntries(listOf(cocaine), listOf("Ketamine"))
        assertEquals(listOf("Cocaine"), entries.map { it.substanceName })
    }

    @Test
    fun searchWithNoMatchesGivesNoEntries() {
        assertTrue(buildPickerEntries(emptyList(), listOf("Ketamine")).isEmpty())
    }

    @Test
    fun durationReasonWinsWhenRoutesFailForDifferentReasons() {
        assertEquals(PreviewProblem.NO_FULL_DURATION, mixed.substancePreviewProblem())
        assertEquals(PreviewProblem.NO_FULL_DURATION, testSubstance("Empty").substancePreviewProblem())
    }

    @Test
    fun preselectedRouteIsTheLatestPreviewableRouteOfTheExperience() {
        assertEquals(
            AdministrationRoute.INSUFFLATED,
            preselectedRoute(ketamine, listOf(AdministrationRoute.ORAL, AdministrationRoute.INSUFFLATED))
        )
        assertEquals(AdministrationRoute.INSUFFLATED, preselectedRoute(ketamine, emptyList()))
        assertNull(preselectedRoute(mdma, emptyList()))
    }

    @Test
    fun doseTextIsParsedWithCommaAndRejectsNonPositive() {
        assertEquals(0.5, parseDose("0,5")!!, 0.0001)
        assertEquals(40.0, parseDose(" 40 ")!!, 0.0001)
        assertNull(parseDose("0"))
        assertNull(parseDose("-1"))
        assertNull(parseDose("abc"))
        assertNull(parseDose(""))
    }

    @Test
    fun rangesTextListsTheKnownRanges() {
        assertEquals("light 5–30 · common 30–75 · strong 75–175 · heavy 175+ mg", dose.rangesText())
        assertEquals("common 30–75 · strong 75–175 · heavy 175+ mg", dose.copy(lightMin = null).rangesText())
    }

    @Test
    fun doseFieldKeepsEveryDigitSoDoneWithoutChangesKeepsTheDose() {
        assertEquals("1234", doseFieldText(1234.0))
        assertEquals(1234.0, parseDose(doseFieldText(1234.0))!!, 0.0)
        assertEquals(0.25, parseDose(doseFieldText(0.25))!!, 0.0)
    }

    @Test
    fun doseTextRejectsInfiniteAndUnusualNumberFormats() {
        assertNull(parseDose("Infinity"))
        assertNull(parseDose("1e400"))
        assertNull(parseDose("5f"))
        assertNull(parseDose("0x1p3"))
        assertNull(parseDose("NaN"))
        assertEquals(12.5, parseDose("12.5")!!, 0.0)
        assertEquals(3.0, parseDose("3.")!!, 0.0)
        assertEquals(0.5, parseDose(".5")!!, 0.0)
    }

    @Test
    fun substanceWhosePreviewableRoutesAreHiddenGetsItsOwnReason() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.INSUFFLATED))
        assertEquals(PreviewProblem.ROUTES_HIDDEN, cocaine.substancePreviewProblem(visibility))
        assertEquals(PreviewProblem.ROUTES_HIDDEN, buildPickerEntries(listOf(cocaine), emptyList(), visibility).single().problem)
    }

    @Test
    fun hiddenRoutesWithoutDataKeepTheExistingReason() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.ORAL))
        assertEquals(PreviewProblem.NO_FULL_DURATION, mdma.substancePreviewProblem(visibility))
    }

    @Test
    fun overrideShowingAHiddenRouteMakesTheSubstancePreviewableAgain() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.INSUFFLATED))
            .withSubstanceRoute("Cocaine", AdministrationRoute.INSUFFLATED, isShown = true)
        assertEquals(null, cocaine.substancePreviewProblem(visibility))
    }

    @Test
    fun preselectedRouteSkipsHiddenRoutes() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.INSUFFLATED))
        assertNull(preselectedRoute(ketamine, listOf(AdministrationRoute.INSUFFLATED), visibility))
    }

    @Test
    fun editorListsOnlyShownRoutes() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.SUBLINGUAL))
        assertEquals(
            listOf(AdministrationRoute.INSUFFLATED, AdministrationRoute.ORAL),
            editorRoutes(ketamine, visibility, plannedRoute = null).map { it.route }
        )
    }

    @Test
    fun editorKeepsThePlannedRouteEvenIfHidden() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.INSUFFLATED))
        assertTrue(editorRoutes(ketamine, visibility, plannedRoute = AdministrationRoute.INSUFFLATED).any { it.route == AdministrationRoute.INSUFFLATED })
    }
}
