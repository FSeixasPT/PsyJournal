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

import java.time.Duration
import java.time.Instant

enum class StepStatus { DONE, UPCOMING, NOT_MARKED, SKIPPED }

fun ActiveRoutine.withStepDone(step: RoutineStep, isDone: Boolean): ActiveRoutine {
    val offset = step.offset.toMinutes()
    return copy(doneOffsets = if (isDone) doneOffsets + offset else doneOffsets - offset)
}

// a past step that isn't marked done may have been missed; one already past when the routine started was skipped
fun stepStatus(routine: ActiveRoutine, step: RoutineStep, now: Instant): StepStatus {
    val time = stepTime(routine.doseTime, step)
    return when {
        step.offset.toMinutes() in routine.doneOffsets -> StepStatus.DONE
        time.isAfter(now) -> StepStatus.UPCOMING
        routine.skippedBefore?.let { !time.isAfter(it) } == true -> StepStatus.SKIPPED
        else -> StepStatus.NOT_MARKED
    }
}

fun notMarkedCount(routine: ActiveRoutine, now: Instant): Int =
    stepsOf(routine.kind).count { stepStatus(routine, it, now) == StepStatus.NOT_MARKED }

// a routine started from a dose belongs to the experience with that dose; a planned one to an experience
// with an MDMA dose of the user's own between its first step and its last step
fun routineBelongsTo(routine: ActiveRoutine, ownMdma: List<Pair<Int, Instant>>): Boolean {
    routine.linkedIngestionId?.let { id -> return ownMdma.any { it.first == id } }
    val from = routine.doseTime.minus(Duration.ofHours(4))
    val until = routine.doseTime.plus(Duration.ofHours(7))
    return ownMdma.any { (_, time) -> !time.isBefore(from) && !time.isAfter(until) }
}

// the step to take now: the latest one that's due, until it's marked done or a newer one is due
fun currentStep(routine: ActiveRoutine, now: Instant): RoutineStep? {
    val latestDue = stepsOf(routine.kind).lastOrNull { !stepTime(routine.doseTime, it).isAfter(now) } ?: return null
    return latestDue.takeIf { stepStatus(routine, it, now) == StepStatus.NOT_MARKED }
}

// earlier steps left unmarked, oldest first; the current step is shown on its own
fun missedSteps(routine: ActiveRoutine, now: Instant): List<RoutineStep> {
    val current = currentStep(routine, now)
    return stepsOf(routine.kind).filter { it != current && stepStatus(routine, it, now) == StepStatus.NOT_MARKED }
}

data class OwnDose(val ingestionId: Int, val time: Instant, val experienceId: Int)

// the experience the routine's reminders open; null before its dose is logged
fun routineExperienceId(routine: ActiveRoutine, ownMdma: List<OwnDose>): Int? =
    ownMdma.filter { routineBelongsTo(routine, listOf(it.ingestionId to it.time)) }
        .maxByOrNull { it.time }?.experienceId
