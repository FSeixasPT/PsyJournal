/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.main

import com.isaakhanimann.journal.ui.main.navigation.graphs.SubstanceRoute
import com.isaakhanimann.journal.ui.main.navigation.graphs.AddIngestionRoute
import com.isaakhanimann.journal.ui.main.navigation.graphs.ExperienceRoute
import com.isaakhanimann.journal.ui.main.navigation.graphs.JournalScreenRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.NotificationTarget
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.isaakhanimann.journal.ui.main.navigation.graphs.journalGraph
import com.isaakhanimann.journal.ui.main.navigation.graphs.saferGraph
import com.isaakhanimann.journal.ui.main.navigation.graphs.searchGraph
import com.isaakhanimann.journal.ui.main.navigation.graphs.settingsGraph
import com.isaakhanimann.journal.ui.main.navigation.graphs.statsGraph
import com.isaakhanimann.journal.ui.main.navigation.JournalTopLevelRoute
import com.isaakhanimann.journal.ui.main.navigation.topLevelRoutes
import com.isaakhanimann.journal.ui.main.navigation.graphs.WhatsNewRoute
import com.isaakhanimann.journal.ui.tabs.settings.whatsnew.WhatsNewDialog

@Composable
fun MainScreen(
    notificationTarget: NotificationTarget? = null,
    onNotificationTargetHandled: () -> Unit = {},
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    if (viewModel.isAcceptedFlow.collectAsState().value) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                val currentDestination = navBackStackEntry?.destination
                topLevelRoutes.forEach { topLevelRoute ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.hasRoute(topLevelRoute.route::class) } == true
                    item(
                        icon = {
                            Icon(
                                if (selected) topLevelRoute.filledIcon else topLevelRoute.outlinedIcon,
                                contentDescription = topLevelRoute.name
                            )
                        },
                        label = { Text(topLevelRoute.name) },
                        selected = selected,
                        onClick = {
                            if (selected) {
                                val isAlreadyOnTopOfTab =
                                    topLevelRoutes.any { it.route == currentDestination?.route }
                                if (!isAlreadyOnTopOfTab) {
                                    navController.popBackStack()
                                }
                            } else {
                                navController.navigate(topLevelRoute.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    // on the back stack as users select items
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination when
                                    // reselecting the same item
                                    launchSingleTop = true
                                    // Restore state when reselecting a previously selected item
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        ) {
            NavHost(
                navController,
                startDestination = JournalTopLevelRoute
            ) {
                journalGraph(navController)
                statsGraph(navController)
                searchGraph(navController)
                saferGraph(navController)
                settingsGraph(navController)
            }
            LaunchedEffect(notificationTarget) {
                when (val target = notificationTarget) {
                    null -> return@LaunchedEffect
                    is NotificationTarget.Experience -> if (viewModel.experienceExists(target.experienceId)) {
                        // single top, so tapping the notification twice doesn't stack the same screen;
                        // not for Log planned, which needs a new screen to show the confirmation
                        navController.navigate(ExperienceRoute(target.experienceId, target.openQuickLog)) {
                            launchSingleTop = !target.openQuickLog
                        }
                    }
                    NotificationTarget.AddIngestion -> navController.navigate(AddIngestionRoute) { launchSingleTop = true }
                    // the journal list is always at the bottom of the back stack
                    NotificationTarget.Journal -> navController.popBackStack(JournalScreenRoute, inclusive = false)
                    is NotificationTarget.Substance -> navController.navigate(SubstanceRoute(target.substanceName)) { launchSingleTop = true }
                }
                onNotificationTargetHandled()
            }
            val releasesToAnnounce by viewModel.releasesToAnnounceFlow.collectAsState()
            if (releasesToAnnounce.isNotEmpty()) {
                WhatsNewDialog(
                    releases = releasesToAnnounce,
                    onDismiss = viewModel::markWhatsNewSeen,
                    onShowDetails = {
                        viewModel.markWhatsNewSeen()
                        navController.navigate(WhatsNewRoute) { launchSingleTop = true }
                    }
                )
            }
        }
    } else {
        // until the conditions are accepted, the notification only opens the app
        LaunchedEffect(notificationTarget) {
            if (notificationTarget != null) onNotificationTargetHandled()
        }
        AcceptConditionsScreen(onTapAccept = viewModel::accept)
    }
}