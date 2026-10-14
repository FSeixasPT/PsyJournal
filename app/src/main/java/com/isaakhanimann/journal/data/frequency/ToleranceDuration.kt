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

package com.isaakhanimann.journal.data.frequency

import java.time.Duration
import kotlin.math.roundToLong

private val durationPattern =
    Regex("""(\d+(?:\.\d+)?)\s*(?:-\s*(\d+(?:\.\d+)?))?\s*(hour|day|week|month)s?""", RegexOption.IGNORE_CASE)

// PsychonautWiki tolerance text such as "7-10 days" or "2.5 months"; ranges use the upper end,
// a month is 30 days, and anything else is null so it never turns into a guessed gap
fun parseToleranceDuration(text: String?): Duration? {
    val match = text?.let { durationPattern.find(it) } ?: return null
    val value = match.groupValues[2].ifEmpty { match.groupValues[1] }.toDouble()
    val minutesPerUnit = when (match.groupValues[3].lowercase()) {
        "hour" -> 60.0
        "day" -> 60.0 * 24
        "week" -> 60.0 * 24 * 7
        else -> 60.0 * 24 * 30
    }
    return Duration.ofMinutes((value * minutesPerUnit).roundToLong())
}
