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

package com.isaakhanimann.journal.data.supplements

import java.time.Instant

// what the routine's notification shows: the step to take now, with Done, until it's marked done;
// otherwise, quietly, the next step
data class RoutineNotice(val step: RoutineStep, val isDue: Boolean, val title: String, val lines: List<String>)

// null once no step is left to show
fun routineNotice(
    routine: ActiveRoutine,
    now: Instant,
    lastMdmaDose: Instant,
    formatTime: (Instant) -> String,
    formatDayTime: (Instant) -> String,
): RoutineNotice? {
    currentStep(routine, now)?.let { due ->
        return RoutineNotice(
            step = due,
            isDue = true,
            title = reminderTitle(due, routine.doseTime, formatTime),
            lines = reminderLines(routine.kind, due, routine.doseTime, routine.mlPerHour, lastMdmaDose, formatTime, formatDayTime)
        )
    }
    val next = remainingSteps(routine.kind, routine.doseTime, now).firstOrNull() ?: return null
    val whenText = reminderTitle(next, routine.doseTime, formatTime).removePrefix("MDMA routine · ")
    return RoutineNotice(
        step = next,
        isDue = false,
        title = "MDMA routine · next: $whenText",
        lines = listOfNotNull(
            takeLine(next),
            waterSoFarText(routine.doseTime, now, routine.mlPerHour) ?: "Planned MDMA at ${formatTime(routine.doseTime)}."
        )
    )
}
