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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import com.isaakhanimann.journal.ui.utils.getShortTimeText
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.roundToInt

class PlannedIngestionState(
    // the chart to draw: with the planned ingestion while held or pinned, otherwise the real one
    val displayedModel: AllTimelinesModel,
    val realModel: AllTimelinesModel,
    val planned: PlannedIngestion?,
    val plannedLine: DataForOneEffectLine?,
    val isPinned: Boolean,
    val isPossibleNow: Boolean,
    val now: Instant,
    val experienceSubstanceNamesByRecency: List<String>,
    val experienceRoutesByRecency: (String) -> List<AdministrationRoute>,
    val setHeld: (Boolean) -> Unit,
    val choose: (PlannedIngestion) -> Unit,
    val clearChoice: () -> Unit,
)

@Composable
fun rememberCurrentTime(): Instant {
    var currentTime by remember { mutableStateOf(Instant.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        currentTime = Instant.now()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            currentTime = Instant.now()
        }
    }
    return currentTime
}

@Composable
fun rememberPlannedIngestionState(realModel: AllTimelinesModel, actions: PlannedIngestionActions): PlannedIngestionState {
    val now = rememberCurrentTime()
    val pin = actions.pinnedRedoseFlow.collectAsState().value
    val chosen = actions.chosenPlanFlow.collectAsState().value
    var isHeld by remember { mutableStateOf(false) }
    val realLines = realModel.realLines
    val defaultPlan = remember(realLines) { actions.defaultPlan(realLines) }
    val pinnedPlan = pin?.let { realModel.resolvePinnedPlan(it, now) }
    val planned = pinnedPlan ?: chosen ?: defaultPlan
    val plannedLine = remember(planned, now, realModel) { planned?.let { actions.buildLine(it, now, realModel.colors) } }
    val isPinInvalid = pin != null && (pinnedPlan == null || plannedLine == null)
    val currentActions by rememberUpdatedState(actions)
    LaunchedEffect(isPinInvalid) {
        if (isPinInvalid) currentActions.pin(null)
    }
    val isPreviewing = (isHeld || pinnedPlan != null) && plannedLine != null
    val displayedModel = remember(realModel, plannedLine, isPreviewing) {
        if (isPreviewing) realModel.withPlannedIngestion(plannedLine!!) else realModel
    }
    val linesByRecency = realLines.sortedByDescending { it.startTime }
    return PlannedIngestionState(
        displayedModel = displayedModel,
        realModel = realModel,
        planned = planned,
        plannedLine = plannedLine,
        isPinned = pinnedPlan != null,
        isPossibleNow = now < realModel.startTime.plusSeconds(realModel.widthInSeconds.toLong()),
        now = now,
        experienceSubstanceNamesByRecency = linesByRecency.map { it.substanceName }.distinct(),
        experienceRoutesByRecency = { name -> linesByRecency.filter { it.substanceName == name }.map { it.route }.distinct() },
        setHeld = { isHeld = it },
        choose = { newPlan ->
            actions.choose(newPlan)
            if (pinnedPlan != null) actions.pin(newPlan)
        },
        clearChoice = { actions.choose(null) },
    )
}

@Composable
fun PlannedIngestionSection(
    state: PlannedIngestionState,
    actions: PlannedIngestionActions,
    selectedTime: Instant?,
    // opens the normal add-ingestion flow with the planned ingestion filled in
    onChangeDetails: (PlannedIngestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var isConfirmingLog by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Hold to preview taking now:", style = MaterialTheme.typography.labelMedium)
        PlannedIngestionChip(
            state = state,
            onEdit = { isEditing = true },
            onTogglePin = {
                if (state.isPinned) {
                    // keep showing the unpinned plan instead of falling back to the default
                    actions.choose(state.planned)
                    actions.pin(null)
                } else {
                    actions.pin(state.planned)
                }
            },
            onTookIt = { isConfirmingLog = true }
        )
        // both lines are always laid out so that nothing moves when they appear
        val model = state.displayedModel
        val lines = if (model.isPlannedRedose) {
            val fractionLine = model.plannedFractionOfPeakAt(selectedTime ?: state.now)?.let {
                val time = selectedTime?.let { time -> "at ${time.getShortTimeText()}" } ?: "now"
                "With redose: ${(it * 100).roundToInt()}% of peak $time"
            }
            val peakLine = model.plannedPeak?.let {
                val route = it.route?.let { route -> " ${route.displayText.lowercase()}" } ?: ""
                "Peak after redose: ≈ ${it.doseEquivalent.toReadableString()} ${it.units}$route equivalent at ${it.time.getShortTimeText()}"
            }
            listOfNotNull(fractionLine, peakLine)
        } else {
            // a substance not taken yet in this experience: when its effects would happen, to plan the overlap
            listOfNotNull(model.plannedEffectTimes?.let {
                "If taken now: starts ≈ ${it.start.getShortTimeText()} · peaks ${it.peakStart.getShortTimeText()}–${it.peakEnd.getShortTimeText()} · ends ≈ ${it.end.getShortTimeText()}"
            })
        }
        Text(
            text = lines.joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            minLines = 2
        )
    }
    if (isEditing) {
        PlannedIngestionEditorSheet(
            initial = state.planned,
            state = state,
            actions = actions,
            onDismiss = { isEditing = false },
            onDone = {
                state.choose(it)
                isEditing = false
            }
        )
    }
    val planned = state.planned
    if (isConfirmingLog && planned != null) {
        QuickLogDialog(
            planned = planned,
            color = state.plannedLine?.color ?: AdaptiveColor.entries.first(),
            now = state.now,
            interactions = actions.interactions(planned, state.experienceSubstanceNamesByRecency),
            onDismiss = { isConfirmingLog = false },
            onChangeDetails = {
                actions.prepareChangeDetails(state.realModel.realLines.maxOfOrNull { it.startTime })
                isConfirmingLog = false
                onChangeDetails(planned)
            },
            onLog = {
                actions.log(planned, state.plannedLine?.color ?: AdaptiveColor.entries.first())
                state.clearChoice()
                isConfirmingLog = false
            }
        )
    }
}

@Composable
private fun PlannedIngestionChip(
    state: PlannedIngestionState,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onTookIt: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val planned = state.planned
    val currentSetHeld by rememberUpdatedState(state.setHeld)
    val canPreview = planned != null && state.plannedLine != null
    var isPressed by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = if (isPressed || state.isPinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pointerInput(canPreview) {
                        if (!canPreview) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                currentSetHeld(true)
                                tryAwaitRelease()
                                isPressed = false
                                currentSetHeld(false)
                            }
                        )
                    }
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
            ) {
                val color = state.plannedLine?.color
                if (color != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color.getComposeColor(isDarkTheme), CircleShape)
                    )
                }
                Text(
                    text = planned?.chipText() ?: "Choose a substance to preview",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            if (canPreview) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (state.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (state.isPinned) "Unpin preview" else "Pin preview",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "Change planned ingestion", modifier = Modifier.size(18.dp))
            }
            if (canPreview) {
                IconButton(onClick = onTookIt, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "I took it now", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlannedIngestionEditorSheet(
    initial: PlannedIngestion?,
    state: PlannedIngestionState,
    actions: PlannedIngestionActions,
    onDismiss: () -> Unit,
    onDone: (PlannedIngestion) -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    var substanceName by remember { mutableStateOf(initial?.substanceName) }
    var route by remember { mutableStateOf(initial?.route) }
    var doseText by remember { mutableStateOf(initial?.dose?.let { doseFieldText(it) } ?: "") }
    var searchText by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val selectedName = substanceName
            val substance = selectedName?.let { actions.getSubstance(it) }
            if (selectedName == null || substance == null) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search substance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val entries = actions.searchSubstances(searchText, state.experienceSubstanceNamesByRecency)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    val inExperience = entries.filter { it.isInExperience }
                    val others = entries.filter { !it.isInExperience }
                    if (inExperience.isNotEmpty()) {
                        item { Text("In this experience", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp)) }
                        items(inExperience) { entry -> PickerRow(entry, actions.companionColor(entry.substanceName), isDarkTheme) { pick(entry, actions, state) { name, newRoute, dose -> substanceName = name; route = newRoute; doseText = dose } } }
                    }
                    if (others.isNotEmpty()) {
                        item { Text("All substances", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp)) }
                        items(others) { entry -> PickerRow(entry, actions.companionColor(entry.substanceName), isDarkTheme) { pick(entry, actions, state) { name, newRoute, dose -> substanceName = name; route = newRoute; doseText = dose } } }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { substanceName = null }) { Text("Change") }
                }
                Text("Route", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    substance.roas.forEach { roa ->
                        val problem = roa.previewProblem()
                        FilterChip(
                            selected = route == roa.route,
                            enabled = problem == null,
                            onClick = {
                                route = roa.route
                                val roaDose = roa.roaDose
                                if (roaDose != null) {
                                    doseText = suggestedDose(selectedName, roa.route, state.realModel.realLines, roaDose)?.let { doseFieldText(it) } ?: ""
                                }
                            },
                            label = {
                                Column {
                                    Text(roa.route.displayText)
                                    when (problem) {
                                        PreviewProblem.NO_FULL_DURATION -> Text("no full duration data", style = MaterialTheme.typography.labelSmall)
                                        PreviewProblem.NO_DOSE_RANGES -> Text("no dose ranges", style = MaterialTheme.typography.labelSmall)
                                        null -> {}
                                    }
                                }
                            }
                        )
                    }
                }
                val roaDose = route?.let { substance.getRoa(it)?.roaDose }
                OutlinedTextField(
                    value = doseText,
                    onValueChange = { doseText = it },
                    label = { Text("Dose") },
                    suffix = { Text(roaDose?.units ?: "") },
                    isError = parseDose(doseText) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (roaDose != null) {
                    Text(roaDose.rangesText(), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    val dose = parseDose(doseText)
                    val selectedRoute = route
                    Button(
                        enabled = dose != null && selectedRoute != null && roaDose != null,
                        onClick = {
                            if (dose != null && selectedRoute != null && roaDose != null) {
                                onDone(PlannedIngestion(selectedName, selectedRoute, dose, roaDose.units))
                            }
                        }
                    ) { Text("Done") }
                }
            }
        }
    }
}

// selecting a substance pre-selects its route and pre-fills the dose
private fun pick(
    entry: PickerEntry,
    actions: PlannedIngestionActions,
    state: PlannedIngestionState,
    apply: (String, AdministrationRoute?, String) -> Unit,
) {
    val substance = actions.getSubstance(entry.substanceName) ?: return
    val route = preselectedRoute(substance, state.experienceRoutesByRecency(entry.substanceName))
    val roaDose = route?.let { substance.getRoa(it)?.roaDose }
    val dose = roaDose?.let { suggestedDose(entry.substanceName, route, state.realModel.realLines, it) }
    apply(entry.substanceName, route, dose?.let { doseFieldText(it) } ?: "")
}

@Composable
private fun PickerRow(entry: PickerEntry, color: AdaptiveColor?, isDarkTheme: Boolean, onClick: () -> Unit) {
    val isEnabled = entry.problem == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color?.getComposeColor(isDarkTheme) ?: MaterialTheme.colorScheme.surface, CircleShape)
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            val textColor = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            Text(entry.substanceName, color = textColor)
            when (entry.problem) {
                PreviewProblem.NO_FULL_DURATION -> Text("Can't be previewed: no full duration data for any route", style = MaterialTheme.typography.labelSmall, color = textColor)
                PreviewProblem.NO_DOSE_RANGES -> Text("Can't be previewed: no dose ranges for any route", style = MaterialTheme.typography.labelSmall, color = textColor)
                null -> {}
            }
        }
    }
}

@Composable
private fun QuickLogDialog(
    planned: PlannedIngestion,
    color: AdaptiveColor,
    now: Instant,
    interactions: List<com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.Interaction>,
    onDismiss: () -> Unit,
    onChangeDetails: () -> Unit,
    onLog: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log ingestion now?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(color.getComposeColor(isDarkTheme), CircleShape))
                    Text(planned.chipText(), modifier = Modifier.padding(start = 6.dp))
                }
                Text("at ${now.getShortTimeText()}", style = MaterialTheme.typography.bodySmall)
                interactions.forEach { interaction ->
                    Text(
                        "⚠ ${interaction.interactionType.name.lowercase().replaceFirstChar { it.uppercase() }} interaction with ${interaction.bName}",
                        color = interaction.interactionType.color
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onLog) { Text("Log") } },
        dismissButton = {
            Row {
                TextButton(onClick = onChangeDetails) { Text("Change details") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
