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
import java.time.Instant

// what the substance page shows about how often to take it
data class FrequencySummary(
    val lastSessionEnd: Instant?,
    val recommendedGapText: String,
    // only while the last session is still within the gap
    val okAgainAt: Instant?,
    // sessions in the rule's window, for rules that count sessions
    val sessionsInWindow: Int?,
    val reason: String,
    val sourceUrl: String,
)

fun frequencySummaryOf(rule: FrequencyRule, substanceName: String, past: List<PastIngestion>, now: Instant): FrequencySummary {
    val ownPast = past.filter { it.substanceName in rule.substanceNames && it.time <= now }
    val warning = checkFrequency(rule, substanceName, now, emptySet(), ownPast)
    return when (rule) {
        is MinimumGapRule -> FrequencySummary(
            lastSessionEnd = ownPast.maxOfOrNull { it.time },
            recommendedGapText = "${rule.gap.toDays() / 30} months",
            okAgainAt = warning?.okAgainAt,
            sessionsInWindow = null,
            reason = rule.reason,
            sourceUrl = rule.sourceUrl,
        )

        is ToleranceRule -> FrequencySummary(
            lastSessionEnd = ownPast.maxOfOrNull { it.time },
            recommendedGapText = "${rule.resetTimes[substanceName]?.toDays() ?: 0} days",
            okAgainAt = warning?.okAgainAt,
            sessionsInWindow = null,
            reason = rule.reason,
            sourceUrl = rule.sourceUrls[substanceName].orEmpty(),
        )

        is MaxSessionsRule -> FrequencySummary(
            lastSessionEnd = ownPast.maxOfOrNull { it.time },
            recommendedGapText = "at most ${rule.maxOtherSessions} sessions a week",
            okAgainAt = null,
            sessionsInWindow = ownPast.filter { Duration.between(it.time, now) < rule.window }.map { it.experienceId }.toSet().size,
            reason = rule.reason,
            sourceUrl = rule.sourceUrl,
        )
    }
}
