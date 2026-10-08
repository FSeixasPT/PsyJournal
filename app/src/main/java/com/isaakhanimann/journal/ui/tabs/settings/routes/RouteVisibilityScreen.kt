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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.theme.horizontalPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteVisibilityViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {
    // null until loaded, so the switches don't flip on first open
    val routeVisibilityFlow = userPreferences.routeVisibilityFlow.map<RouteVisibility, RouteVisibility?> { it }.stateIn(
        initialValue = null,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun setShown(route: AdministrationRoute, isShown: Boolean) = viewModelScope.launch {
        userPreferences.updateRouteVisibility { it.withGlobalRoute(route, isShown) }
    }
}

@Composable
fun RouteVisibilityScreen(viewModel: RouteVisibilityViewModel = hiltViewModel()) {
    RouteVisibilityScreen(
        routeVisibility = viewModel.routeVisibilityFlow.collectAsState().value,
        setShown = viewModel::setShown
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteVisibilityScreen(
    routeVisibility: RouteVisibility?,
    setShown: (AdministrationRoute, Boolean) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Routes of administration") }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Text(
                    text = "Hidden routes are not offered when adding an ingestion or in the redose chip. Ingestions you already logged are always shown. You can override this for a single substance on its page.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)
                )
            }
            if (routeVisibility == null) return@LazyColumn
            items(AdministrationRoute.entries) { route ->
                val isShown = routeVisibility.isShownGlobally(route)
                // the whole row is the switch, so screen readers read the route name with it
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = isShown, role = Role.Switch, onValueChange = { setShown(route, it) })
                        .padding(horizontal = horizontalPadding, vertical = 4.dp)
                ) {
                    Text(route.displayText, modifier = Modifier.weight(1f))
                    Text("Shown", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                    Switch(checked = isShown, onCheckedChange = null)
                }
                HorizontalDivider()
            }
        }
    }
}
