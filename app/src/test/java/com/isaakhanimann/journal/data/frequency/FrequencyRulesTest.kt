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

import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration

class FrequencyRulesTest {
    private val substances = SubstanceParser()
        .parseSubstanceFile(File("src/main/assets/Substances.json").readText()).substances
    private val rules = buildFrequencyRules(substances)

    @Test
    fun mdmaAndMdaShareThreeMonths() {
        val rule = rules.ruleFor("MDMA") as MinimumGapRule
        assertEquals(setOf("MDMA", "MDA"), rule.substanceNames)
        assertEquals(Duration.ofDays(90), rule.gap)
        assertTrue(rules.ruleFor("MDA") === rule)
    }

    @Test
    fun psychedelicsUseTheirOwnResetTime() {
        val rule = rules.ruleFor("LSD") as ToleranceRule
        assertEquals(Duration.ofDays(5), rule.resetTimes["LSD"])
        assertEquals(Duration.ofDays(7), rule.resetTimes["Psilocybin mushrooms"])
        assertEquals(Duration.ofDays(14), rule.resetTimes["1P-LSD"])
        assertTrue(rules.ruleFor("Mescaline") === rule)
        assertEquals(70, rule.resetTimes.size)
    }

    @Test
    fun excludedSubstancesHaveNoPsychedelicRule() {
        listOf("2C-B", "DMT", "5-MeO-DMT", "Ayahuasca").forEach { assertNull(it, rules.ruleFor(it)) }
        assertTrue(rules.ruleFor("MDA") is MinimumGapRule)
    }

    @Test
    fun ketamineCountsSessionsPerWeek() {
        val rule = rules.ruleFor("Ketamine") as MaxSessionsRule
        assertEquals(2, rule.maxOtherSessions)
        assertEquals(Duration.ofDays(7), rule.window)
    }

    @Test
    fun otherSubstancesHaveNoRule() {
        listOf("Cocaine", "Alcohol", "Cannabis").forEach { assertNull(it, rules.ruleFor(it)) }
    }
}
