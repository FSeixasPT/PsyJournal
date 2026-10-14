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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FrequencyCheckerTest {
    private val now = Instant.parse("2026-10-14T20:00:00Z")
    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))
    private fun hoursAgo(hours: Long) = now.minus(Duration.ofHours(hours))

    private val mdma = MinimumGapRule(setOf("MDMA", "MDA"), Duration.ofDays(90), "r", "s")
    private val psychedelics = ToleranceRule(
        resetTimes = mapOf("LSD" to Duration.ofDays(5), "Psilocybin mushrooms" to Duration.ofDays(7)),
        sourceUrls = mapOf("LSD" to "lsd-url", "Psilocybin mushrooms" to "mush-url"),
        reason = "r",
    )
    private val ketamine = MaxSessionsRule(setOf("Ketamine"), 2, Duration.ofDays(7), "r", "s")

    @Test
    fun redoseInTheSameExperienceDoesNotWarn() {
        val past = listOf(PastIngestion("MDMA", hoursAgo(2), experienceId = 1))
        assertNull(checkFrequency(mdma, "MDMA", now, currentExperienceIds = setOf(1), past))
    }

    @Test
    fun mdmaWarnsWithinNinetyDays() {
        val warning = checkFrequency(mdma, "MDMA", now, emptySet(), listOf(PastIngestion("MDMA", daysAgo(89), 1)))
        assertNotNull(warning)
        assertEquals(daysAgo(89).plus(Duration.ofDays(90)), warning!!.okAgainAt)
        assertNull(checkFrequency(mdma, "MDMA", now, emptySet(), listOf(PastIngestion("MDMA", daysAgo(91), 1))))
    }

    @Test
    fun sessionEndsAtItsLastDose() {
        val past = listOf(PastIngestion("MDMA", daysAgo(95), 1), PastIngestion("MDMA", daysAgo(85), 1))
        assertEquals(daysAgo(85), checkFrequency(mdma, "MDMA", now, emptySet(), past)!!.lastSessionEnd)
    }

    @Test
    fun mdaCountsForMdma() {
        val warning = checkFrequency(mdma, "MDMA", now, emptySet(), listOf(PastIngestion("MDA", daysAgo(30), 1)))
        assertEquals("MDA", warning!!.lastSubstanceName)
    }

    @Test
    fun crossToleranceUsesTheResetTimeOfTheLoggedSubstance() {
        val mushrooms3DaysAgo = listOf(PastIngestion("Psilocybin mushrooms", daysAgo(3), 1))
        val warning = checkFrequency(psychedelics, "LSD", now, emptySet(), mushrooms3DaysAgo)
        assertEquals(Duration.ofDays(5), warning!!.recommendedGap)
        assertEquals("lsd-url", warning.sourceUrl)
        val mushrooms6DaysAgo = listOf(PastIngestion("Psilocybin mushrooms", daysAgo(6), 1))
        assertNull(checkFrequency(psychedelics, "LSD", now, emptySet(), mushrooms6DaysAgo))
        val lsd6DaysAgo = listOf(PastIngestion("LSD", daysAgo(6), 1))
        assertNotNull(checkFrequency(psychedelics, "Psilocybin mushrooms", now, emptySet(), lsd6DaysAgo))
    }

    @Test
    fun ketamineCountsSessionsNotDoses() {
        val twoSessions = listOf(
            PastIngestion("Ketamine", daysAgo(2), 1),
            PastIngestion("Ketamine", daysAgo(2).plusSeconds(3600), 1),
            PastIngestion("Ketamine", daysAgo(5), 2),
        )
        assertEquals(2, checkFrequency(ketamine, "Ketamine", now, emptySet(), twoSessions)!!.sessionsInWindow)
        val oneSessionThreeDoses = listOf(
            PastIngestion("Ketamine", daysAgo(2), 1),
            PastIngestion("Ketamine", daysAgo(2).plusSeconds(1800), 1),
            PastIngestion("Ketamine", daysAgo(2).plusSeconds(3600), 1),
        )
        assertNull(checkFrequency(ketamine, "Ketamine", now, emptySet(), oneSessionThreeDoses))
        val oneInWindow = listOf(PastIngestion("Ketamine", daysAgo(2), 1), PastIngestion("Ketamine", daysAgo(9), 2))
        assertNull(checkFrequency(ketamine, "Ketamine", now, emptySet(), oneInWindow))
    }

    @Test
    fun recentExperiencesAreCurrent() {
        val past = listOf(
            PastIngestion("MDMA", hoursAgo(11), 1),
            PastIngestion("MDMA", hoursAgo(13), 2),
            PastIngestion("MDMA", daysAgo(40), 3),
        )
        assertEquals(setOf(1), currentExperienceIdsAt(now, past, Duration.ofHours(12)))
    }

    @Test
    fun futureIngestionsAreIgnored() {
        val past = listOf(PastIngestion("MDMA", now.plus(Duration.ofDays(1)), 1))
        assertNull(checkFrequency(mdma, "MDMA", now, emptySet(), past))
    }
}
