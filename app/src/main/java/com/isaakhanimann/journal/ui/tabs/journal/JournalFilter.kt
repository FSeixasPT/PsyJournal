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

package com.isaakhanimann.journal.ui.tabs.journal

import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import java.time.Duration
import java.util.Locale
import kotlin.math.ceil

enum class SubstanceCount(val label: String) {
    ANY("Any"), ONE("1"), TWO("2"), THREE_OR_MORE("3+");

    fun matches(count: Int): Boolean = when (this) {
        ANY -> true
        ONE -> count == 1
        TWO -> count == 2
        THREE_OR_MORE -> count >= 3
    }
}

// the Journal's filter: experiences with all the chosen substances, how many substances, and the estimated
// duration between minHours and maxHours (null for an open end); only the user's own doses count
data class JournalFilter(
    val substances: Set<String> = emptySet(),
    val substanceCount: SubstanceCount = SubstanceCount.ANY,
    val minHours: Float? = null,
    val maxHours: Float? = null,
) {
    val isActive: Boolean get() = this != JournalFilter()

    // an unknown duration only matches when the duration isn't filtered
    fun matches(ownSubstances: Set<String>, duration: Duration?): Boolean {
        if (!ownSubstances.containsAll(substances)) return false
        if (!substanceCount.matches(ownSubstances.size)) return false
        if (minHours == null && maxHours == null) return true
        val hours = duration?.toMinutes()?.div(60f) ?: return false
        return (minHours == null || hours >= minHours) && (maxHours == null || hours <= maxHours)
    }

    // e.g. "MDMA, LSD · 2 substances · 4–8.5 h"; null without a filter
    fun summary(): String? {
        val parts = mutableListOf<String>()
        if (substances.isNotEmpty()) parts += substances.joinToString(", ")
        when (substanceCount) {
            SubstanceCount.ANY -> {}
            SubstanceCount.ONE -> parts += "1 substance"
            else -> parts += "${substanceCount.label} substances"
        }
        when {
            minHours != null && maxHours != null -> parts += "${hoursText(minHours)}–${hoursText(maxHours)} h"
            minHours != null -> parts += "at least ${hoursText(minHours)} h"
            maxHours != null -> parts += "up to ${hoursText(maxHours)} h"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

fun hoursText(hours: Float): String = String.format(Locale.US, "%.1f", hours).removeSuffix(".0")

fun ownSubstances(ingestions: List<Ingestion>): Set<String> =
    ingestions.filter { it.consumerName == null }.map { it.substanceName }.toSet()

// the substances of each experience, so the ones taken in most experiences come first
fun substancesByUse(experiences: List<Set<String>>): List<String> =
    experiences.flatten().groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

// the slider's right end, in whole hours; there it means "or longer"
fun durationSliderMax(durations: Collection<Duration>): Float =
    maxOf(12f, ceil(durations.maxOfOrNull { it.toMinutes() / 60f } ?: 0f))
