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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

// what an import file holds, read before anything in the journal is replaced
sealed interface ImportFile {
    data class Export(val journal: JournalExport, val summary: ImportSummary) : ImportFile
    // valid JSON, but none of an export's keys, e.g. an unrelated document
    data object NotAnExport : ImportFile
    data object Unreadable : ImportFile
}

data class ImportSummary(
    val experiences: Int,
    val ingestions: Int,
    val firstDate: Instant?,
    val lastDate: Instant?,
    val customSubstances: Int,
    val customUnits: Int,
) {
    // e.g. "This file contains 42 experiences with 118 ingestions, from 3 Mar 2024 to 12 Oct 2026, plus 5 custom substances."
    fun text(formatDate: (Instant) -> String): String {
        if (experiences == 0) return "This file contains no experiences."
        val dates = if (firstDate != null && lastDate != null) {
            val first = formatDate(firstDate)
            val last = formatDate(lastDate)
            if (first == last) ", on $first" else ", from $first to $last"
        } else {
            ""
        }
        val extras = listOfNotNull(
            customSubstances.takeIf { it > 0 }?.let { count(it, "custom substance") },
            customUnits.takeIf { it > 0 }?.let { count(it, "custom unit") },
        )
        val plus = if (extras.isEmpty()) "" else ", plus " + extras.joinToString(" and ")
        return "This file contains ${count(experiences, "experience")} with ${count(ingestions, "ingestion")}$dates$plus."
    }
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

// the keys an export writes; empty lists aren't written, so an empty journal's export is just {}
private val exportKeys = setOf("experiences", "substanceCompanions", "customSubstances", "customUnits")

private val json = Json { ignoreUnknownKeys = true }

fun readImportFile(text: String): ImportFile {
    val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return ImportFile.Unreadable
    if (element !is JsonObject) return ImportFile.NotAnExport
    if (element.isNotEmpty() && element.keys.none { it in exportKeys }) return ImportFile.NotAnExport
    val decoded = runCatching { json.decodeFromJsonElement<JournalExport>(element) }.getOrNull() ?: return ImportFile.Unreadable
    val journal = decoded.copy(substanceCompanions = withMissingCompanions(decoded))
    val dates = journal.experiences.map { it.sortDate }
    return ImportFile.Export(
        journal,
        ImportSummary(
            experiences = journal.experiences.size,
            ingestions = journal.experiences.sumOf { it.ingestions.size },
            firstDate = dates.minOrNull(),
            lastDate = dates.maxOrNull(),
            customSubstances = journal.customSubstances.size,
            customUnits = journal.customUnits.size,
        )
    )
}

// the journal needs a color for every substance it shows; an export without one (made by hand or by
// another tool) gets built-in colors, preferring ones the file doesn't use yet
private fun withMissingCompanions(journal: JournalExport): List<SubstanceCompanion> {
    val known = journal.substanceCompanions.map { it.substanceName }.toSet()
    val missing = (journal.experiences.flatMap { e -> e.ingestions.map { it.substanceName } } + journal.customUnits.map { it.substanceName })
        .distinct()
        .filter { it !in known }
    if (missing.isEmpty()) return journal.substanceCompanions
    val used = journal.substanceCompanions.map { it.color }.toSet()
    val palette = AdaptiveColor.pickerColors.filter { it !in used }.ifEmpty { AdaptiveColor.pickerColors }
    return journal.substanceCompanions + missing.mapIndexed { i, name -> SubstanceCompanion(name, palette[i % palette.size]) }
}
