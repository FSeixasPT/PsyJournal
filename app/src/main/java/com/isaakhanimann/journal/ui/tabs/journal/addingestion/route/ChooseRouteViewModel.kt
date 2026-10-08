/*
 * Copyright (c) 2022. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.route

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.ChooseRouteOfAddIngestionRoute
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility
import com.isaakhanimann.journal.ui.tabs.settings.routes.splitRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChooseRouteViewModel @Inject constructor(
    substanceRepo: SubstanceRepository,
    userPreferences: UserPreferences,
    state: SavedStateHandle
) : ViewModel() {
    val route = state.toRoute<ChooseRouteOfAddIngestionRoute>()
    val substanceName = route.substanceName
    val substance = substanceRepo.getSubstance(substanceName)

    var showOtherRoutes by mutableStateOf(false)
    private val allPwRoutes = substance?.roas?.map { it.route } ?: emptyList()
    private val allOtherRoutes = AdministrationRoute.entries.filter { route -> !allPwRoutes.contains(route) }

    // all routes, unfiltered, for adding a custom unit (hidden routes don't apply there)
    val pwRoutes = allPwRoutes
    val otherRoutesChunked = allOtherRoutes.chunked(2)

    // null until loaded, so hidden routes don't show for a moment
    val routeVisibilityFlow = userPreferences.routeVisibilityFlow.map<RouteVisibility, RouteVisibility?> { it }.stateIn(
        initialValue = null,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    // the routes to offer: (substance routes, other routes, hidden routes), using the substance's overrides
    fun routesFor(visibility: RouteVisibility): Triple<List<AdministrationRoute>, List<AdministrationRoute>, List<AdministrationRoute>> {
        val (shownPw, hiddenPw) = splitRoutes(substanceName, allPwRoutes, visibility)
        val (shownOther, hiddenOther) = splitRoutes(substanceName, allOtherRoutes, visibility)
        return Triple(shownPw, shownOther, hiddenPw + hiddenOther)
    }

    var isShowingInjectionDialog by mutableStateOf(false)
    var currentRoute by mutableStateOf(AdministrationRoute.INTRAVENOUS)
}