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

package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.PlannedIngestion
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.chipText
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.plannedTimeText
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

data class SubstanceLine(
    val label: String,
    val color: AdaptiveColor,
    val phase: Phase,
    val percentOfPeak: Int?,
    val hint: TimeHint?,
    // when the line reaches zero, null if unknown
    val endsAt: Instant?,
    val text: String,
) {
    val isActive: Boolean get() = phase != Phase.ENDED && phase != Phase.NO_TIMELINE

    // for the collapsed notification, where all substances share one line
    val shortText: String
        get() = when {
            percentOfPeak == null -> "$label ${phase.word}"
            phase == Phase.COMING_UP -> "$label $percentOfPeak% ▲"
            phase == Phase.COMING_DOWN -> "$label $percentOfPeak% ▼"
            else -> "$label $percentOfPeak% ${phase.word}"
        }
}

data class LiveNotificationContent(
    val title: String,
    // in the order the lines are drawn on the chart
    val lines: List<SubstanceLine>,
    val plannedLine: String?,
    // when the last active line ends, null if one of them has an unknown end
    val endsAt: Instant?,
    val isFinished: Boolean,
    val nextRefreshAt: Instant,
)

// the notification is about the ongoing experience, so Auto is relative to now, like the redose chip
fun liveTimeDisplayOption(saved: SavedTimeDisplayOption): TimeDisplayOption = when (saved) {
    SavedTimeDisplayOption.AUTO, SavedTimeDisplayOption.RELATIVE_TO_NOW -> TimeDisplayOption.RELATIVE_TO_NOW
    SavedTimeDisplayOption.RELATIVE_TO_START -> TimeDisplayOption.RELATIVE_TO_START
    SavedTimeDisplayOption.TIME_BETWEEN -> TimeDisplayOption.TIME_BETWEEN
    SavedTimeDisplayOption.REGULAR -> TimeDisplayOption.REGULAR
}

private val longestRefreshInterval: Duration = Duration.ofMinutes(5)
private val shortestRefreshInterval: Duration = Duration.ofMinutes(1)

fun buildLiveNotificationContent(
    title: String,
    model: AllTimelinesModel,
    lines: List<DataForOneEffectLine>,
    plannedIngestion: PlannedIngestion?,
    savedTimeDisplayOption: SavedTimeDisplayOption,
    now: Instant,
): LiveNotificationContent {
    val timeOption = liveTimeDisplayOption(savedTimeDisplayOption)
    val curves = model.substanceCurves()
    val substanceLines = curves.map { curve ->
        val hasSeveralLines = curves.count { it.substanceName == curve.substanceName } > 1
        val label = if (hasSeveralLines) {
            "${curve.substanceName} (${curve.routes.joinToString { it.displayText.lowercase() }})"
        } else {
            curve.substanceName
        }
        val line = curve.line
        val phaseWithHint: PhaseWithHint
        val percent: Int?
        val endsAt: Instant?
        if (line != null) {
            phaseWithHint = line.phaseWithHint(now)
            val isOnTheLine = phaseWithHint.phase != Phase.ENDED && phaseWithHint.phase != Phase.WAITING_FOR_ONSET
            percent = if (isOnTheLine) (line.heightAt(now) / line.maxHeight * 100).roundToInt() else null
            endsAt = line.endTime
        } else {
            val result = fallbackPhase(lines.filter { it.substanceName == curve.substanceName && it.route in curve.routes }, now)
            phaseWithHint = result.phaseWithHint
            percent = null
            endsAt = result.knownEnd
        }
        val hintText = phaseWithHint.hint?.let { "${it.kind.word} ${plannedTimeText(it.time, now, model.startTime, timeOption)}" }
        val text = listOfNotNull(label, phaseWithHint.phase.word, percent?.let { "$it%" }, hintText).joinToString(" · ")
        SubstanceLine(label, curve.color, phaseWithHint.phase, percent, phaseWithHint.hint, endsAt, text)
    }
    val active = substanceLines.filter { it.isActive }
    val endsAt = if (active.isNotEmpty() && active.all { it.endsAt != null }) active.maxOf { it.endsAt!! } else null
    val latestRefresh = now + longestRefreshInterval
    val nextChange = active.mapNotNull { it.hint?.time }.filter { it > now }.minOrNull()
    val nextRefreshAt = maxOf(minOf(nextChange ?: latestRefresh, latestRefresh), now + shortestRefreshInterval)
    return LiveNotificationContent(
        title = title,
        lines = substanceLines,
        plannedLine = plannedIngestion?.let { "Planned: " + it.chipText() },
        endsAt = endsAt,
        isFinished = active.isEmpty(),
        nextRefreshAt = nextRefreshAt,
    )
}

// whether a live notification would show anything, used for the switch on the experience screen
fun hasActiveEffects(
    lines: List<DataForOneEffectLine>,
    areSubstanceHeightsIndependent: Boolean,
    areRoutesMerged: Boolean,
    now: Instant,
): Boolean {
    if (lines.isEmpty()) return false
    val model = AllTimelinesModel(lines, emptyList(), emptyList(), areSubstanceHeightsIndependent, areRoutesMerged)
    return !buildLiveNotificationContent("", model, lines, null, SavedTimeDisplayOption.REGULAR, now).isFinished
}
