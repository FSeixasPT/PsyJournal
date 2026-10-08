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

package com.isaakhanimann.journal.ui.tabs.search.substance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility
import com.isaakhanimann.journal.ui.theme.horizontalPadding

// per-substance exceptions to the routes hidden in the app settings
@Composable
fun RoutesShownSection(
    substanceName: String,
    routes: List<AdministrationRoute>,
    routeVisibility: RouteVisibility,
    onRouteShownChange: (AdministrationRoute, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    if (routes.isEmpty()) return
    SectionWithTitle(title = "Routes shown in the app") {
        Column(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp)) {
            routes.forEach { route ->
                val isShown = routeVisibility.isShown(substanceName, route)
                val isShownGlobally = routeVisibility.isShownGlobally(route)
                // the whole row is the switch, so screen readers read the route name with it
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = isShown, role = Role.Switch, onValueChange = { onRouteShownChange(route, it) })
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(route.displayText)
                        val note = when {
                            isShown != isShownGlobally -> if (isShownGlobally) "shown in app settings" else "hidden in app settings"
                            // an override that now matches the app settings, e.g. after they changed; Reset removes it
                            routeVisibility.hasOverride(substanceName, route) -> "set for this substance"
                            else -> null
                        }
                        if (note != null) {
                            Text(note, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Switch(checked = isShown, onCheckedChange = null)
                }
            }
            if (routeVisibility.hasOverrides(substanceName)) {
                TextButton(onClick = onReset) { Text("Reset to app settings") }
            }
        }
    }
}
