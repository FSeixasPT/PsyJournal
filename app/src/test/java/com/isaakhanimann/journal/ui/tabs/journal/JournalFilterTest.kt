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

package com.isaakhanimann.journal.ui.tabs.journal

import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class JournalFilterTest {
    private fun ingestion(substance: String, consumer: String? = null) = Ingestion(
        substanceName = substance, time = Instant.EPOCH, administrationRoute = AdministrationRoute.ORAL, dose = null,
        isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = null, experienceId = 1, notes = null,
        stomachFullness = null, consumerName = consumer, customUnitId = null
    )
    private fun hours(h: Double): Duration = Duration.ofMinutes((h * 60).toLong())

    @Test
    fun onlyTheUsersOwnDosesCountAsTheirSubstances() {
        val ingestions = listOf(ingestion("MDMA"), ingestion("MDMA"), ingestion("LSD", consumer = "Alex"), ingestion("Ketamine"))
        assertEquals(setOf("MDMA", "Ketamine"), ownSubstances(ingestions))
    }

    @Test
    fun everySelectedSubstanceMustHaveBeenTaken() {
        val filter = JournalFilter(substances = setOf("MDMA", "LSD"))
        assertTrue(filter.matches(setOf("MDMA", "LSD", "Ketamine"), duration = null))
        assertFalse(filter.matches(setOf("MDMA"), duration = null))
    }

    @Test
    fun theNumberOfSubstancesCanBeAnyOfTheChosenOnes() {
        val oneOrTwo = JournalFilter(substanceCounts = setOf(SubstanceCount.ONE, SubstanceCount.TWO))
        assertTrue(oneOrTwo.matches(setOf("MDMA"), null))
        assertTrue(oneOrTwo.matches(setOf("MDMA", "LSD"), null))
        assertFalse(oneOrTwo.matches(setOf("A", "B", "C"), null))
        assertTrue(JournalFilter(substanceCounts = setOf(SubstanceCount.THREE_OR_MORE)).matches(setOf("A", "B", "C", "D"), null))
        assertFalse(JournalFilter(substanceCounts = setOf(SubstanceCount.THREE_OR_MORE)).matches(setOf("A", "B"), null))
        // none chosen is any number
        assertTrue(JournalFilter().matches(setOf("A", "B", "C", "D", "E"), null))
    }

    @Test
    fun durationIsWithinTheRangeAndAnOpenEndHasNoLimit() {
        val between = JournalFilter(minHours = 4f, maxHours = 8f)
        assertTrue(between.matches(setOf("MDMA"), hours(4.0)))
        assertTrue(between.matches(setOf("MDMA"), hours(8.0)))
        assertFalse(between.matches(setOf("MDMA"), hours(9.5)))
        assertTrue(JournalFilter(minHours = 4f).matches(setOf("MDMA"), hours(30.0)))
        assertFalse(JournalFilter(maxHours = 8f).matches(setOf("MDMA"), hours(9.0)))
    }

    @Test
    fun anUnknownDurationOnlyMatchesWithoutADurationFilter() {
        assertTrue(JournalFilter().matches(setOf("MDMA"), null))
        assertFalse(JournalFilter(minHours = 1f).matches(setOf("MDMA"), null))
    }

    @Test
    fun substancesAreOfferedMostTakenFirst() {
        val experiences = listOf(setOf("MDMA", "LSD"), setOf("MDMA"), setOf("Ketamine", "LSD"), setOf("MDMA"))
        assertEquals(listOf("MDMA", "LSD", "Ketamine"), substancesByUse(experiences))
    }

    @Test
    fun theSliderReachesTheLongestExperienceAndAtLeast12Hours() {
        assertEquals(12f, durationSliderMax(listOf(hours(4.5), hours(9.5))))
        assertEquals(31f, durationSliderMax(listOf(hours(30.2))))
    }

    @Test
    fun summary() {
        assertEquals(null, JournalFilter().summary())
        assertEquals("MDMA, LSD · 2 substances · 4–8.5 h", JournalFilter(setOf("MDMA", "LSD"), setOf(SubstanceCount.TWO), 4f, 8.5f).summary())
        assertEquals("1 substance · at least 4 h", JournalFilter(substanceCounts = setOf(SubstanceCount.ONE), minHours = 4f).summary())
        assertEquals("3+ substances · up to 8 h", JournalFilter(substanceCounts = setOf(SubstanceCount.THREE_OR_MORE), maxHours = 8f).summary())
        assertEquals("1 or 3+ substances", JournalFilter(substanceCounts = setOf(SubstanceCount.THREE_OR_MORE, SubstanceCount.ONE)).summary())
        assertEquals("1, 2 or 3+ substances", JournalFilter(substanceCounts = SubstanceCount.entries.toSet()).summary())
    }

    @Test
    fun theSliderIsLabelledEveryFewHoursEndingInOrLonger() {
        assertEquals(listOf(0f to "0", 2f to "2 h", 4f to "4 h", 6f to "6 h", 8f to "8 h", 10f to "10 h", 12f to "12 h+"), durationSliderLabels(12f))
        // a mark too close to the end makes way for it
        assertEquals(listOf(0f to "0", 6f to "6 h", 12f to "12 h", 18f to "18 h", 24f to "24 h", 31f to "31 h+"), durationSliderLabels(31f))
    }
}
