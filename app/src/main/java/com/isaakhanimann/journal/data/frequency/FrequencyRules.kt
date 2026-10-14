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

import com.isaakhanimann.journal.data.substances.classes.Substance
import java.time.Duration

sealed interface FrequencyRule {
    val substanceNames: Set<String>
    val reason: String
}

// warns when a previous session of any substance in the group ended less than the gap ago
data class MinimumGapRule(
    override val substanceNames: Set<String>,
    val gap: Duration,
    override val reason: String,
    val sourceUrl: String,
) : FrequencyRule

// psychedelics: the gap is the reset time of the substance being taken, and every member counts (cross-tolerance)
data class ToleranceRule(
    val resetTimes: Map<String, Duration>,
    val sourceUrls: Map<String, String>,
    override val reason: String,
) : FrequencyRule {
    override val substanceNames: Set<String> get() = resetTimes.keys
}

// warns when there were at least maxOtherSessions other sessions within the window
data class MaxSessionsRule(
    override val substanceNames: Set<String>,
    val maxOtherSessions: Int,
    val window: Duration,
    override val reason: String,
    val sourceUrl: String,
) : FrequencyRule

fun buildFrequencyRules(substances: List<Substance>): List<FrequencyRule> {
    val psychedelics = substances.mapNotNull { substance ->
        val isPsychedelic = substance.categories.any { it.equals("psychedelic", ignoreCase = true) }
        val buildsImmediately = substance.tolerance?.full?.contains("immediately", ignoreCase = true) == true
        val reset = parseToleranceDuration(substance.tolerance?.zero)
        if (isPsychedelic && buildsImmediately && reset != null && reset >= Duration.ofDays(1)) {
            substance to reset
        } else {
            null
        }
    }
    // MDMA comes first so MDA, which is also a "psychedelic", resolves to it
    return listOf(
        MinimumGapRule(
            substanceNames = setOf("MDMA", "MDA"),
            gap = Duration.ofDays(90),
            reason = "Waiting at least 3 months between sessions is recommended: serotonin needs time to recover, and frequent use is linked to lasting low mood and memory problems.",
            sourceUrl = "https://tripsitter.substack.com/p/rolling-safe-mdma",
        ),
        ToleranceRule(
            resetTimes = psychedelics.associate { (substance, reset) -> substance.name to reset },
            sourceUrls = psychedelics.associate { (substance, _) -> substance.name to substance.url },
            reason = "Tolerance, also between different psychedelics, means the effects will likely be weaker. Taking more to make up for it raises the risk of a difficult trip.",
        ),
        MaxSessionsRule(
            substanceNames = setOf("Ketamine"),
            maxOtherSessions = 2,
            window = Duration.ofDays(7),
            reason = "Using ketamine several times a week can damage the bladder. Early damage often heals if you cut down.",
            sourceUrl = "https://knowyourstuff.nz/ketamine-bladder-0-stars-do-not-recommend/",
        ),
    )
}

fun List<FrequencyRule>.ruleFor(substanceName: String): FrequencyRule? =
    firstOrNull { substanceName in it.substanceNames }
