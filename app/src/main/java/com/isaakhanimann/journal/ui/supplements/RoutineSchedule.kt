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

package com.isaakhanimann.journal.ui.supplements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.supplements.ActiveRoutine
import com.isaakhanimann.journal.data.supplements.RoutineStep
import com.isaakhanimann.journal.data.supplements.StepStatus
import com.isaakhanimann.journal.data.supplements.currentStep
import com.isaakhanimann.journal.data.supplements.missedSteps
import com.isaakhanimann.journal.data.supplements.notMarkedCount
import com.isaakhanimann.journal.data.supplements.reminderTitle
import com.isaakhanimann.journal.data.supplements.remainingSteps
import com.isaakhanimann.journal.data.supplements.stepStatus
import com.isaakhanimann.journal.data.supplements.stepTime
import com.isaakhanimann.journal.data.supplements.stepsOf
import com.isaakhanimann.journal.data.supplements.takeLine
import com.isaakhanimann.journal.data.supplements.waterSoFarText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val notMarkedColor = Color(0xFFE0A030)

fun clockText(instant: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(instant.atZone(ZoneId.systemDefault()))

// so "Next" and the water total move on while the screen stays open
@Composable
fun rememberTickingNow(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }
    return now
}

// next step, water so far and how many past steps aren't marked done
@Composable
fun RoutineSummary(routine: ActiveRoutine, now: Instant) {
    // the step to take now comes first, until it's marked done
    currentStep(routine, now)?.let { current ->
        val title = reminderTitle(current, routine.doseTime, ::clockText).removePrefix("MDMA routine · ")
        Text(
            "Now: $title · ${takeLine(current)?.removePrefix("Take: ").orEmpty()}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = notMarkedColor
        )
    }
    remainingSteps(routine.kind, routine.doseTime, now).firstOrNull()?.let { next ->
        val title = reminderTitle(next, routine.doseTime, ::clockText).removePrefix("MDMA routine · ")
        Text(
            "Next: $title · ${takeLine(next)?.removePrefix("Take: ").orEmpty()}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
    waterSoFarText(routine.doseTime, now, routine.mlPerHour)?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    val notMarked = notMarkedCount(routine, now)
    if (notMarked > 0) {
        Text(
            if (notMarked == 1) "1 step not marked done" else "$notMarked steps not marked done",
            style = MaterialTheme.typography.bodyMedium,
            color = notMarkedColor
        )
    }
}

@Composable
fun RoutineSchedule(routine: ActiveRoutine, now: Instant, onToggle: (RoutineStep, Boolean) -> Unit) {
    Column {
        stepsOf(routine.kind).forEach { step ->
            val status = stepStatus(routine, step, now)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (status == StepStatus.SKIPPED) 0.5f else 1f)
            ) {
                Checkbox(checked = status == StepStatus.DONE, onCheckedChange = { onToggle(step, it) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${clockText(stepTime(routine.doseTime, step))}  ${takeLine(step)?.removePrefix("Take: ").orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    when (status) {
                        StepStatus.NOT_MARKED -> Text("Not marked done", style = MaterialTheme.typography.bodySmall, color = notMarkedColor)
                        StepStatus.SKIPPED -> Text("Skipped: already past when started", style = MaterialTheme.typography.bodySmall)
                        else -> {}
                    }
                }
            }
        }
    }
}

// earlier steps not marked done, to tick off what was taken; never a nudge to catch up
@Composable
private fun MissedSteps(routine: ActiveRoutine, now: Instant, onToggle: (RoutineStep, Boolean) -> Unit) {
    val missed = missedSteps(routine, now)
    if (missed.isEmpty()) return
    Text(
        "Not marked done. Tick the ones you took, and don't double up to catch up.",
        style = MaterialTheme.typography.bodySmall
    )
    missed.forEach { step ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = false, onCheckedChange = { onToggle(step, it) })
            Text(
                "${clockText(stepTime(routine.doseTime, step))}  ${takeLine(step)?.removePrefix("Take: ").orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = notMarkedColor
            )
        }
    }
}

// under the chart of the experience the routine belongs to
@Composable
fun SupplementRoutineCard(
    routine: ActiveRoutine,
    onToggle: (RoutineStep, Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberTickingNow()
    var isScheduleShown by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Supplement routine", style = MaterialTheme.typography.titleMedium)
            RoutineSummary(routine, now)
            if (!isScheduleShown) MissedSteps(routine, now, onToggle)
            if (isScheduleShown) RoutineSchedule(routine, now, onToggle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { isScheduleShown = !isScheduleShown }) {
                    Text(if (isScheduleShown) "Hide schedule" else "Schedule")
                }
                TextButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}
