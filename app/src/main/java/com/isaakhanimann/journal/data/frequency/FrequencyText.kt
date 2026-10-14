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

import com.isaakhanimann.journal.ui.utils.getTimeDifferenceText
import java.time.Instant

private fun ordinal(n: Int) = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

fun frequencyTitle(warning: FrequencyWarning, now: Instant): String {
    val ago = getTimeDifferenceText(warning.lastSessionEnd, now)
    return when (val rule = warning.rule) {
        is MinimumGapRule -> "Last ${warning.lastSubstanceName} session was $ago ago"
        is ToleranceRule -> "Last psychedelic session: ${warning.lastSubstanceName}, $ago ago"
        // this session included, e.g. the 3rd one this week
        is MaxSessionsRule -> "${ordinal(warning.sessionsInWindow + 1)} ${warning.lastSubstanceName.lowercase()} session in ${rule.window.toDays()} days"
    }
}

// one short line under the redose preview
fun frequencyPreviewLine(warning: FrequencyWarning, now: Instant): String {
    val ago = getTimeDifferenceText(warning.lastSessionEnd, now)
    return when (val rule = warning.rule) {
        is MinimumGapRule -> "Last ${warning.lastSubstanceName} session $ago ago: ${rule.gap.toDays() / 30} months between sessions is recommended"
        is ToleranceRule -> "Tolerance from ${warning.lastSubstanceName} $ago ago: effects will be weaker"
        is MaxSessionsRule -> "${frequencyTitle(warning, now)}: risk of bladder damage"
    }
}
