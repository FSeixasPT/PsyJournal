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

package com.isaakhanimann.journal.ui.tabs.journal.components

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class ExperienceDurationTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")

    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)

    // ends 60 min after the ingestion
    private val oneHour = RoaDuration(minutes(10f), minutes(10f), minutes(20f), minutes(20f), total = null, afterglow = null)

    // ends 3 h after the ingestion
    private val threeHours = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), total = null, afterglow = null)

    private fun line(minutesAfterStart: Long, roaDuration: RoaDuration?) = DataForOneEffectLine(
        substanceName = "Substance",
        route = AdministrationRoute.ORAL,
        roaDuration = roaDuration,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = start.plus(minutesAfterStart, ChronoUnit.MINUTES),
        endTime = null
    )

    @Test
    fun runsFromTheFirstIngestionToTheEndOfTheLastEffect() {
        val lines = listOf(line(0, oneHour), line(120, threeHours))
        assertEquals(Duration.ofHours(5), experienceDuration(lines))
    }

    @Test
    fun anEarlierEndingLaterIngestionDoesNotShortenIt() {
        val lines = listOf(line(0, threeHours), line(30, oneHour))
        assertEquals(Duration.ofHours(3), experienceDuration(lines))
    }

    @Test
    fun usesTheTotalDurationWhenThePhasesAreUnknown() {
        val totalOnly = RoaDuration(null, null, null, null, total = minutes(240f), afterglow = null)
        assertEquals(Duration.ofHours(4), experienceDuration(listOf(line(0, totalOnly))))
    }

    @Test
    fun unknownWhenAnIngestionHasNoDurations() {
        assertNull(experienceDuration(listOf(line(0, oneHour), line(10, null))))
        val onsetOnly = RoaDuration(minutes(10f), null, null, null, total = null, afterglow = null)
        assertNull(experienceDuration(listOf(line(0, onsetOnly))))
    }

    @Test
    fun unknownWithoutIngestions() {
        assertNull(experienceDuration(emptyList()))
    }

    @Test
    fun hoursAreRoundedToTheNearestHalfHour() {
        assertEquals("1 h", Duration.ofMinutes(61).toHoursText())
        assertEquals("1.5 h", Duration.ofMinutes(80).toHoursText())
        assertEquals("6.5 h", Duration.ofMinutes(390).toHoursText())
        // never rounded down to nothing
        assertEquals("0.5 h", Duration.ofMinutes(5).toHoursText())
    }
}
