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

import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility
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
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.roundToSignificantDigits
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
    val customUnits: List<CustomUnit>,
    val routeVisibility: RouteVisibility,
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
    val customUnits = actions.customUnitsFlow.collectAsState().value
    val routeVisibility = actions.routeVisibilityFlow.collectAsState().value
    val planned = (pinnedPlan ?: chosen ?: defaultPlan)?.withCurrentCustomUnit(customUnits)
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
        customUnits = customUnits,
        routeVisibility = routeVisibility,
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
    // the time display option chosen with the stopwatch, used for the times below the chip
    timeDisplayOption: TimeDisplayOption,
    // opens the normal add-ingestion flow with the planned ingestion filled in
    onChangeDetails: (PlannedIngestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    // the planned ingestion and its color when ✓ was tapped, so the dialog logs exactly what it shows
    var logSnapshot by remember { mutableStateOf<Pair<PlannedIngestion, AdaptiveColor>?>(null) }
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
            onTookIt = {
                state.planned?.let { logSnapshot = it to (state.plannedLine?.color ?: AdaptiveColor.entries.first()) }
            }
        )
        // both lines are always laid out so that nothing moves when they appear
        val model = state.displayedModel
        val experienceStart = state.realModel.startTime
        val savedTimeDisplayOption = actions.savedTimeDisplayOptionFlow.collectAsState().value
        val chipOption = chipTimeDisplayOption(savedTimeDisplayOption, resolved = timeDisplayOption)
        fun timeText(time: Instant) = plannedTimeText(time, state.now, experienceStart, chipOption)
        val lines = if (model.isPlannedRedose) {
            val fractionLine = model.plannedFractionOfPeakAt(selectedTime ?: state.now)?.let {
                val time = selectedTime?.let { time -> timeText(time) } ?: "now"
                "With redose: ${(it * 100).roundToInt()}% of peak $time"
            }
            val peakLine = model.plannedPeak?.let { peak ->
                val isMerged = peak.doseEquivalents.size > 1
                // e.g. "20 mg oral / 8 mg insufflated"; the route is only named if there are several
                val equivalents = peak.doseEquivalents.joinToString(" / ") {
                    val route = if (isMerged) " ${it.route.displayText.lowercase()}" else ""
                    "${it.dose.toReadableString()} ${it.units}$route"
                }
                "Peak after redose: $equivalents equivalent ${timeText(peak.time)}"
            }
            listOfNotNull(fractionLine, peakLine)
        } else {
            // a substance not taken yet in this experience: when its effects would happen, to plan the overlap
            listOfNotNull(model.plannedEffectTimes?.let {
                val peaks = plannedTimeRangeText(it.peakStart, it.peakEnd, state.now, experienceStart, chipOption)
                "If taken now: starts ${timeText(it.start)} · peaks $peaks · ends ${timeText(it.end)}"
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
    val snapshot = logSnapshot
    if (snapshot != null) {
        val (planned, color) = snapshot
        val names = state.experienceSubstanceNamesByRecency
        val interactions = remember(planned, names) { actions.interactions(planned, names) }
        QuickLogDialog(
            planned = planned,
            color = color,
            now = state.now,
            interactions = interactions,
            onDismiss = { logSnapshot = null },
            onChangeDetails = {
                actions.prepareChangeDetails(state.realModel.realLines.maxOfOrNull { it.startTime })
                logSnapshot = null
                onChangeDetails(planned)
            },
            onLog = {
                actions.log(planned, color)
                state.clearChoice()
                logSnapshot = null
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
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    // also when the gesture is cancelled, e.g. because the chip changed mid-press
                                    isPressed = false
                                    currentSetHeld(false)
                                }
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
    var customUnitId by remember { mutableStateOf(initial?.customUnit?.id) }
    var doseText by remember { mutableStateOf(initial?.let { doseFieldText(it.customUnit?.count ?: it.dose) } ?: "") }
    // puts a suggested plan into the fields, its custom unit only if it can still be used
    fun applySuggestion(plan: PlannedIngestion?) {
        val usableUnit = plan?.customUnit?.takeIf { unit -> state.customUnits.any { it.id == unit.id } }
        customUnitId = usableUnit?.id
        doseText = plan?.let { doseFieldText(usableUnit?.count ?: it.dose) } ?: ""
    }
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
                val names = state.experienceSubstanceNamesByRecency
                // only searched again when the text or the substances of the experience change
                val entries = remember(searchText, names) { actions.searchSubstances(searchText, names) }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    val inExperience = entries.filter { it.isInExperience }
                    val others = entries.filter { !it.isInExperience }
                    if (inExperience.isNotEmpty()) {
                        item { Text("In this experience", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp)) }
                        items(inExperience) { entry -> PickerRow(entry, actions.companionColor(entry.substanceName), isDarkTheme) { pick(entry, actions, state) { name, newRoute, plan -> substanceName = name; route = newRoute; applySuggestion(plan) } } }
                    }
                    if (others.isNotEmpty()) {
                        item { Text("All substances", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp)) }
                        items(others) { entry -> PickerRow(entry, actions.companionColor(entry.substanceName), isDarkTheme) { pick(entry, actions, state) { name, newRoute, plan -> substanceName = name; route = newRoute; applySuggestion(plan) } } }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { substanceName = null }) { Text("Change") }
                }
                Text("Route", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    editorRoutes(substance, state.routeVisibility, plannedRoute = initial?.route).forEach { roa ->
                        val problem = roa.previewProblem()
                        FilterChip(
                            selected = route == roa.route,
                            enabled = problem == null,
                            onClick = {
                                route = roa.route
                                val roaDose = roa.roaDose
                                if (roaDose != null) {
                                    applySuggestion(suggestedPlan(selectedName, roa.route, state.realModel.realLines, roaDose))
                                }
                            },
                            label = {
                                Column {
                                    Text(roa.route.displayText)
                                    when (problem) {
                                        PreviewProblem.NO_FULL_DURATION -> Text("no full duration data", style = MaterialTheme.typography.labelSmall)
                                        PreviewProblem.NO_DOSE_RANGES -> Text("no dose ranges", style = MaterialTheme.typography.labelSmall)
                                        PreviewProblem.ROUTES_HIDDEN -> {}
                                        null -> {}
                                    }
                                }
                            }
                        )
                    }
                }
                val roaDose = route?.let { substance.getRoa(it)?.roaDose }
                val selectedRouteForUnits = route
                val offeredUnits = if (roaDose != null && selectedRouteForUnits != null) {
                    offeredCustomUnits(selectedName, selectedRouteForUnits, roaDose.units, state.customUnits)
                } else {
                    emptyList()
                }
                val selectedUnit = offeredUnits.firstOrNull { it.id == customUnitId }
                if (roaDose != null && offeredUnits.isNotEmpty()) {
                    Text("Unit", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedUnit == null,
                            onClick = {
                                // keep the same amount: the count of the custom unit becomes its pure dose
                                val count = parseDose(doseText)
                                if (selectedUnit != null && count != null) doseText = doseFieldText(count * selectedUnit.dose!!)
                                customUnitId = null
                            },
                            label = { Text(roaDose.units) }
                        )
                        offeredUnits.forEach { unit ->
                            FilterChip(
                                selected = selectedUnit?.id == unit.id,
                                onClick = {
                                    val amount = parseDose(doseText)
                                    if (amount != null) {
                                        val pureDose = if (selectedUnit != null) amount * selectedUnit.dose!! else amount
                                        doseText = doseFieldText(roundToSignificantDigits(pureDose / unit.dose!!, 3))
                                    }
                                    customUnitId = unit.id
                                },
                                label = { Text("${unit.name} (${unit.unit})") }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = doseText,
                    onValueChange = { doseText = it },
                    label = { Text(if (selectedUnit != null) "Amount" else "Dose") },
                    suffix = { Text(selectedUnit?.getPluralizableUnit()?.plural ?: roaDose?.units ?: "") },
                    isError = parseDose(doseText) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                val count = parseDose(doseText)
                if (selectedUnit != null && count != null && roaDose != null) {
                    Text(
                        "= ${roundToSignificantDigits(count * selectedUnit.dose!!, 6).toReadableString()} ${roaDose.units}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                                val unit = selectedUnit
                                onDone(
                                    if (unit != null) {
                                        PlannedIngestion(
                                            substanceName = selectedName,
                                            route = selectedRoute,
                                            dose = roundToSignificantDigits(dose * unit.dose!!, 6),
                                            units = roaDose.units,
                                            customUnit = PlannedCustomUnit(unit.id, dose, unit.unit, unit.getPluralizableUnit().plural)
                                        )
                                    } else {
                                        PlannedIngestion(selectedName, selectedRoute, dose, roaDose.units)
                                    }
                                )
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
    apply: (String, AdministrationRoute?, PlannedIngestion?) -> Unit,
) {
    val substance = actions.getSubstance(entry.substanceName) ?: return
    val route = preselectedRoute(substance, state.experienceRoutesByRecency(entry.substanceName), state.routeVisibility)
    val roaDose = route?.let { substance.getRoa(it)?.roaDose }
    apply(entry.substanceName, route, roaDose?.let { suggestedPlan(entry.substanceName, route, state.realModel.realLines, it) })
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
                PreviewProblem.ROUTES_HIDDEN -> Text("Can't be previewed: its previewable routes are hidden", style = MaterialTheme.typography.labelSmall, color = textColor)
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
