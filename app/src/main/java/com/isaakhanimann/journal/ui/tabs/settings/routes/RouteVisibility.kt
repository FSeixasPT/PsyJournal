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

import com.isaakhanimann.journal.data.substances.AdministrationRoute

// a per-substance exception to the globally hidden routes, in either direction
data class RouteOverride(
    val substanceName: String,
    val route: AdministrationRoute,
    val isShown: Boolean
) {
    fun toPreferenceString(): String =
        listOf(substanceName, route.name, if (isShown) SHOW else HIDE).joinToString(SEPARATOR)

    companion object {
        // unit separator, which can't be part of a substance name
        private const val SEPARATOR = "\u001F"
        private const val SHOW = "SHOW"
        private const val HIDE = "HIDE"

        fun fromPreferenceString(value: String): RouteOverride? {
            val parts = value.split(SEPARATOR)
            if (parts.size != 3) return null
            val route = AdministrationRoute.entries.firstOrNull { it.name == parts[1] } ?: return null
            val isShown = when (parts[2]) {
                SHOW -> true
                HIDE -> false
                else -> return null
            }
            return RouteOverride(substanceName = parts[0], route = route, isShown = isShown)
        }
    }
}

// which routes are offered when choosing a route; logged ingestions are always shown
data class RouteVisibility(
    val hiddenRoutes: Set<AdministrationRoute> = emptySet(),
    val overrides: Set<RouteOverride> = emptySet()
) {
    // custom substances pass null and only use the global setting
    fun isShown(substanceName: String?, route: AdministrationRoute): Boolean {
        if (substanceName != null) {
            overrides.firstOrNull { it.substanceName == substanceName && it.route == route }?.let { return it.isShown }
        }
        return isShownGlobally(route)
    }

    fun isShownGlobally(route: AdministrationRoute): Boolean = route !in hiddenRoutes

    fun hasOverrides(substanceName: String): Boolean = overrides.any { it.substanceName == substanceName }

    // only stores an override if it differs from the global setting
    fun withSubstanceRoute(substanceName: String, route: AdministrationRoute, isShown: Boolean): RouteVisibility {
        val others = overrides.filterNot { it.substanceName == substanceName && it.route == route }.toSet()
        return if (isShown == isShownGlobally(route)) {
            copy(overrides = others)
        } else {
            copy(overrides = others + RouteOverride(substanceName, route, isShown))
        }
    }

    fun withoutOverridesOf(substanceName: String): RouteVisibility =
        copy(overrides = overrides.filterNot { it.substanceName == substanceName }.toSet())

    fun withGlobalRoute(route: AdministrationRoute, isShown: Boolean): RouteVisibility =
        copy(hiddenRoutes = if (isShown) hiddenRoutes - route else hiddenRoutes + route)
}

// the shown and the hidden routes, each in the order of the given routes
fun splitRoutes(
    substanceName: String?,
    routes: List<AdministrationRoute>,
    visibility: RouteVisibility
): Pair<List<AdministrationRoute>, List<AdministrationRoute>> =
    routes.partition { visibility.isShown(substanceName, it) }
