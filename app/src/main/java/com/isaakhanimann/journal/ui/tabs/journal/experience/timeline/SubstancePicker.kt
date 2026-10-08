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

import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
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
    experienceSubstanceNamesByRecency: List<String>,
    visibility: RouteVisibility = RouteVisibility()
): List<PickerEntry> {
    val byName = matchingSubstances.associateBy { it.name }
    val inExperience = experienceSubstanceNamesByRecency.distinct().mapNotNull { byName[it] }
    val others = matchingSubstances.filter { it.name !in experienceSubstanceNamesByRecency }
    return inExperience.map { PickerEntry(it.name, isInExperience = true, problem = it.substancePreviewProblem(visibility)) } +
            others.map { PickerEntry(it.name, isInExperience = false, problem = it.substancePreviewProblem(visibility)) }
}

// why no shown route of the substance can be previewed; hidden routes only matter if they could be previewed
fun Substance.substancePreviewProblem(visibility: RouteVisibility = RouteVisibility()): PreviewProblem? {
    val shownRoas = roas.filter { visibility.isShown(name, it.route) }
    if (shownRoas.any { it.previewProblem() == null }) return null
    if (roas.any { !visibility.isShown(name, it.route) && it.previewProblem() == null }) return PreviewProblem.ROUTES_HIDDEN
    val problems = roas.map { it.previewProblem() }
    return when {
        problems.isNotEmpty() && problems.all { it == PreviewProblem.NO_DOSE_RANGES } -> PreviewProblem.NO_DOSE_RANGES
        else -> PreviewProblem.NO_FULL_DURATION
    }
}

fun preselectedRoute(
    substance: Substance,
    experienceRoutesByRecency: List<AdministrationRoute>,
    visibility: RouteVisibility = RouteVisibility()
): AdministrationRoute? {
    val previewableRoutes = substance.roas
        .filter { it.previewProblem() == null && visibility.isShown(substance.name, it.route) }
        .map { it.route }
    return experienceRoutesByRecency.firstOrNull { it in previewableRoutes } ?: previewableRoutes.firstOrNull()
}

// the routes offered in the editor: the shown ones, and the route being edited so a selection never disappears
fun editorRoutes(substance: Substance, visibility: RouteVisibility, plannedRoute: AdministrationRoute?): List<Roa> =
    substance.roas.filter { visibility.isShown(substance.name, it.route) || it.route == plannedRoute }

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
