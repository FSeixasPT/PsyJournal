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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isaakhanimann.journal.data.supplements.ActiveRoutine
import com.isaakhanimann.journal.data.supplements.ROLLSAFE_URL
import com.isaakhanimann.journal.data.supplements.RoutineKind
import com.isaakhanimann.journal.data.supplements.reminderTitle
import com.isaakhanimann.journal.data.supplements.remainingSteps
import com.isaakhanimann.journal.data.supplements.stepTime
import com.isaakhanimann.journal.data.supplements.stepsOf
import com.isaakhanimann.journal.data.supplements.takeLine
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.DatePickerButton
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.TimePickerButton
import com.isaakhanimann.journal.ui.utils.getInstant
import com.isaakhanimann.journal.ui.utils.getLocalDateTime
import com.isaakhanimann.journal.ui.utils.getStringOfPattern
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun clockText(instant: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(instant.atZone(ZoneId.systemDefault()))

// 4 h from now, rounded up to the next quarter hour
private fun defaultDoseTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
    val in4Hours = now.plusHours(4).withSecond(0).withNano(0)
    val extra = (15 - in4Hours.minute % 15) % 15
    return in4Hours.plusMinutes(extra.toLong())
}

// asks for the notification permission first; calls onAllowed only when reminders can be shown
@Composable
fun rememberNotificationGate(onBlocked: () -> Unit): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val action = pending
        pending = null
        if (isGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()) action?.invoke() else onBlocked()
    }
    return { action ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            needsPermission -> {
                pending = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> onBlocked()
            else -> action()
        }
    }
}

@Composable
fun SupplementRoutineSection(
    activeRoutine: ActiveRoutine?,
    onStart: (RoutineKind, Instant, Int) -> Unit,
    onChange: (Instant, Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSheetOpen by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }
    val gate = rememberNotificationGate(onBlocked = { isBlocked = true })
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (activeRoutine == null) {
            Text(
                "Hourly reminders for RollSafe's supplement routine, with how much water to drink.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = { isSheetOpen = true }) { Text("Plan…") }
        } else {
            RunningRoutine(activeRoutine)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { isSheetOpen = true }) { Text("Change") }
                OutlinedButton(onClick = onStop) { Text("Stop") }
            }
        }
        if (isBlocked) {
            Text("Allow notifications for PsyJournal to get the reminders.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (isSheetOpen) {
        RoutinePlanSheet(
            activeRoutine = activeRoutine,
            onDismiss = { isSheetOpen = false },
            onConfirm = { kind, dose, rate ->
                isSheetOpen = false
                gate {
                    isBlocked = false
                    if (activeRoutine != null && kind == activeRoutine.kind) onChange(dose, rate) else onStart(kind, dose, rate)
                }
            }
        )
    }
}

@Composable
private fun RunningRoutine(routine: ActiveRoutine) {
    val now = Instant.now()
    val next = remainingSteps(routine.kind, routine.doseTime, now).firstOrNull()
    if (next != null) {
        val title = reminderTitle(next, routine.doseTime, ::clockText).removePrefix("MDMA routine · ")
        Text(
            "Next: $title · ${takeLine(next)?.removePrefix("Take: ") ?: "water"}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
    Text("Water: ${routine.mlPerHour} ml an hour", style = MaterialTheme.typography.bodyMedium)
    stepsOf(routine.kind).forEach { step ->
        val time = stepTime(routine.doseTime, step)
        Text(
            "${clockText(time)}  ${takeLine(step)?.removePrefix("Take: ") ?: "water"}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(if (time.isBefore(now)) 0.5f else 1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutinePlanSheet(
    activeRoutine: ActiveRoutine?,
    onDismiss: () -> Unit,
    onConfirm: (RoutineKind, Instant, Int) -> Unit,
) {
    var dose by remember { mutableStateOf(activeRoutine?.doseTime?.getLocalDateTime() ?: defaultDoseTime()) }
    var kind by remember { mutableStateOf(activeRoutine?.kind ?: RoutineKind.REGULAR) }
    var isDancing by remember { mutableStateOf((activeRoutine?.mlPerHour ?: 250) >= 500) }
    var isConfirmingReplace by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Supplement routine", style = MaterialTheme.typography.titleLarge)
            Text("When will you take MDMA?", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerButton(
                    localDateTime = dose,
                    onChange = { dose = dose.withYear(it.year).withDayOfYear(it.dayOfYear) },
                    dateString = dose.getStringOfPattern("EEE d MMM"),
                    modifier = Modifier.weight(1f)
                )
                TimePickerButton(
                    localDateTime = dose,
                    onChange = { dose = dose.withHour(it.hour).withMinute(it.minute) },
                    timeString = clockText(dose.getInstant()),
                    modifier = Modifier.weight(1f)
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RoutineKind.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = kind == option,
                        onClick = { kind = option },
                        shape = SegmentedButtonDefaults.itemShape(index, RoutineKind.entries.size)
                    ) { Text(if (option == RoutineKind.REGULAR) "Regular" else "Minimal") }
                }
            }
            Text(
                if (kind == RoutineKind.REGULAR) {
                    "Pills from 4 h before to 7 h after the dose."
                } else {
                    "Three pills before the event (from 13 h before), then only water reminders."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            val doseInstant = dose.getInstant()
            val firstStep = stepsOf(kind).first()
            if (stepTime(doseInstant, firstStep).isBefore(Instant.now())) {
                val firstLeft = remainingSteps(kind, doseInstant, Instant.now()).firstOrNull()
                Text(
                    if (firstLeft != null) "Steps before ${clockText(stepTime(doseInstant, firstLeft))} are skipped." else "All steps are already past.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dancing or somewhere hot?", style = MaterialTheme.typography.bodyLarge)
                    Text(if (isDancing) "About 500 ml of water an hour" else "About 250 ml of water an hour", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = isDancing, onCheckedChange = { isDancing = it })
            }
            Text(
                "The evidence for these supplements comes from animal studies, and RollSafe earns from some of the supplement links on its site. Supplements don't make MDMA safe.",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { uriHandler.openUri(ROLLSAFE_URL) }) { Text("RollSafe") }
            Button(
                onClick = {
                    if (activeRoutine != null && activeRoutine.kind != kind) {
                        isConfirmingReplace = true
                    } else {
                        onConfirm(kind, doseInstant, if (isDancing) 500 else 250)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (activeRoutine == null) "Start" else "Save") }
        }
    }
    if (isConfirmingReplace) {
        AlertDialog(
            onDismissRequest = { isConfirmingReplace = false },
            title = { Text("Replace the running routine?") },
            text = { Text("Only one routine runs at a time.") },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingReplace = false
                    onConfirm(kind, dose.getInstant(), if (isDancing) 500 else 250)
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { isConfirmingReplace = false }) { Text("Cancel") } }
        )
    }
}
