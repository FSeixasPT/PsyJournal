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

package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveExperienceTest {
    private val createdAt = Instant.parse("2026-01-01T20:00:00.123Z")
    private fun experience(id: Int, creationDate: Instant) =
        Experience(id = id, title = "T", text = "", creationDate = creationDate, sortDate = creationDate, location = null)

    @Test
    fun roundTrips() {
        val live = LiveExperience(3, createdAt)
        assertEquals(live, LiveExperience.fromPreferenceString(live.toPreferenceString()))
    }

    @Test
    fun malformedValuesAreAbsent() {
        assertNull(LiveExperience.fromPreferenceString(null))
        assertNull(LiveExperience.fromPreferenceString("abc"))
        assertNull(LiveExperience.fromPreferenceString("3|not a date"))
    }

    @Test
    fun aReusedIdIsAnotherExperience() {
        val live = LiveExperience(3, createdAt)
        assertTrue(live.isFor(experience(3, createdAt)))
        assertFalse(live.isFor(experience(3, createdAt.plusSeconds(60))))
        assertFalse(live.isFor(experience(4, createdAt)))
    }

    @Test
    fun deletingTheLiveExperienceStopsTheNotification() {
        val live = LiveExperience(3, createdAt)
        assertTrue(stopsLiveNotification(live, deleted = experience(3, createdAt)))
        // another experience, or one that reused the id, leaves it running
        assertFalse(stopsLiveNotification(live, deleted = experience(4, createdAt)))
        assertFalse(stopsLiveNotification(live, deleted = experience(3, createdAt.plusSeconds(60))))
        assertFalse(stopsLiveNotification(null, deleted = experience(3, createdAt)))
    }
}
