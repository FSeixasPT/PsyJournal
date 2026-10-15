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

package com.isaakhanimann.journal.data.supplements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReminderTextTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")
    private val time: (Instant) -> String = { "T" + Duration.between(dose, it).toMinutes() }
    private val dayTime: (Instant) -> String = { "D" + Duration.between(dose, it).toHours() }
    private fun step(kind: RoutineKind, hours: Long) = stepsOf(kind).first { it.offset == Duration.ofHours(hours) }
    private fun lines(kind: RoutineKind, hours: Long, rate: Int = 250, lastDose: Instant = dose) =
        reminderLines(kind, step(kind, hours), dose, rate, lastDose, time, dayTime)

    @Test
    fun titles() {
        assertEquals("MDMA routine · 3 h before (T-180)", reminderTitle(step(RoutineKind.REGULAR, -3), dose, time))
        assertEquals("MDMA routine · with your dose (T0)", reminderTitle(step(RoutineKind.REGULAR, 0), dose, time))
        assertEquals("MDMA routine · +2 h (T120)", reminderTitle(step(RoutineKind.REGULAR, 2), dose, time))
        val coq10 = stepsOf(RoutineKind.MINIMAL)[1]
        assertEquals("MDMA routine · 6 h 30 min before (T-390)", reminderTitle(coq10, dose, time))
    }

    @Test
    fun takeLineListsOptionalSeparately() {
        assertEquals("Take: ALA 300 mg · optional: Magnesium 100 mg", takeLine(step(RoutineKind.REGULAR, 2)))
    }

    @Test
    fun beforeTheDoseThereIsNoWater() {
        val result = lines(RoutineKind.REGULAR, -3)
        assertEquals(listOf("Take: ALCAR 500 mg · Vitamin C 500 mg", "Planned MDMA at T0."), result)
    }

    @Test
    fun atTheDoseWaterStarts() {
        val result = lines(RoutineKind.REGULAR, 0, rate = 500)
        assertTrue(result[1].startsWith("Start drinking about 500 ml an hour."))
        assertTrue(result[1].contains("too much water is dangerous"))
    }

    @Test
    fun afterTheDoseSupplementStepsSayTheWaterSoFar() {
        val result = lines(RoutineKind.REGULAR, 2)
        assertEquals("Take: ALA 300 mg · optional: Magnesium 100 mg", result[0])
        assertTrue(result[1].startsWith("Water so far: about 0.5 L (250 ml an hour)."))
        assertTrue(result[1].contains("too much water is dangerous"))
    }

    @Test
    fun lastReminderWarnsAboutFiveHtpFromTheLatestDose() {
        val redose = dose.plus(Duration.ofHours(2))
        val result = lines(RoutineKind.REGULAR, 7, lastDose = redose)
        assertEquals("No 5-HTP until 24 h after your last MDMA dose (from D26).", result.last())
        assertEquals(redose.plus(Duration.ofHours(24)), fiveHtpFrom(redose))
    }

    @Test
    fun stepLinesSayWhenRelativeToTheDoseAndWhatToTake() {
        assertEquals("+3 h (T180) · ALA 300 mg · Vitamin C 500 mg", stepLine(step(RoutineKind.REGULAR, 3), dose, time))
        assertEquals("with your dose (T0) · ALA 300 mg · Vitamin C 500 mg", stepLine(step(RoutineKind.REGULAR, 0), dose, time))
        assertEquals("3 h before (T-180) · ALCAR 1000 mg", stepLine(step(RoutineKind.MINIMAL, -3), dose, time))
    }
}
