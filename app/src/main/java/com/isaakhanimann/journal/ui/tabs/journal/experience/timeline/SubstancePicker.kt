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

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toPreservedString
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString

data class PickerEntry(
    val substanceName: String,
    val isInExperience: Boolean,
    // null if at least one route can be previewed
    val problem: PreviewProblem?
)

// the substances of the experience first (most recent first), then the other matches in their given order
fun buildPickerEntries(
    matchingSubstances: List<Substance>,
    experienceSubstanceNamesByRecency: List<String>
): List<PickerEntry> {
    val byName = matchingSubstances.associateBy { it.name }
    val inExperience = experienceSubstanceNamesByRecency.distinct().mapNotNull { byName[it] }
    val others = matchingSubstances.filter { it.name !in experienceSubstanceNamesByRecency }
    return inExperience.map { PickerEntry(it.name, isInExperience = true, problem = it.substancePreviewProblem()) } +
            others.map { PickerEntry(it.name, isInExperience = false, problem = it.substancePreviewProblem()) }
}

// the reason why no route of the substance can be previewed, missing durations win over missing dose ranges
fun Substance.substancePreviewProblem(): PreviewProblem? {
    val problems = roas.map { it.previewProblem() }
    return when {
        problems.any { it == null } -> null
        problems.isNotEmpty() && problems.all { it == PreviewProblem.NO_DOSE_RANGES } -> PreviewProblem.NO_DOSE_RANGES
        else -> PreviewProblem.NO_FULL_DURATION
    }
}

fun preselectedRoute(substance: Substance, experienceRoutesByRecency: List<AdministrationRoute>): AdministrationRoute? {
    val previewableRoutes = substance.roas.filter { it.previewProblem() == null }.map { it.route }
    return experienceRoutesByRecency.firstOrNull { it in previewableRoutes } ?: previewableRoutes.firstOrNull()
}

// only plain decimal numbers, so text like "Infinity", "1e400" or "0x1p3" is not accepted as a dose
private val plainDecimal = Regex("""\d+(\.\d*)?|\.\d+""")

fun parseDose(text: String): Double? {
    val normalized = text.trim().replace(',', '.')
    if (!plainDecimal.matches(normalized)) return null
    return normalized.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
}

fun RoaDose.rangesText(): String {
    val parts = listOfNotNull(
        if (lightMin != null && commonMin != null) "light ${lightMin.toReadableString()}–${commonMin.toReadableString()}" else null,
        if (commonMin != null && strongMin != null) "common ${commonMin.toReadableString()}–${strongMin.toReadableString()}" else null,
        if (strongMin != null && heavyMin != null) "strong ${strongMin.toReadableString()}–${heavyMin.toReadableString()}" else null,
        if (heavyMin != null) "heavy ${heavyMin.toReadableString()}+" else null,
    )
    return parts.joinToString(" · ") + " $units"
}

// every digit of the dose, so that confirming the editor without changes keeps the dose
fun doseFieldText(dose: Double): String = dose.toPreservedString()
