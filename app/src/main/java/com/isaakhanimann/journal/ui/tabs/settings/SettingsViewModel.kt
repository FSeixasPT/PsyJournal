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

package com.isaakhanimann.journal.ui.tabs.settings

import kotlinx.coroutines.flow.MutableStateFlow
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.LiveNotificationController
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val experienceRepository: ExperienceRepository,
    private val fileSystemConnection: FileSystemConnection,
    private val userPreferences: UserPreferences,
    private val liveNotificationController: LiveNotificationController,
) : ViewModel() {

    val isRoutineSuggestedFlow = userPreferences.isRoutineSuggestedFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveIsRoutineSuggested(value: Boolean) = viewModelScope.launch { userPreferences.saveIsRoutineSuggested(value) }

    val isLiveAutoStartFlow = userPreferences.isLiveAutoStartFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveIsLiveAutoStart(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsLiveAutoStart(value)
    }

    val isLiveShownOnLockScreenFlow = userPreferences.isLiveShownOnLockScreenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    // a live notification takes the new lock-screen choice right away
    fun saveIsLiveShownOnLockScreen(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsLiveShownOnLockScreen(value)
        liveNotificationController.refresh()
    }

    fun saveDosageDotsAreHidden(value: Boolean) = viewModelScope.launch {
        userPreferences.saveDosageDotsAreHidden(value)
    }

    fun saveAreSubstanceHeightsIndependent(value: Boolean) = viewModelScope.launch {
        userPreferences.saveAreSubstanceHeightsIndependent(value)
    }

    fun saveAreRoutesMerged(value: Boolean) = viewModelScope.launch {
        userPreferences.saveAreRoutesMerged(value)
    }

    fun saveIsDurationRangeShown(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsDurationRangeShown(value)
    }

    fun saveIsChartPinned(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsChartPinned(value)
    }

    fun saveIsTimelineHidden(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsTimelineHidden(value)
    }

    val isTimelineHiddenFlow = userPreferences.isTimelineHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val areSubstanceHeightsIndependentFlow = userPreferences.areSubstanceHeightsIndependentFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val areRoutesMergedFlow = userPreferences.areRoutesMergedFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val isDurationRangeShownFlow = userPreferences.isDurationRangeShownFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val isChartPinnedFlow = userPreferences.isChartPinnedFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val areDosageDotsHiddenFlow = userPreferences.areDosageDotsHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val snackbarHostState = SnackbarHostState()

    // a read export waiting for the user to confirm replacing the journal, with the current journal's size
    val pendingImportFlow = MutableStateFlow<PendingImport?>(null)

    // reads the file first; nothing is replaced until the user has seen what it contains
    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val text = fileSystemConnection.getTextFromUri(uri)
            if (text == null) {
                snackbarHostState.showSnackbar(message = "File not found", duration = SnackbarDuration.Short)
                return@launch
            }
            when (val read = readImportFile(text)) {
                is ImportFile.Export -> pendingImportFlow.value = PendingImport(
                    read,
                    currentExperiences = experienceRepository.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted().size
                )

                ImportFile.NotAnExport -> snackbarHostState.showSnackbar(
                    message = "This file isn't a PsyJournal export. Nothing was changed.",
                    duration = SnackbarDuration.Long
                )

                ImportFile.Unreadable -> snackbarHostState.showSnackbar(
                    message = "This file couldn't be read. Nothing was changed.",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    fun cancelImport() {
        pendingImportFlow.value = null
    }

    fun confirmImport() {
        val pending = pendingImportFlow.value ?: return
        pendingImportFlow.value = null
        viewModelScope.launch {
            try {
                // the imported journal replaces the experience the notification follows
                liveNotificationController.stop()
                experienceRepository.deleteEverything()
                experienceRepository.insertEverything(pending.file.journal)
                snackbarHostState.showSnackbar(message = "Import successful", duration = SnackbarDuration.Short)
            } catch (e: Exception) {
                println("Error when importing: ${e.message}")
                snackbarHostState.showSnackbar(message = "Import failed", duration = SnackbarDuration.Short)
            }
        }
    }

    fun exportFile(uri: Uri) {
        viewModelScope.launch {
            val experiencesWithIngestionsAndRatings =
                experienceRepository.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted()
            val experiencesSerializable = experiencesWithIngestionsAndRatings.map {
                val location = it.experience.location
                return@map ExperienceSerializable(
                    title = it.experience.title,
                    text = it.experience.text,
                    creationDate = it.experience.creationDate,
                    sortDate = it.experience.sortDate,
                    isFavorite = it.experience.isFavorite,
                    ingestions = it.ingestions.map { ingestion ->
                        IngestionSerializable(
                            substanceName = ingestion.substanceName,
                            time = ingestion.time,
                            endTime = ingestion.endTime,
                            creationDate = ingestion.creationDate,
                            administrationRoute = ingestion.administrationRoute,
                            dose = ingestion.dose,
                            estimatedDoseStandardDeviation = ingestion.estimatedDoseStandardDeviation,
                            isDoseAnEstimate = ingestion.isDoseAnEstimate,
                            units = ingestion.units,
                            notes = ingestion.notes,
                            stomachFullness = ingestion.stomachFullness,
                            consumerName = ingestion.consumerName,
                            customUnitId = ingestion.customUnitId,
                            isPartOfTimeline = ingestion.isPartOfTimeline
                        )
                    },
                    location = if (location != null) {
                        LocationSerializable(
                            name = location.name,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    } else {
                        null
                    },
                    ratings = it.ratings.map { rating ->
                        RatingSerializable(
                            option = rating.option,
                            time = rating.time,
                            creationDate = rating.creationDate,
                            isPartOfTimeline = rating.isPartOfTimeline
                        )
                    },
                    timedNotes = it.timedNotes.map { timedNote ->
                        TimedNoteSerializable(
                            creationDate = timedNote.creationDate,
                            time = timedNote.time,
                            note = timedNote.note,
                            color = timedNote.color,
                            isPartOfTimeline = timedNote.isPartOfTimeline
                        )
                    }
                )
            }
            val customUnitsSerializable = experienceRepository.getAllCustomUnitsSorted().map {
                CustomUnitSerializable(
                    id = it.id,
                    substanceName = it.substanceName,
                    name = it.name,
                    creationDate = it.creationDate,
                    administrationRoute = it.administrationRoute,
                    dose = it.dose,
                    estimatedDoseStandardDeviation = it.estimatedDoseStandardDeviation,
                    isEstimate = it.isEstimate,
                    isArchived = it.isArchived,
                    unit = it.unit,
                    unitPlural = it.unitPlural,
                    originalUnit = it.originalUnit,
                    note = it.note
                )
            }
            val journalExport = JournalExport(
                experiences = experiencesSerializable,
                substanceCompanions = experienceRepository.getAllSubstanceCompanions(),
                customSubstances = experienceRepository.getAllCustomSubstances(),
                customUnits = customUnitsSerializable
            )
            try {
                val jsonList = Json.encodeToString(journalExport)
                fileSystemConnection.saveTextInUri(uri, text = jsonList)
                snackbarHostState.showSnackbar(
                    message = "Export successful",
                    duration = SnackbarDuration.Short
                )
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(
                    message = "Export failed",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun deleteEverything() {
        viewModelScope.launch {
            liveNotificationController.stop()
            experienceRepository.deleteEverything()
        }
    }
}

data class PendingImport(val file: ImportFile.Export, val currentExperiences: Int)
