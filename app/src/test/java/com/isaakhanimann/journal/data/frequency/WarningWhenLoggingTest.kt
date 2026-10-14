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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WarningWhenLoggingTest {
    private val saturday = Instant.parse("2026-10-10T22:00:00Z")
    private val mdma = MinimumGapRule(setOf("MDMA", "MDA"), Duration.ofDays(90), "r", "s")

    @Test
    fun backfilledRedoseIntoTheSameOldExperienceDoesNotWarn() {
        val past = listOf(PastIngestion("MDMA", saturday, experienceId = 1))
        val redoseTime = saturday.plus(Duration.ofHours(2))
        assertNull(warningWhenLogging(mdma, "MDMA", redoseTime, joiningExperienceId = 1, consumerName = "", past))
    }

    @Test
    fun quickLoggedDoseInANewExperienceWarns() {
        val past = listOf(PastIngestion("MDMA", saturday, experienceId = 1))
        val threeWeeksLater = saturday.plus(Duration.ofDays(21))
        assertNotNull(warningWhenLogging(mdma, "MDMA", threeWeeksLater, joiningExperienceId = null, consumerName = "", past))
    }

    @Test
    fun aDoseForSomeoneElseDoesNotWarn() {
        val past = listOf(PastIngestion("MDMA", saturday, experienceId = 1))
        val threeWeeksLater = saturday.plus(Duration.ofDays(21))
        assertNull(warningWhenLogging(mdma, "MDMA", threeWeeksLater, joiningExperienceId = null, consumerName = "Alex", past))
    }

    @Test
    fun backfillingAnOlderDoseIgnoresLaterSessions() {
        val past = listOf(PastIngestion("MDMA", saturday, experienceId = 1))
        val monthBefore = saturday.minus(Duration.ofDays(30))
        assertNull(warningWhenLogging(mdma, "MDMA", monthBefore, joiningExperienceId = null, consumerName = "", past))
    }
}
