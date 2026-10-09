/*
 * Copyright (c) 2023. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.settings.combinations

import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.LiveExperience
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.PinnedRedose
import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteOverride
import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object PreferencesKeys {
        val KEY_TIME_DISPLAY_OPTION = stringPreferencesKey("key_time_display_option")

        // last ingestion time of experience is used when adding an ingestion from a past experience
        // cloned ingestion time is used to copy the time from another ingestion
        // those values need to be set/reset whenever an ingestion is added
        val KEY_LAST_INGESTION_OF_EXPERIENCE = longPreferencesKey("KEY_LAST_INGESTION_OF_EXPERIENCE")
        val KEY_CLONED_INGESTION_TIME = longPreferencesKey("KEY_CLONED_INGESTION_TIME")

        val KEY_HIDE_ORAL_DISCLAIMER = booleanPreferencesKey("key_hide_oral_disclaimer")
        val KEY_HIDE_DOSAGE_DOTS = booleanPreferencesKey("key_hide_dosage_dots")
        val KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT = booleanPreferencesKey("KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT")
        val KEY_ARE_ROUTES_MERGED = booleanPreferencesKey("KEY_ARE_ROUTES_MERGED")
        val KEY_PINNED_REDOSE = stringPreferencesKey("KEY_PINNED_REDOSE")
        val KEY_CHOSEN_PLAN = stringPreferencesKey("KEY_CHOSEN_PLAN")
        val KEY_HIDDEN_ROUTES = stringSetPreferencesKey("KEY_HIDDEN_ROUTES")
        val KEY_ROUTE_OVERRIDES = stringSetPreferencesKey("KEY_ROUTE_OVERRIDES")
        val KEY_IS_TIMELINE_HIDDEN = booleanPreferencesKey("KEY_IS_TIMELINE_HIDDEN")
        val KEY_LIVE_EXPERIENCE = stringPreferencesKey("KEY_LIVE_EXPERIENCE")
        val KEY_LIVE_AUTO_START = booleanPreferencesKey("KEY_LIVE_AUTO_START")
        val KEY_LIVE_SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("KEY_LIVE_SHOW_ON_LOCK_SCREEN")
    }

    suspend fun saveTimeDisplayOption(value: SavedTimeDisplayOption) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_TIME_DISPLAY_OPTION] = value.name
        }
    }

    val savedTimeDisplayOptionFlow: Flow<SavedTimeDisplayOption> = dataStore.data
        .map { preferences ->
            val name = preferences[PreferencesKeys.KEY_TIME_DISPLAY_OPTION] ?: SavedTimeDisplayOption.AUTO.name
            SavedTimeDisplayOption.valueOf(name)
        }

    suspend fun saveLastIngestionTimeOfExperience(value: Instant?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_LAST_INGESTION_OF_EXPERIENCE] = value?.epochSecond ?: 0L
        }
    }

    val lastIngestionTimeOfExperienceFlow: Flow<Instant?> = dataStore.data
        .map { preferences ->
            val epochSecond = preferences[PreferencesKeys.KEY_LAST_INGESTION_OF_EXPERIENCE] ?: 0L
            if (epochSecond != 0L) {
                Instant.ofEpochSecond(epochSecond)
            } else {
                null
            }
        }

    suspend fun saveClonedIngestionTime(value: Instant?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_CLONED_INGESTION_TIME] = value?.epochSecond ?: 0L
        }
    }

    val clonedIngestionTimeFlow: Flow<Instant?> = dataStore.data
        .map { preferences ->
            val epochSecond = preferences[PreferencesKeys.KEY_CLONED_INGESTION_TIME] ?: 0L
            if (epochSecond != 0L) {
                Instant.ofEpochSecond(epochSecond)
            } else {
                null
            }
        }

    suspend fun saveOralDisclaimerIsHidden(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_ORAL_DISCLAIMER] = value
        }
    }

    val isOralDisclaimerHiddenFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_ORAL_DISCLAIMER] ?: false
        }

    suspend fun saveDosageDotsAreHidden(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_DOSAGE_DOTS] = value
        }
    }

    val areDosageDotsHiddenFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_DOSAGE_DOTS] ?: false
        }

    suspend fun saveAreSubstanceHeightsIndependent(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT] = value
        }
    }

    val areSubstanceHeightsIndependentFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT] ?: false
        }

    suspend fun saveAreRoutesMerged(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ARE_ROUTES_MERGED] = value
        }
    }

    val areRoutesMergedFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_ARE_ROUTES_MERGED] ?: true
        }

    // only one redose preview can be pinned at a time, across all experiences
    suspend fun savePinnedRedose(value: PinnedRedose?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(PreferencesKeys.KEY_PINNED_REDOSE)
            } else {
                preferences[PreferencesKeys.KEY_PINNED_REDOSE] = value.toPreferenceString()
            }
        }
    }

    private fun routeVisibilityOf(preferences: Preferences) = RouteVisibility(
        hiddenRoutes = (preferences[PreferencesKeys.KEY_HIDDEN_ROUTES] ?: emptySet())
            .mapNotNull { name -> AdministrationRoute.entries.firstOrNull { it.name == name } }
            .toSet(),
        overrides = (preferences[PreferencesKeys.KEY_ROUTE_OVERRIDES] ?: emptySet())
            .mapNotNull { RouteOverride.fromPreferenceString(it) }
            .toSet()
    )

    val routeVisibilityFlow: Flow<RouteVisibility> = dataStore.data.map { routeVisibilityOf(it) }.distinctUntilChanged()

    // reads and writes in one transaction, so concurrent changes are not lost
    suspend fun updateRouteVisibility(change: (RouteVisibility) -> RouteVisibility) {
        dataStore.edit { preferences ->
            val updated = change(routeVisibilityOf(preferences))
            preferences[PreferencesKeys.KEY_HIDDEN_ROUTES] = updated.hiddenRoutes.map { it.name }.toSet()
            preferences[PreferencesKeys.KEY_ROUTE_OVERRIDES] = updated.overrides.map { it.toPreferenceString() }.toSet()
        }
    }

    // the planned-ingestion chip's choice when it is not pinned, saved in the same format as a pin
    suspend fun saveChosenPlan(value: PinnedRedose?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(PreferencesKeys.KEY_CHOSEN_PLAN)
            } else {
                preferences[PreferencesKeys.KEY_CHOSEN_PLAN] = value.toPreferenceString()
            }
        }
    }

    val chosenPlanFlow: Flow<PinnedRedose?> = dataStore.data
        .map { preferences ->
            PinnedRedose.fromPreferenceString(preferences[PreferencesKeys.KEY_CHOSEN_PLAN])
        }

    val pinnedRedoseFlow: Flow<PinnedRedose?> = dataStore.data
        .map { preferences ->
            PinnedRedose.fromPreferenceString(preferences[PreferencesKeys.KEY_PINNED_REDOSE])
        }

    val isTimelineHiddenFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_IS_TIMELINE_HIDDEN] ?: false
        }

    suspend fun saveIsTimelineHidden(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_IS_TIMELINE_HIDDEN] = value
        }
    }

    // the experience shown in the live notification, if any
    val liveExperienceFlow: Flow<LiveExperience?> = dataStore.data
        .map { preferences -> LiveExperience.fromPreferenceString(preferences[PreferencesKeys.KEY_LIVE_EXPERIENCE]) }
        .distinctUntilChanged()

    suspend fun saveLiveExperience(value: LiveExperience?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(PreferencesKeys.KEY_LIVE_EXPERIENCE)
            } else {
                preferences[PreferencesKeys.KEY_LIVE_EXPERIENCE] = value.toPreferenceString()
            }
        }
    }

    val isLiveAutoStartFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.KEY_LIVE_AUTO_START] ?: false }

    suspend fun saveIsLiveAutoStart(value: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.KEY_LIVE_AUTO_START] = value }
    }

    val isLiveShownOnLockScreenFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.KEY_LIVE_SHOW_ON_LOCK_SCREEN] ?: false }

    suspend fun saveIsLiveShownOnLockScreen(value: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.KEY_LIVE_SHOW_ON_LOCK_SCREEN] = value }
    }
}
