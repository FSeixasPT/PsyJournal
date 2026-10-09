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

import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullDurationRange
import java.time.Duration
import java.time.Instant

data class FallbackResult(val phaseWithHint: PhaseWithHint, val knownEnd: Instant?)

// a substance with an unknown end counts as ended this long after its ingestion
private val unknownEndAfter: Duration = Duration.ofHours(12)

private class FallbackTimes(
    val start: Instant,
    val onsetEnd: Instant?,
    val comeupEnd: Instant?,
    val peakEnd: Instant?,
    val offsetEnd: Instant?,
    val totalEnd: Instant?,
) {
    val knownEnd: Instant? get() = offsetEnd ?: totalEnd
    val end: Instant get() = knownEnd ?: (start + unknownEndAfter)

    fun phaseWithHint(now: Instant): PhaseWithHint {
        if (now >= end) return PhaseWithHint(Phase.ENDED, null)
        if (onsetEnd == null) return PhaseWithHint(Phase.ACTIVE, totalEnd?.let { TimeHint(HintKind.ENDS, it) })
        val steps = listOf(
            Triple(Phase.WAITING_FOR_ONSET, onsetEnd, HintKind.STARTS),
            Triple(Phase.COMING_UP, comeupEnd, HintKind.PEAKS),
            Triple(Phase.PEAKING, peakEnd, HintKind.COMES_DOWN),
            Triple(Phase.COMING_DOWN, offsetEnd, HintKind.ENDS),
        )
        for ((phase, boundary, kind) in steps) {
            // the last phase that can be worked out
            if (boundary == null) return PhaseWithHint(phase, totalEnd?.let { TimeHint(HintKind.ENDS, it) })
            if (now < boundary) return PhaseWithHint(phase, TimeHint(kind, boundary))
        }
        return PhaseWithHint(Phase.ENDED, null)
    }
}

// the middle of a duration range, the same weight the chart uses for onset and comeup
private fun DurationRange?.middleInSeconds(): Long? =
    this?.toFullDurationRange()?.interpolateAtValueInSeconds(0.5f)?.toLong()

private fun DataForOneEffectLine.fallbackTimes(): FallbackTimes? {
    val duration = roaDuration ?: return null
    val onsetEnd = duration.onset.middleInSeconds()?.let { startTime.plusSeconds(it) }
    val comeupEnd = onsetEnd?.let { onset -> duration.comeup.middleInSeconds()?.let { onset.plusSeconds(it) } }
    // a time range keeps peaking until its end plus the peak duration
    val peakStart = comeupEnd?.let { comeup -> endTime?.let { maxOf(comeup, it) } ?: comeup }
    val peakEnd = peakStart?.let { peak -> duration.peak.middleInSeconds()?.let { peak.plusSeconds(it) } }
    val offsetEnd = peakEnd?.let { peak -> duration.offset.middleInSeconds()?.let { peak.plusSeconds(it) } }
    val totalEnd = duration.total.middleInSeconds()?.let { (endTime ?: startTime).plusSeconds(it) }
    if (onsetEnd == null && totalEnd == null) return null
    return FallbackTimes(startTime, onsetEnd, comeupEnd, peakEnd, offsetEnd, totalEnd)
}

// for substances whose durations are too incomplete for a summed line: the most recent ingestion that hasn't ended decides
fun fallbackPhase(lines: List<DataForOneEffectLine>, now: Instant): FallbackResult {
    val timed = lines.mapNotNull { it.fallbackTimes() }
    if (timed.isEmpty()) return FallbackResult(PhaseWithHint(Phase.NO_TIMELINE, null), null)
    val active = timed.filter { now < it.end }.maxByOrNull { it.start }
        ?: return FallbackResult(PhaseWithHint(Phase.ENDED, null), null)
    return FallbackResult(active.phaseWithHint(now), active.knownEnd)
}
