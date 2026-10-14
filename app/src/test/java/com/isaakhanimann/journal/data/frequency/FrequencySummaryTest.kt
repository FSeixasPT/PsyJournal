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
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FrequencySummaryTest {
    private val now = Instant.parse("2026-10-14T20:00:00Z")
    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))
    private val mdma = MinimumGapRule(setOf("MDMA", "MDA"), Duration.ofDays(90), "mdma reason", "mdma-source")
    private val psychedelics = ToleranceRule(mapOf("LSD" to Duration.ofDays(5)), mapOf("LSD" to "lsd-url"), "r")
    private val ketamine = MaxSessionsRule(setOf("Ketamine"), 2, Duration.ofDays(7), "r", "k-source")

    @Test
    fun withinTheGapShowsWhenItIsOkAgain() {
        val summary = frequencySummaryOf(mdma, "MDMA", listOf(PastIngestion("MDMA", daysAgo(35), 1)), now)
        assertEquals(daysAgo(35), summary.lastSessionEnd)
        assertEquals("3 months", summary.recommendedGapText)
        assertEquals(daysAgo(35).plus(Duration.ofDays(90)), summary.okAgainAt)
        assertEquals("mdma reason", summary.reason)
        assertEquals("mdma-source", summary.sourceUrl)
    }

    @Test
    fun noPastSessionsShowsOnlyTheGap() {
        val summary = frequencySummaryOf(mdma, "MDMA", emptyList(), now)
        assertNull(summary.lastSessionEnd)
        assertNull(summary.okAgainAt)
    }

    @Test
    fun afterTheGapThereIsNoOkAgainDate() {
        assertNull(frequencySummaryOf(mdma, "MDMA", listOf(PastIngestion("MDMA", daysAgo(100), 1)), now).okAgainAt)
    }

    @Test
    fun psychedelicsUseTheSubstanceResetTimeAndPage() {
        val summary = frequencySummaryOf(psychedelics, "LSD", emptyList(), now)
        assertEquals("5 days", summary.recommendedGapText)
        assertEquals("lsd-url", summary.sourceUrl)
    }

    @Test
    fun ketamineCountsSessionsThisWeek() {
        val past = listOf(
            PastIngestion("Ketamine", daysAgo(1), 1),
            PastIngestion("Ketamine", daysAgo(1).plusSeconds(600), 1),
            PastIngestion("Ketamine", daysAgo(3), 2),
            PastIngestion("Ketamine", daysAgo(10), 3),
        )
        val summary = frequencySummaryOf(ketamine, "Ketamine", past, now)
        assertEquals(2, summary.sessionsInWindow)
        assertEquals("at most 2 sessions a week", summary.recommendedGapText)
    }
}
