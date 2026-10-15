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

package com.isaakhanimann.journal.data.supplements

import java.time.Duration
import java.time.Instant

// the after-dose reminders are offered for an MDMA dose of the user's own from the last 2 hours
fun routineOfferDose(
    ownMdmaTimes: List<Instant>,
    now: Instant,
    isRoutineRunning: Boolean,
    isDismissed: Boolean,
    // the "Suggest MDMA supplement routine" setting
    isSuggestionOn: Boolean = true,
): Instant? {
    if (isRoutineRunning || isDismissed || !isSuggestionOn) return null
    return ownMdmaTimes.filter { !it.isAfter(now) && Duration.between(it, now) <= Duration.ofHours(2) }.maxOrNull()
}

// the ingestion the offer is about, so the routine can be linked to it
fun offeredIngestionId(ownMdma: List<Pair<Int, Instant>>, now: Instant): Int? =
    ownMdma.filter { (_, time) -> !time.isAfter(now) && Duration.between(time, now) <= Duration.ofHours(2) }
        .maxByOrNull { (_, time) -> time }?.first
