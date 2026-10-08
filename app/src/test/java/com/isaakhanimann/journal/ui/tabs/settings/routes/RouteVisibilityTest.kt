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

package com.isaakhanimann.journal.ui.tabs.settings.routes

import com.isaakhanimann.journal.data.substances.AdministrationRoute.INSUFFLATED
import com.isaakhanimann.journal.data.substances.AdministrationRoute.ORAL
import com.isaakhanimann.journal.data.substances.AdministrationRoute.RECTAL
import com.isaakhanimann.journal.data.substances.AdministrationRoute.SMOKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteVisibilityTest {

    private val insufflatedHidden = RouteVisibility(hiddenRoutes = setOf(INSUFFLATED))

    @Test
    fun globalSettingDecidesWithoutOverride() {
        assertEquals(false, insufflatedHidden.isShown("Ketamine", INSUFFLATED))
        assertEquals(true, insufflatedHidden.isShown("Ketamine", ORAL))
    }

    @Test
    fun overrideCanShowAGloballyHiddenRoute() {
        val visibility = insufflatedHidden.withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
        assertEquals(true, visibility.isShown("Ketamine", INSUFFLATED))
        assertEquals(false, visibility.isShown("Cocaine", INSUFFLATED))
    }

    @Test
    fun overrideCanHideAGloballyShownRoute() {
        val visibility = RouteVisibility().withSubstanceRoute("Cannabis", SMOKED, isShown = false)
        assertEquals(false, visibility.isShown("Cannabis", SMOKED))
        assertEquals(true, visibility.isShown("Tobacco", SMOKED))
    }

    @Test
    fun customSubstancesUseTheGlobalSettingOnly() {
        val visibility = insufflatedHidden.withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
        assertEquals(false, visibility.isShown(null, INSUFFLATED))
    }

    @Test
    fun onlyDifferencesFromTheGlobalSettingAreStored() {
        assertEquals(emptySet<RouteOverride>(), RouteVisibility().withSubstanceRoute("Ketamine", ORAL, isShown = true).overrides)
        val shown = insufflatedHidden.withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
        assertEquals(emptySet<RouteOverride>(), shown.withSubstanceRoute("Ketamine", INSUFFLATED, isShown = false).overrides)
    }

    @Test
    fun resetRemovesOnlyThatSubstancesOverrides() {
        val visibility = insufflatedHidden
            .withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
            .withSubstanceRoute("Cocaine", INSUFFLATED, isShown = true)
            .withoutOverridesOf("Ketamine")
        assertEquals(false, visibility.hasOverrides("Ketamine"))
        assertEquals(true, visibility.hasOverrides("Cocaine"))
    }

    @Test
    fun changingTheGlobalRouteKeepsOverrides() {
        val visibility = insufflatedHidden
            .withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
            .withGlobalRoute(INSUFFLATED, isShown = true)
        assertEquals(setOf(RouteOverride("Ketamine", INSUFFLATED, true)), visibility.overrides)
        assertEquals(true, visibility.isShown("Ketamine", INSUFFLATED))
        assertEquals(emptySet<Any>(), visibility.hiddenRoutes)
    }

    @Test
    fun overrideSurvivesSavingAndLoading() {
        val override = RouteOverride("2C-B", INSUFFLATED, isShown = false)
        assertEquals(override, RouteOverride.fromPreferenceString(override.toPreferenceString()))
    }

    @Test
    fun malformedOverridesAreIgnored() {
        assertNull(RouteOverride.fromPreferenceString(""))
        assertNull(RouteOverride.fromPreferenceString("Ketamine"))
        assertNull(RouteOverride.fromPreferenceString(listOf("Ketamine", "TELEPORTED", "SHOW").joinToString("\u001F")))
        assertNull(RouteOverride.fromPreferenceString(listOf("Ketamine", "ORAL", "MAYBE").joinToString("\u001F")))
        assertNull(RouteOverride.fromPreferenceString(listOf("a", "b", "ORAL", "SHOW").joinToString("\u001F")))
    }

    @Test
    fun splitKeepsTheOrderOfTheRoutes() {
        val visibility = insufflatedHidden.withSubstanceRoute("Ketamine", RECTAL, isShown = false)
        val (shown, hidden) = splitRoutes("Ketamine", listOf(INSUFFLATED, ORAL, RECTAL, SMOKED), visibility)
        assertEquals(listOf(ORAL, SMOKED), shown)
        assertEquals(listOf(INSUFFLATED, RECTAL), hidden)
    }

    @Test
    fun everyRouteHiddenStillListsThemAsHidden() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(ORAL, INSUFFLATED))
        val (shown, hidden) = splitRoutes("Ketamine", listOf(INSUFFLATED, ORAL), visibility)
        assertEquals(emptyList<Any>(), shown)
        assertEquals(listOf(INSUFFLATED, ORAL), hidden)
    }

    @Test
    fun overrideAppliesToAnyRouteOfTheSubstance() {
        // e.g. RECTAL is not a PsychonautWiki route of ketamine, but appears in the "other routes" list
        val visibility = RouteVisibility().withSubstanceRoute("Ketamine", RECTAL, isShown = false)
        assertEquals(listOf(SMOKED) to listOf(RECTAL), splitRoutes("Ketamine", listOf(RECTAL, SMOKED), visibility))
    }
}
