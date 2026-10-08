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
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration

fun exactDuration(onset: Float, comeup: Float, peak: Float, offset: Float) = RoaDuration(
    onset = DurationRange(onset, onset, DurationUnits.MINUTES),
    comeup = DurationRange(comeup, comeup, DurationUnits.MINUTES),
    peak = DurationRange(peak, peak, DurationUnits.MINUTES),
    offset = DurationRange(offset, offset, DurationUnits.MINUTES),
    total = null,
    afterglow = null
)

fun testSubstance(name: String, vararg roas: Roa) = Substance(
    name = name,
    commonNames = emptyList(),
    url = "",
    isApproved = true,
    tolerance = null,
    crossTolerances = emptyList(),
    addictionPotential = null,
    toxicities = emptyList(),
    categories = emptyList(),
    summary = null,
    effectsSummary = null,
    dosageRemark = null,
    generalRisks = null,
    longtermRisks = null,
    saferUse = emptyList(),
    interactions = null,
    roas = roas.toList()
)

val ketamineInsufflatedDose = RoaDose(units = "mg", lightMin = 5.0, commonMin = 30.0, strongMin = 75.0, heavyMin = 175.0)
val ketamineInsufflatedDuration = exactDuration(onset = 10f, comeup = 10f, peak = 20f, offset = 20f)

val ketamine = testSubstance(
    "Ketamine",
    Roa(AdministrationRoute.INSUFFLATED, ketamineInsufflatedDose, ketamineInsufflatedDuration, null),
    Roa(
        AdministrationRoute.ORAL,
        RoaDose(units = "mg", lightMin = 50.0, commonMin = 100.0, strongMin = 300.0, heavyMin = 450.0),
        exactDuration(onset = 30f, comeup = 30f, peak = 60f, offset = 60f).copy(peak = null),
        null
    ),
    Roa(AdministrationRoute.SUBLINGUAL, null, exactDuration(onset = 5f, comeup = 10f, peak = 30f, offset = 30f), null)
)
