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

package com.isaakhanimann.journal.data.room.experiences.relations

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.room.experiences.entities.TimedNote
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ExperienceRowCountsTest {

    private val now = Instant.parse("2026-01-01T20:00:00Z")

    private fun experience(text: String, timedNotes: Int) = ExperienceWithIngestionsCompanionsAndRatings(
        experience = Experience(id = 1, title = "Title", text = text, sortDate = now, location = null),
        ingestionsWithCompanions = emptyList(),
        ratings = emptyList(),
        timedNotes = List(timedNotes) {
            TimedNote(time = now, note = "note", color = AdaptiveColor.BLUE, experienceId = 1, isPartOfTimeline = true)
        }
    )

    @Test
    fun writtenNotesCountAsOneNote() {
        assertEquals(1, experience(text = "A good evening", timedNotes = 0).noteCount)
    }

    @Test
    fun timedNotesAddToTheWrittenNotes() {
        assertEquals(3, experience(text = "A good evening", timedNotes = 2).noteCount)
        assertEquals(2, experience(text = "", timedNotes = 2).noteCount)
    }

    @Test
    fun blankTextIsNoNote() {
        assertEquals(0, experience(text = "  \n ", timedNotes = 0).noteCount)
    }
}
