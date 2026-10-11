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

package com.isaakhanimann.journal.ui.tabs.settings

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JournalExportTimelineTest {
    private val json = Json { ignoreUnknownKeys = true }

    // a backup made before ingestions, ratings and substances could be hidden from the timeline
    private val oldBackup = """
        {
          "experiences": [{
            "title": "Old", "text": "", "creationDate": 1700000000000, "sortDate": 1700000000000,
            "ingestions": [{
              "substanceName": "MDMA", "time": 1700000000000, "endTime": null, "creationDate": 1700000000000,
              "administrationRoute": "ORAL", "dose": 100.0, "isDoseAnEstimate": false, "units": "mg"
            }],
            "ratings": [{ "option": "twoPlus", "time": 1700003600000, "creationDate": 1700003600000 }]
          }],
          "substanceCompanions": [{ "substanceName": "MDMA", "color": "PINK" }]
        }
    """.trimIndent()

    @Test
    fun anOldBackupShowsEverythingInTheTimeline() {
        val export = json.decodeFromString<JournalExport>(oldBackup)
        val experience = export.experiences.single()
        assertTrue(experience.ingestions.single().isPartOfTimeline)
        assertTrue(experience.ratings.single().isPartOfTimeline)
        assertTrue(export.substanceCompanions.single().isPartOfTimeline)
    }

    @Test
    fun hiddenItemsStayHiddenThroughABackup() {
        val time = Instant.ofEpochMilli(1700000000000)
        val export = JournalExport(
            experiences = listOf(
                ExperienceSerializable(
                    title = "New",
                    text = "",
                    sortDate = time,
                    ingestions = listOf(
                        IngestionSerializable(
                            substanceName = "MDMA",
                            time = time,
                            endTime = null,
                            administrationRoute = AdministrationRoute.ORAL,
                            isDoseAnEstimate = false,
                            isPartOfTimeline = false
                        )
                    ),
                    ratings = listOf(RatingSerializable(option = ShulginRatingOption.TWO_PLUS, time = time, isPartOfTimeline = false))
                )
            ),
            substanceCompanions = listOf(SubstanceCompanion("MDMA", AdaptiveColor.PINK, isPartOfTimeline = false))
        )
        val restored = json.decodeFromString<JournalExport>(Json.encodeToString(export))
        val experience = restored.experiences.single()
        assertEquals(false, experience.ingestions.single().isPartOfTimeline)
        assertEquals(false, experience.ratings.single().isPartOfTimeline)
        assertEquals(false, restored.substanceCompanions.single().isPartOfTimeline)
    }
}
