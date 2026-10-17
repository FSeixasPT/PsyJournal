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
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ImportFileTest {
    private val march = Instant.parse("2024-03-03T20:00:00Z")
    private val october = Instant.parse("2026-10-12T20:00:00Z")

    private fun ingestion(at: Instant) = IngestionSerializable(
        substanceName = "MDMA", time = at, endTime = null, administrationRoute = AdministrationRoute.ORAL, isDoseAnEstimate = false
    )

    private fun experience(at: Instant, ingestions: Int) =
        ExperienceSerializable(title = "Night", text = "", sortDate = at, ingestions = List(ingestions) { ingestion(at) })

    @Test
    fun anExportIsSummedUpBeforeAnythingIsReplaced() {
        val text = Json.encodeToString(JournalExport.serializer(), JournalExport(experiences = listOf(experience(october, 2), experience(march, 1))))
        val read = readImportFile(text) as ImportFile.Export
        assertEquals(ImportSummary(experiences = 2, ingestions = 3, firstDate = march, lastDate = october, customSubstances = 0, customUnits = 0), read.summary)
        assertEquals(2, read.journal.experiences.size)
    }

    @Test
    fun anEmptyJournalsExportIsStillAnExport() {
        // empty lists aren't written, so the backup of an empty journal is just {}
        val read = readImportFile("{}") as ImportFile.Export
        assertEquals(0, read.summary.experiences)
        assertEquals(null, read.summary.firstDate)
    }

    @Test
    fun unrelatedJsonIsNotAnExport() {
        assertEquals(ImportFile.NotAnExport, readImportFile("""{"not_a_journal":"Synthetic unrelated JSON document"}"""))
        assertEquals(ImportFile.NotAnExport, readImportFile("""[1, 2, 3]"""))
        assertEquals(ImportFile.NotAnExport, readImportFile("\"hello\""))
    }

    @Test
    fun aBrokenFileCantBeRead() {
        assertEquals(ImportFile.Unreadable, readImportFile("""{"experiences": [ {"title": "Ni"""))
        // the right keys but the wrong shapes
        assertEquals(ImportFile.Unreadable, readImportFile("""{"experiences": 5}"""))
    }

    @Test
    fun theSummaryReadsLikeASentence() {
        val summary = ImportSummary(experiences = 42, ingestions = 118, firstDate = march, lastDate = october, customSubstances = 5, customUnits = 1)
        val text = summary.text(formatDate = { if (it == march) "3 Mar 2024" else "12 Oct 2026" })
        assertEquals("This file contains 42 experiences with 118 ingestions, from 3 Mar 2024 to 12 Oct 2026, plus 5 custom substances and 1 custom unit.", text)
        assertTrue(ImportSummary(1, 1, march, march, 0, 0).text { "3 Mar 2024" }.startsWith("This file contains 1 experience with 1 ingestion, on 3 Mar 2024."))
        assertEquals("This file contains no experiences.", ImportSummary(0, 0, null, null, 0, 0).text { "" })
    }

    @Test
    fun substancesWithoutAColorGetOneSoTheJournalCanShowThem() {
        val lsd = experience(october, 0).copy(ingestions = listOf(ingestion(october).copy(substanceName = "LSD")))
        val export = JournalExport(
            experiences = listOf(experience(march, 1), lsd),
            substanceCompanions = listOf(SubstanceCompanion("MDMA", AdaptiveColor.PINK))
        )
        val read = readImportFile(Json.encodeToString(JournalExport.serializer(), export)) as ImportFile.Export
        val companions = read.journal.substanceCompanions.associateBy { it.substanceName }
        assertEquals(setOf("MDMA", "LSD"), companions.keys)
        // the file's own color is kept, and the new one differs from it
        assertEquals(AdaptiveColor.PINK, companions.getValue("MDMA").color)
        assertTrue(companions.getValue("LSD").color != AdaptiveColor.PINK)
    }
}
