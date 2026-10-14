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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FrequencyTextTest {
    private val now = Instant.parse("2026-10-14T20:00:00Z")
    private val mdma = MinimumGapRule(setOf("MDMA", "MDA"), Duration.ofDays(90), "r", "s")
    private val psychedelics = ToleranceRule(mapOf("LSD" to Duration.ofDays(5)), mapOf(), "r")
    private val ketamine = MaxSessionsRule(setOf("Ketamine"), 2, Duration.ofDays(7), "r", "s")

    private fun warning(rule: FrequencyRule, name: String, ago: Duration, sessions: Int = 1) =
        FrequencyWarning(rule, name, now.minus(ago), null, null, sessions, "s")

    @Test
    fun mdmaTitle() {
        assertEquals("Last MDMA session was 3 days ago", frequencyTitle(warning(mdma, "MDMA", Duration.ofDays(3)), now))
    }

    @Test
    fun psychedelicTitleNamesTheSubstance() {
        assertEquals(
            "Last psychedelic session: Psilocybin mushrooms, 3 days ago",
            frequencyTitle(warning(psychedelics, "Psilocybin mushrooms", Duration.ofDays(3)), now)
        )
    }

    @Test
    fun ketamineTitleCountsThisSession() {
        assertEquals("3rd ketamine session in 7 days", frequencyTitle(warning(ketamine, "Ketamine", Duration.ofDays(2), sessions = 2), now))
    }

    @Test
    fun previewLines() {
        assertEquals(
            "Tolerance from Psilocybin mushrooms 3 days ago: effects will be weaker",
            frequencyPreviewLine(warning(psychedelics, "Psilocybin mushrooms", Duration.ofDays(3)), now)
        )
        assertEquals(
            "Last MDMA session 3 days ago: 3 months between sessions is recommended",
            frequencyPreviewLine(warning(mdma, "MDMA", Duration.ofDays(3)), now)
        )
    }
}
