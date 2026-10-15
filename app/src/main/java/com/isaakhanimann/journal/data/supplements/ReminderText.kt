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
import java.util.Locale

private fun offsetText(offset: Duration): String {
    val minutes = offset.abs().toMinutes()
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "$h h" else "$h h $m min"
}

fun reminderTitle(step: RoutineStep, doseTime: Instant, formatTime: (Instant) -> String): String {
    val at = formatTime(stepTime(doseTime, step))
    val whenText = when {
        step.offset.isNegative -> "${offsetText(step.offset)} before"
        step.offset.isZero -> "with your dose"
        else -> "+${offsetText(step.offset)}"
    }
    return "MDMA routine · $whenText ($at)"
}

// a step on the routine's card: "+3 h (2:13 PM) · ALA 300 mg · Vitamin C 500 mg"
fun stepLine(step: RoutineStep, doseTime: Instant, formatTime: (Instant) -> String): String {
    val whenText = reminderTitle(step, doseTime, formatTime).removePrefix("MDMA routine · ")
    return listOfNotNull(whenText, takeLine(step)?.removePrefix("Take: ")).joinToString(" · ")
}

private fun Supplement.text() = "$name $amount"

fun takeLine(step: RoutineStep): String? {
    if (step.take.isEmpty() && step.optional.isEmpty()) return null
    val parts = step.take.map { it.text() } + step.optional.map { "optional: ${it.text()}" }
    return "Take: " + parts.joinToString(" · ")
}

private const val WATER_CAUTION = "Don't drink much more than that: too much water is dangerous. An electrolyte drink is better than plain water."

private fun litres(ml: Int) = String.format(Locale.US, "%.2f", ml / 1000.0).trimEnd('0').trimEnd('.')

fun fiveHtpFrom(lastMdmaDose: Instant): Instant = lastMdmaDose.plus(Duration.ofHours(24))

fun reminderLines(
    kind: RoutineKind,
    step: RoutineStep,
    doseTime: Instant,
    mlPerHour: Int,
    lastMdmaDose: Instant,
    formatTime: (Instant) -> String,
    formatDayTime: (Instant) -> String,
): List<String> {
    val lines = mutableListOf<String>()
    takeLine(step)?.let { lines += it }
    val at = stepTime(doseTime, step)
    when {
        step.offset.isNegative -> lines += "Planned MDMA at ${formatTime(doseTime)}."
        step.offset.isZero -> lines += "Start drinking about $mlPerHour ml an hour. $WATER_CAUTION"
        else -> {
            val total = waterTotalMl(doseTime, at, mlPerHour) ?: 0
            lines += "Water so far: about ${litres(total)} L ($mlPerHour ml an hour). $WATER_CAUTION"
        }
    }
    if (step == stepsOf(kind).last()) {
        lines += "No 5-HTP until 24 h after your last MDMA dose (from ${formatDayTime(fiveHtpFrom(lastMdmaDose))})."
    }
    return lines
}

// for the routine's card and page; null before the dose
fun waterSoFarText(doseTime: Instant, at: Instant, mlPerHour: Int): String? =
    waterTotalMl(doseTime, at, mlPerHour)?.let {
        if (it == 0) "Start drinking about $mlPerHour ml an hour" else "Water so far: about ${litres(it)} L ($mlPerHour ml an hour)"
    }
