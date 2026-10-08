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

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.horizontalWeightFromDots
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullDurations
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.roundToSignificantDigits
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import java.time.Instant

// an ingestion that is previewed on the chart and can be logged, the dose is in the database unit of the route
data class PlannedIngestion(
    val substanceName: String,
    val route: AdministrationRoute,
    val dose: Double,
    val units: String
)

enum class PreviewProblem { NO_FULL_DURATION, NO_DOSE_RANGES }

// null if a planned ingestion of this route can be drawn and compared with other ingestions
fun Roa.previewProblem(): PreviewProblem? = when {
    roaDuration?.toFullDurations() == null -> PreviewProblem.NO_FULL_DURATION
    roaDose?.averageCommonDose == null -> PreviewProblem.NO_DOSE_RANGES
    else -> null
}

fun buildPlannedLine(
    planned: PlannedIngestion,
    substance: Substance,
    color: AdaptiveColor,
    time: Instant
): DataForOneEffectLine? {
    val roa = substance.getRoa(planned.route) ?: return null
    if (roa.previewProblem() != null) return null
    val roaDose = roa.roaDose ?: return null
    if (planned.units != roaDose.units) return null
    val referenceDose = roaDose.averageCommonDose ?: return null
    return DataForOneEffectLine(
        substanceName = planned.substanceName,
        route = planned.route,
        roaDuration = roa.roaDuration,
        height = (planned.dose / referenceDose).toFloat(),
        horizontalWeight = horizontalWeightFromDots(roaDose.getNumDots(planned.dose, planned.units)),
        color = color,
        startTime = time,
        endTime = null,
        referenceDose = referenceDose,
        doseUnits = planned.units,
        isReferenceDoseFromDoseRanges = true
    )
}

// the color the substance already has in the journal, otherwise one that is not yet on the chart
fun choosePlannedColor(
    substanceName: String,
    companions: List<SubstanceCompanion>,
    colorsOnChart: List<AdaptiveColor>
): AdaptiveColor {
    companions.firstOrNull { it.substanceName == substanceName }?.let { return it.color }
    return AdaptiveColor.entries.firstOrNull { it !in colorsOnChart } ?: AdaptiveColor.entries.first()
}

// the first dose of that substance and route if it was logged in the database unit, else the start of the common range
fun suggestedDose(
    substanceName: String,
    route: AdministrationRoute,
    realLines: List<DataForOneEffectLine>,
    roaDose: RoaDose
): Double? {
    val firstLine = realLines
        .filter { it.substanceName == substanceName && it.route == route && it.referenceDose != null }
        .minByOrNull { it.startTime }
    if (firstLine != null && firstLine.doseUnits == roaDose.units) {
        // the height is a Float, rounding removes the noise that adds to the logged dose
        return roundToSignificantDigits(firstLine.height * firstLine.referenceDose!!, 6)
    }
    return roaDose.commonMin
}

// the first logged ingestion with a known dose whose substance and route can be previewed
fun defaultPlannedIngestion(
    realLines: List<DataForOneEffectLine>,
    getSubstance: (String) -> Substance?
): PlannedIngestion? {
    for (line in realLines.sortedBy { it.startTime }) {
        if (line.referenceDose == null) continue
        val roa = getSubstance(line.substanceName)?.getRoa(line.route) ?: continue
        if (roa.previewProblem() != null) continue
        val roaDose = roa.roaDose ?: continue
        val dose = suggestedDose(line.substanceName, line.route, realLines, roaDose) ?: continue
        return PlannedIngestion(line.substanceName, line.route, dose, roaDose.units)
    }
    return null
}

fun PlannedIngestion.chipText(): String =
    "$substanceName · ${dose.toReadableString()} $units ${route.displayText.lowercase()}"
