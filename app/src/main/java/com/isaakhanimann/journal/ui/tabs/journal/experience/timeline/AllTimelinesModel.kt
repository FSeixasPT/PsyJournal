/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.AxisDrawable
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.GroupDrawable
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AllTimelinesModel(
    dataForLines: List<DataForOneEffectLine>,
    val dataForRatings: List<DataForOneRating>,
    val timedNotes: List<DataForOneTimedNote>,
    areSubstanceHeightsIndependent: Boolean,
) {
    val startTime: Instant
    val widthInSeconds: Float
    val groupDrawables: List<GroupDrawable>
    val axisDrawable: AxisDrawable

    data class RoaGroup(
        val substanceName: String,
        val route: AdministrationRoute,
        val color: AdaptiveColor,
        val roaDuration: RoaDuration?,
        val weightedLines: List<WeightedLine>,
        val referenceDose: Double?,
        val doseUnits: String?
    )

    private val roaGroups: List<RoaGroup>

    init {
        val ratingTimes = dataForRatings.map { it.time }
        val ingestionTimes = dataForLines.map { it.startTime }
        val noteTimes = timedNotes.map { it.time }
        val allStartTimeCandidates = ratingTimes + ingestionTimes + noteTimes
        startTime =
            allStartTimeCandidates.reduce { acc, date -> if (acc.isBefore(date)) acc else date }
        val roaGroups = dataForLines.groupBy { it.substanceName }
            .flatMap { substanceGroup ->
                val linesPerSubstance = substanceGroup.value
                return@flatMap linesPerSubstance.groupBy { it.route }.map { routeGroup ->
                    val linesPerRoute = routeGroup.value
                    val isDoseEquivalentKnown = linesPerRoute.all { it.referenceDose != null } &&
                            linesPerRoute.map { it.doseUnits }.distinct().size == 1
                    return@map RoaGroup(
                        substanceName = substanceGroup.key,
                        route = routeGroup.key,
                        referenceDose = if (isDoseEquivalentKnown) linesPerRoute.first().referenceDose else null,
                        doseUnits = if (isDoseEquivalentKnown) linesPerRoute.first().doseUnits else null,
                        color = linesPerRoute.first().color,
                        roaDuration = linesPerRoute.first().roaDuration,
                        weightedLines = linesPerRoute.map {
                            WeightedLine(
                                startTime = it.startTime,
                                endTime = it.endTime,
                                horizontalWeight = it.horizontalWeight,
                                height = it.height
                            )
                        })
                }
            }
        this.roaGroups = roaGroups
        val groupDrawables = roaGroups.map { group ->
            GroupDrawable(
                startTimeGraph = startTime,
                color = group.color,
                roaDuration = group.roaDuration,
                weightedLines = group.weightedLines,
                areSubstanceHeightsIndependent = areSubstanceHeightsIndependent
            )
        }
        val overallMaxHeight = groupDrawables.maxOfOrNull { it.nonNormalisedHeight } ?: 1f
        groupDrawables.forEach { it.normaliseHeight(overallMaxHeight) }
        this.groupDrawables = groupDrawables
        val maxWidthOfGroups: Float = groupDrawables.maxOfOrNull {
            it.endOfLineRelativeToStartInSeconds
        } ?: 0f
        val latestRating = ratingTimes.maxOrNull()
        val maxWidthRating: Float = if (latestRating != null) {
            Duration.between(startTime, latestRating).seconds.toFloat()
        } else {
            0f
        }
        val latestNote = noteTimes.maxOrNull()
        val maxWidthNote: Float = if (latestNote != null) {
            Duration.between(startTime, latestNote).seconds.toFloat()
        } else {
            0f
        }
        val maxCandidates = listOf(
            maxWidthOfGroups,
            maxWidthRating,
            maxWidthNote,
            2.hours.inWholeSeconds.toFloat()
        )
        widthInSeconds = maxCandidates.max() + 10.minutes.inWholeSeconds.toFloat()
        axisDrawable = AxisDrawable(startTime, widthInSeconds)
    }

    fun getQuantityDetails(time: Instant): List<TimelineQuantityDetail> {
        val secondsFromStart = Duration.between(startTime, time).seconds.toFloat()
        return roaGroups.zip(groupDrawables).mapNotNull { (group, drawable) ->
            val fullTimelines = drawable.fullTimelines ?: return@mapNotNull null
            val height = fullTimelines.heightAt(secondsFromStart)
            val maxHeight = fullTimelines.nonNormalisedHeight
            if (maxHeight <= 0f) return@mapNotNull null
            TimelineQuantityDetail(
                substanceName = group.substanceName,
                route = group.route,
                color = group.color,
                fractionOfPeak = height / maxHeight,
                doseEquivalent = group.referenceDose?.let { it * height },
                doseUnits = group.doseUnits
            )
        }
    }
}
