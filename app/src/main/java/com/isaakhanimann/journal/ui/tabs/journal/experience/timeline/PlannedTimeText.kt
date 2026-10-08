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

import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.getDurationText
import com.isaakhanimann.journal.ui.utils.getShortTimeText
import java.time.Instant

// a time below the planned-ingestion chip, in the time display option chosen with the stopwatch
// (auto is relative to now while the experience is ongoing)
fun plannedTimeText(time: Instant, now: Instant, experienceStart: Instant, option: TimeDisplayOption): String =
    when (option) {
        TimeDisplayOption.RELATIVE_TO_NOW ->
            if (time < now) getDurationText(fromInstant = time, toInstant = now) + " ago"
            else "in " + getDurationText(fromInstant = now, toInstant = time)

        TimeDisplayOption.RELATIVE_TO_START -> getDurationText(fromInstant = experienceStart, toInstant = time) + " in"
        TimeDisplayOption.TIME_BETWEEN, TimeDisplayOption.REGULAR -> "at " + time.getShortTimeText()
    }

// a range of times like "in 45 min–2.8 hr" or "10:17 PM–12:17 AM"
fun plannedTimeRangeText(from: Instant, to: Instant, now: Instant, experienceStart: Instant, option: TimeDisplayOption): String =
    when (option) {
        TimeDisplayOption.RELATIVE_TO_NOW ->
            if (from >= now) "in " + getDurationText(now, from) + "–" + getDurationText(now, to)
            else plannedTimeText(from, now, experienceStart, option) + "–" + plannedTimeText(to, now, experienceStart, option)

        TimeDisplayOption.RELATIVE_TO_START ->
            getDurationText(experienceStart, from) + "–" + getDurationText(experienceStart, to) + " in"

        TimeDisplayOption.TIME_BETWEEN, TimeDisplayOption.REGULAR ->
            from.getShortTimeText() + "–" + to.getShortTimeText()
    }

// the chip's texts are about the near future, so "Auto" always means relative to now for them
fun chipTimeDisplayOption(saved: SavedTimeDisplayOption, resolved: TimeDisplayOption): TimeDisplayOption =
    if (saved == SavedTimeDisplayOption.AUTO) TimeDisplayOption.RELATIVE_TO_NOW else resolved
