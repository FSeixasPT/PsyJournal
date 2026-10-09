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

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// quick choices for when an ingestion was taken, instead of opening the clock
data class RecentTime(val minutesAgo: Long, val label: String) {
    fun timeBefore(now: LocalDateTime): LocalDateTime = now.minusMinutes(minutesAgo)
}

val recentTimes = listOf(
    RecentTime(0, "Now"),
    RecentTime(5, "5 min ago"),
    RecentTime(15, "15 min ago"),
    RecentTime(30, "30 min ago"),
    RecentTime(60, "1 hr ago"),
)

// the choice that gives the same minute as time, so a time picked with the clock can match too
fun selectedRecentTime(time: LocalDateTime, now: LocalDateTime): RecentTime? {
    val minute = time.truncatedTo(ChronoUnit.MINUTES)
    return recentTimes.firstOrNull { it.timeBefore(now).truncatedTo(ChronoUnit.MINUTES) == minute }
}
