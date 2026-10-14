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

import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.CurveLine
import java.time.Duration
import java.time.Instant

enum class Phase { WAITING_FOR_ONSET, COMING_UP, PEAKING, STEADY, COMING_DOWN, ACTIVE, ENDED, NO_TIMELINE }

enum class HintKind { STARTS, PEAKS, COMES_DOWN, RISES_AGAIN, ENDS }

data class TimeHint(val kind: HintKind, val time: Instant)

data class PhaseWithHint(val phase: Phase, val hint: TimeHint?)

val Phase.word: String
    get() = when (this) {
        Phase.WAITING_FOR_ONSET -> "waiting for onset"
        Phase.COMING_UP -> "coming up"
        Phase.PEAKING -> "peaking"
        Phase.STEADY -> "steady"
        Phase.COMING_DOWN -> "coming down"
        Phase.ACTIVE -> "active"
        Phase.ENDED -> "ended"
        Phase.NO_TIMELINE -> "no timeline"
    }

val HintKind.word: String
    get() = when (this) {
        HintKind.STARTS -> "starts"
        HintKind.PEAKS -> "peaks"
        HintKind.COMES_DOWN -> "comes down"
        HintKind.RISES_AGAIN -> "rises again"
        HintKind.ENDS -> "ends"
    }

// the direction is measured over two minutes around a time, so hint times are accurate to about a minute
private val halfWindow: Duration = Duration.ofMinutes(1)
private val step: Duration = Duration.ofMinutes(1)

private fun CurveLine.isZero(height: Float) = height <= 0.001f * maxHeight

// the phase follows the summed line, so a small top-up doesn't count as coming up while the line goes down
fun CurveLine.phaseAt(time: Instant): Phase {
    if (maxHeight <= 0f) return Phase.ENDED
    val height = heightAt(time)
    if (isZero(height)) {
        val risesLater = firstTimeAfter(time) { !isZero(heightAt(it)) } != null
        return if (risesLater) Phase.WAITING_FOR_ONSET else Phase.ENDED
    }
    val change = heightAt(time + halfWindow) - heightAt(time - halfWindow)
    // the line is made of straight pieces, so any rise or fall counts; a slow comedown like
    // oral caffeine's is still coming down, the tolerance only absorbs rounding
    val threshold = 0.0002f * maxHeight
    return when {
        change > threshold -> Phase.COMING_UP
        change < -threshold -> Phase.COMING_DOWN
        height >= 0.5f * maxHeight -> Phase.PEAKING
        else -> Phase.STEADY
    }
}

fun CurveLine.phaseWithHint(now: Instant): PhaseWithHint {
    val phase = phaseAt(now)
    val hint = when (phase) {
        Phase.WAITING_FOR_ONSET ->
            firstTimeAfter(now) { !isZero(heightAt(it)) }?.let { TimeHint(HintKind.STARTS, it) }

        Phase.COMING_UP -> endOfRiseAfter(now)?.let { TimeHint(HintKind.PEAKS, it) }

        Phase.PEAKING, Phase.STEADY -> {
            val change = firstTimeAfter(now) {
                isZero(heightAt(it)) || phaseAt(it).let { p -> p == Phase.COMING_DOWN || p == Phase.COMING_UP }
            }
            when {
                change == null -> null
                isZero(heightAt(change)) -> TimeHint(HintKind.ENDS, change)
                phaseAt(change) == Phase.COMING_DOWN -> TimeHint(HintKind.COMES_DOWN, change)
                // a redose kicking in: when it starts matters, and "peaking, peaks in" would contradict itself
                else -> TimeHint(HintKind.RISES_AGAIN, change)
            }
        }

        // flat stretches on the way down don't count, only a new rise or the end
        Phase.COMING_DOWN -> {
            val change = firstTimeAfter(now) { isZero(heightAt(it)) || phaseAt(it) == Phase.COMING_UP }
            when {
                change == null -> null
                isZero(heightAt(change)) -> TimeHint(HintKind.ENDS, change)
                else -> TimeHint(HintKind.RISES_AGAIN, change)
            }
        }

        else -> null
    }
    return PhaseWithHint(phase, hint)
}

private fun CurveLine.endOfRiseAfter(time: Instant): Instant? =
    firstTimeAfter(time) { phaseAt(it) != Phase.COMING_UP }

// the first whole step after the given time at which the condition holds, up to the end of the line
private fun CurveLine.firstTimeAfter(time: Instant, condition: (Instant) -> Boolean): Instant? {
    var candidate = time + step
    val last = endTime + step
    while (candidate <= last) {
        if (condition(candidate)) return candidate
        candidate += step
    }
    return null
}
