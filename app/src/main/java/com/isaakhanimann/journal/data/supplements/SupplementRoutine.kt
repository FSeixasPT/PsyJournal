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
import kotlin.math.roundToInt

// RollSafe's MDMA supplement routines (https://rollsafe.org/mdma-supplements/, checked 2026-10-14);
// the amounts and times are theirs, the wording in the app is ours
const val ROLLSAFE_URL = "https://rollsafe.org/mdma-supplements/"

enum class RoutineKind { REGULAR, MINIMAL }

data class Supplement(val name: String, val amount: String)

// offset from the planned MDMA dose
data class RoutineStep(val offset: Duration, val take: List<Supplement>, val optional: List<Supplement> = emptyList())

private val ginger = Supplement("Ginger", "2 g")
private val alcar = Supplement("ALCAR", "500 mg")
private val vitaminC = Supplement("Vitamin C", "500 mg")
private val magnesium = Supplement("Magnesium", "100 mg")
private val ala = Supplement("ALA", "300 mg")

private fun hours(h: Long) = Duration.ofHours(h)

private val regular = listOf(
    RoutineStep(hours(-4), listOf(ginger)),
    RoutineStep(hours(-3), listOf(alcar, vitaminC)),
    RoutineStep(hours(-1), listOf(ginger), optional = listOf(magnesium)),
    RoutineStep(hours(0), listOf(ala, vitaminC)),
    RoutineStep(hours(1), listOf(ala, alcar)),
    RoutineStep(hours(2), listOf(ala), optional = listOf(magnesium)),
    RoutineStep(hours(3), listOf(ala, vitaminC)),
    RoutineStep(hours(4), listOf(ala)),
    RoutineStep(hours(5), listOf(ala, alcar)),
    RoutineStep(hours(6), listOf(ala, vitaminC)),
    RoutineStep(hours(7), listOf(ala)),
)

// for when pills can't be taken at the event, so it ends before the dose
private val minimal = listOf(
    RoutineStep(hours(-13), listOf(Supplement("Vitamin E", "800 IU"))),
    RoutineStep(Duration.ofMinutes(-390), listOf(Supplement("CoQ10", "200–600 mg"))),
    RoutineStep(hours(-3), listOf(Supplement("ALCAR", "1000 mg"))),
)

fun stepsOf(kind: RoutineKind): List<RoutineStep> = when (kind) {
    RoutineKind.REGULAR -> regular
    RoutineKind.MINIMAL -> minimal
}

fun stepTime(doseTime: Instant, step: RoutineStep): Instant = doseTime.plus(step.offset)

fun remainingSteps(kind: RoutineKind, doseTime: Instant, now: Instant): List<RoutineStep> =
    stepsOf(kind).filter { !stepTime(doseTime, it).isBefore(now) }

// after a restart or a late alarm only the most recent missed step is shown, once
fun stepToPost(kind: RoutineKind, doseTime: Instant, now: Instant, lastPostedAt: Instant?): RoutineStep? =
    stepsOf(kind).lastOrNull { step ->
        val time = stepTime(doseTime, step)
        !time.isAfter(now) && (lastPostedAt == null || time.isAfter(lastPostedAt))
    }

fun isFinished(kind: RoutineKind, doseTime: Instant, now: Instant): Boolean =
    !now.isBefore(stepTime(doseTime, stepsOf(kind).last()))

fun waterTotalMl(doseTime: Instant, at: Instant, mlPerHour: Int): Int? {
    if (at.isBefore(doseTime)) return null
    val hoursSinceDose = Duration.between(doseTime, at).toMinutes() / 60.0
    return ((hoursSinceDose * mlPerHour) / 250.0).roundToInt() * 250
}

// Android may deliver an inexact alarm up to 75% of its delay late, so far steps get halfway checkpoints:
// even a late checkpoint lands before its step, and steps end up at most a few minutes late
fun nextCheckpoint(now: Instant, stepAt: Instant, directWithin: Duration = Duration.ofMinutes(15)): Instant {
    val untilStep = Duration.between(now, stepAt)
    return if (untilStep > directWithin) now.plus(untilStep.dividedBy(2)) else stepAt
}

// a step from the last half hour is still shown when starting, so starting right after the dose
// shows the dose step; older steps stay skipped
fun initialLastPostedAt(kind: RoutineKind, doseTime: Instant, now: Instant, grace: Duration = Duration.ofMinutes(30)): Instant {
    val recent = stepsOf(kind).lastOrNull { step ->
        val time = stepTime(doseTime, step)
        !time.isAfter(now) && Duration.between(time, now) <= grace
    }
    return recent?.let { stepTime(doseTime, it).minusSeconds(1) } ?: now
}

// a stored routine whose last step has passed is over, even if no alarm came to clear it
fun isRoutineActive(routine: ActiveRoutine?, now: Instant): Boolean =
    routine != null && !isFinished(routine.kind, routine.doseTime, now)
