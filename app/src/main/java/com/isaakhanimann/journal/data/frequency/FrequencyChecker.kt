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

data class PastIngestion(val substanceName: String, val time: Instant, val experienceId: Int)

data class FrequencyWarning(
    val rule: FrequencyRule,
    // the substance of the most recent previous session, e.g. mushrooms when LSD is logged
    val lastSubstanceName: String,
    val lastSessionEnd: Instant,
    val recommendedGap: Duration?,
    val okAgainAt: Instant?,
    val sessionsInWindow: Int,
    val sourceUrl: String,
)

private data class Session(val end: Instant, val lastSubstanceName: String)

// one session per experience, ending at its last dose of a substance in the rule
private fun previousSessions(
    rule: FrequencyRule,
    at: Instant,
    currentExperienceIds: Set<Int>,
    past: List<PastIngestion>,
): List<Session> = past
    .filter { it.substanceName in rule.substanceNames && it.time <= at && it.experienceId !in currentExperienceIds }
    .groupBy { it.experienceId }
    .values
    .map { doses -> doses.maxBy { it.time }.let { Session(it.time, it.substanceName) } }
    .sortedByDescending { it.end }

fun checkFrequency(
    rule: FrequencyRule,
    substanceName: String,
    at: Instant,
    currentExperienceIds: Set<Int>,
    past: List<PastIngestion>,
): FrequencyWarning? {
    val sessions = previousSessions(rule, at, currentExperienceIds, past)
    val last = sessions.firstOrNull() ?: return null
    val sinceLast = Duration.between(last.end, at)
    return when (rule) {
        is MinimumGapRule -> if (sinceLast < rule.gap) {
            FrequencyWarning(rule, last.lastSubstanceName, last.end, rule.gap, last.end.plus(rule.gap), 1, rule.sourceUrl)
        } else {
            null
        }

        is ToleranceRule -> {
            val reset = rule.resetTimes[substanceName] ?: return null
            if (sinceLast < reset) {
                FrequencyWarning(
                    rule, last.lastSubstanceName, last.end, reset, last.end.plus(reset), 1,
                    rule.sourceUrls[substanceName].orEmpty()
                )
            } else {
                null
            }
        }

        is MaxSessionsRule -> {
            val inWindow = sessions.count { Duration.between(it.end, at) < rule.window }
            if (inWindow >= rule.maxOtherSessions) {
                FrequencyWarning(rule, last.lastSubstanceName, last.end, null, null, inWindow, rule.sourceUrl)
            } else {
                null
            }
        }
    }
}

// the app's own grouping: an experience with a dose in the last hours is the one a new dose would join
fun currentExperienceIdsAt(at: Instant, past: List<PastIngestion>, limit: Duration): Set<Int> =
    past.filter { it.time <= at && Duration.between(it.time, at) <= limit }.map { it.experienceId }.toSet()

// the check on the last step of logging, where the dose time, its experience and who took it are known:
// the experience the dose joins is the current session, and a dose for someone else never warns
fun warningWhenLogging(
    rule: FrequencyRule,
    substanceName: String,
    at: Instant,
    joiningExperienceId: Int?,
    consumerName: String,
    past: List<PastIngestion>,
): FrequencyWarning? {
    if (consumerName.isNotBlank()) return null
    return checkFrequency(rule, substanceName, at, setOfNotNull(joiningExperienceId), past)
}
