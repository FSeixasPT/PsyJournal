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

import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrequencyRuleBook @Inject constructor(substanceRepo: SubstanceRepository) {
    val rules: List<FrequencyRule> = buildFrequencyRules(substanceRepo.getAllSubstances())

    fun ruleFor(substanceName: String): FrequencyRule? = rules.ruleFor(substanceName)

    // how far back previous sessions matter for any rule
    val longestWindow: Duration = rules.maxOf { rule ->
        when (rule) {
            is MinimumGapRule -> rule.gap
            is ToleranceRule -> rule.resetTimes.values.maxOrNull() ?: Duration.ZERO
            is MaxSessionsRule -> rule.window
        }
    }
}
