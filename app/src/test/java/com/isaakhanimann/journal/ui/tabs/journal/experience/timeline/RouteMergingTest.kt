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
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.AdministrationRoute.INSUFFLATED
import com.isaakhanimann.journal.data.substances.AdministrationRoute.ORAL
import com.isaakhanimann.journal.data.substances.AdministrationRoute.SUBLINGUAL
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class RouteMergingTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")

    private fun duration(onset: Float, comeup: Float, peak: Float, offset: Float) = RoaDuration(
        onset = DurationRange(onset, onset, DurationUnits.MINUTES),
        comeup = DurationRange(comeup, comeup, DurationUnits.MINUTES),
        peak = DurationRange(peak, peak, DurationUnits.MINUTES),
        offset = DurationRange(offset, offset, DurationUnits.MINUTES),
        total = null,
        afterglow = null
    )

    // insufflated: peak 20-40 min after ingestion; oral: peak 60-120 min after ingestion
    private val insufflatedDuration = duration(onset = 10f, comeup = 10f, peak = 20f, offset = 20f)
    private val oralDuration = duration(onset = 30f, comeup = 30f, peak = 60f, offset = 60f)

    private val referenceDoses = mapOf(INSUFFLATED to 52.5, ORAL to 200.0, SUBLINGUAL to 100.0)

    private fun line(
        minutes: Long,
        route: AdministrationRoute,
        dose: Double?,
        units: String = "mg",
        roaDuration: RoaDuration? = if (route == ORAL) oralDuration else insufflatedDuration,
        isReferenceDoseFromDoseRanges: Boolean = true,
    ): DataForOneEffectLine {
        val referenceDose = referenceDoses.getValue(route)
        return DataForOneEffectLine(
            substanceName = "Ketamine",
            route = route,
            roaDuration = roaDuration,
            height = if (dose != null) (dose / referenceDose).toFloat() else 1f,
            horizontalWeight = 0.5f,
            color = AdaptiveColor.PINK,
            startTime = start.plus(minutes, ChronoUnit.MINUTES),
            endTime = null,
            referenceDose = if (dose != null) referenceDose else null,
            doseUnits = if (dose != null) units else null,
            isReferenceDoseFromDoseRanges = isReferenceDoseFromDoseRanges
        )
    }

    private fun model(areRoutesMerged: Boolean, vararg lines: DataForOneEffectLine) = AllTimelinesModel(
        dataForLines = lines.toList(),
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false,
        areRoutesMerged = areRoutesMerged
    )

    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)

    @Test
    fun mergingOnCombinesRoutesIntoOneLine() {
        val details = model(true, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0)).getQuantityDetails(at(30))
        assertEquals(1, details.size)
    }

    @Test
    fun mergingOffKeepsOneLinePerRoute() {
        val details = model(false, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0)).getQuantityDetails(at(30))
        assertEquals(2, details.size)
    }

    @Test
    fun mergedLineListsTheEquivalentOfEachRouteInOrderTaken() {
        // at 30 min the insufflated 52.5 mg is at its peak and the oral dose is not taken yet: one reference dose
        val detail = model(true, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0)).getQuantityDetails(at(30)).single()
        assertEquals(listOf(INSUFFLATED, ORAL), detail.doseEquivalents.map { it.route })
        assertEquals(52.5, detail.doseEquivalents[0].dose, 0.01)
        assertEquals(200.0, detail.doseEquivalents[1].dose, 0.01)
        assertEquals("mg", detail.doseEquivalents[1].units)
    }

    @Test
    fun routesAddUpToTheCombinedEffect() {
        // oral 100 mg (height 0.5) at 0 min peaks 60-120 min; insufflated 52.5 mg (height 1) at 60 min peaks 80-100 min
        // at 90 min both are at their peak: 0.5 + 1 = 1.5
        val details = model(true, line(60, INSUFFLATED, 52.5), line(0, ORAL, 100.0)).getQuantityDetails(at(90)).single()
        assertEquals(1.5 * 52.5, details.doseEquivalents.first { it.route == INSUFFLATED }.dose, 0.01)
    }

    @Test
    fun routeWithoutDoseRangesStaysSeparate() {
        val details = model(
            true,
            line(0, INSUFFLATED, 52.5),
            line(300, ORAL, 200.0, isReferenceDoseFromDoseRanges = false)
        ).getQuantityDetails(at(30))
        assertEquals(2, details.size)
    }

    @Test
    fun routeWithPartialDurationsStaysSeparate() {
        val partial = oralDuration.copy(peak = null, offset = null)
        val model = model(
            true,
            line(0, INSUFFLATED, 52.5),
            line(10, ORAL, 200.0),
            line(20, SUBLINGUAL, 100.0, roaDuration = partial)
        )
        // insufflated and oral merge into one detail; sublingual has no full curve so it has no detail at all
        val detail = model.getQuantityDetails(at(30)).single()
        assertEquals(listOf(INSUFFLATED, ORAL), detail.doseEquivalents.map { it.route })
        assertEquals(2, model.groupDrawables.size)
    }

    @Test
    fun unknownDoseStillMergesButDropsEquivalents() {
        val detail = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, null)).getQuantityDetails(at(30)).single()
        assertTrue(detail.doseEquivalents.isEmpty())
        assertTrue(detail.fractionOfPeak > 0f)
    }

    @Test
    fun mixedUnitsInOneRouteDropsEquivalentsButStillMerges() {
        val detail = model(
            true,
            line(0, INSUFFLATED, 52.5),
            line(10, ORAL, 200.0, units = "mg"),
            line(20, ORAL, 200.0, units = "µg")
        ).getQuantityDetails(at(30)).single()
        assertTrue(detail.doseEquivalents.isEmpty())
    }

    @Test
    fun mergedSubstanceHasOneRedoseChipPerRoute() {
        val options = model(true, line(0, INSUFFLATED, 40.0), line(10, ORAL, 150.0)).redoseOptions
        assertEquals(listOf(INSUFFLATED, ORAL), options.map { it.route })
        assertEquals(40.0, options[0].dose, 0.01)
        assertEquals(150.0, options[1].dose, 0.01)
    }

    @Test
    fun plannedDoseOfOneRouteMergesIntoTheSubstanceLine() {
        val real = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, 200.0))
        val oral = real.redoseOptions.first { it.route == ORAL }
        val preview = real.withPlannedIngestion(oral, dose = 200.0, time = at(20))
        assertEquals(1, preview.getQuantityDetails(at(30)).size)
    }

    @Test
    fun peakOfMergedLineIsExpressedInThePlannedRouteAndNamesIt() {
        // only insufflated taken so far, as 52.5 mg at 0; plan 52.5 mg insufflated at 0 as well: peak 2 x 52.5 = 105 mg
        val real = model(true, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0))
        val insufflated = real.redoseOptions.first { it.route == INSUFFLATED }
        val peak = real.withPlannedIngestion(insufflated, dose = 52.5, time = at(0)).plannedPeak!!
        assertEquals(105.0, peak.doseEquivalent, 0.01)
        assertEquals("mg", peak.units)
        assertEquals(INSUFFLATED, peak.route)
    }

    @Test
    fun peakOfUnmergedLineDoesNotNameTheRoute() {
        val real = model(false, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0))
        val insufflated = real.redoseOptions.first { it.route == INSUFFLATED }
        val peak = real.withPlannedIngestion(insufflated, dose = 52.5, time = at(0)).plannedPeak!!
        assertEquals(null, peak.route)
    }

    @Test
    fun unknownDoseRouteHasNoChipButOtherRouteStillMerges() {
        val real = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, null))
        assertEquals(listOf(INSUFFLATED), real.redoseOptions.map { it.route })
        val preview = real.withPlannedIngestion(real.redoseOptions.single(), dose = 52.5, time = at(20))
        assertEquals(1, preview.getQuantityDetails(at(30)).size)
    }
}
