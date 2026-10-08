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
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullDurations
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AllTimelinesModel(
    private val dataForLines: List<DataForOneEffectLine>,
    val dataForRatings: List<DataForOneRating>,
    val timedNotes: List<DataForOneTimedNote>,
    private val areSubstanceHeightsIndependent: Boolean,
    // combine the routes of a substance into one line if their doses are comparable
    private val areRoutesMerged: Boolean = false,
    // set if this model is a preview of a planned ingestion that is not logged
    private val plannedLine: DataForOneEffectLine? = null,
) {
    val plannedIngestionTime: Instant? get() = plannedLine?.startTime

    // the logged lines without the planned one
    val realLines: List<DataForOneEffectLine> =
        if (plannedLine == null) dataForLines else dataForLines - plannedLine
    val startTime: Instant
    val widthInSeconds: Float
    val groupDrawables: List<GroupDrawable>
    val axisDrawable: AxisDrawable

    data class RouteInfo(
        val route: AdministrationRoute,
        val referenceDose: Double?,
        val doseUnits: String?
    )

    data class RoaGroup(
        val substanceName: String,
        // in the order they were first taken
        val routes: List<RouteInfo>,
        val color: AdaptiveColor,
        // duration of the first route, used for partial timelines which are never merged
        val roaDuration: RoaDuration?,
        val weightedLines: List<WeightedLine>,
        val isDoseEquivalentKnown: Boolean
    ) {
        val isMerged: Boolean get() = routes.size > 1

        fun contains(substanceName: String, route: AdministrationRoute): Boolean =
            this.substanceName == substanceName && routes.any { it.route == route }
    }

    private val roaGroups: List<RoaGroup>

    init {
        val ratingTimes = dataForRatings.map { it.time }
        val ingestionTimes = dataForLines.map { it.startTime }
        val noteTimes = timedNotes.map { it.time }
        val allStartTimeCandidates = ratingTimes + ingestionTimes + noteTimes
        startTime =
            allStartTimeCandidates.reduce { acc, date -> if (acc.isBefore(date)) acc else date }
        val roaGroups = dataForLines.groupBy { it.substanceName }
            .flatMap { (substanceName, linesPerSubstance) ->
                val routes = linesPerSubstance.groupBy { it.route }.entries
                    .sortedBy { (_, lines) -> lines.minOf { it.startTime } }
                val mergeableRoutes = if (areRoutesMerged) {
                    routes.filter { (_, lines) ->
                        lines.all { it.isReferenceDoseFromDoseRanges && it.roaDuration?.toFullDurations() != null }
                    }
                } else {
                    emptyList()
                }
                val routesPerGroup = if (mergeableRoutes.size >= 2) {
                    listOf(mergeableRoutes) + routes.filter { it !in mergeableRoutes }.map { listOf(it) }
                } else {
                    routes.map { listOf(it) }
                }
                routesPerGroup.map { createGroup(substanceName, it) }
            }
        this.roaGroups = roaGroups
        val plannedWeightedLine = plannedLine?.let {
            WeightedLine(
                startTime = it.startTime,
                endTime = it.endTime,
                horizontalWeight = it.horizontalWeight,
                height = it.height,
                roaDuration = it.roaDuration
            )
        }
        val groupDrawables = roaGroups.map { group ->
            val isPlannedGroup = plannedLine != null && group.contains(plannedLine.substanceName, plannedLine.route)
            GroupDrawable(
                startTimeGraph = startTime,
                color = group.color,
                roaDuration = group.roaDuration,
                weightedLines = group.weightedLines,
                areSubstanceHeightsIndependent = areSubstanceHeightsIndependent,
                previewBaselineLines = if (isPlannedGroup && plannedWeightedLine != null) {
                    group.weightedLines - plannedWeightedLine
                } else {
                    null
                }
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

    val colors: List<AdaptiveColor> get() = roaGroups.map { it.color }.distinct()

    // the planned ingestion of a pin, or null if the pin no longer applies to this timeline
    fun resolvePinnedPlan(pin: PinnedRedose, now: Instant): PlannedIngestion? {
        val isLoggedSincePinning = realLines.any {
            it.substanceName == pin.substanceName && it.route == pin.route && it.startTime > pin.pinnedAt
        }
        if (isLoggedSincePinning) return null
        val endTime = startTime.plusSeconds(widthInSeconds.toLong())
        if (now > endTime) return null
        return PlannedIngestion(
            substanceName = pin.substanceName,
            route = pin.route,
            dose = pin.dose,
            units = pin.units
        )
    }

    fun withPlannedIngestion(line: DataForOneEffectLine): AllTimelinesModel {
        return AllTimelinesModel(
            dataForLines = realLines + line,
            dataForRatings = dataForRatings,
            timedNotes = timedNotes,
            areSubstanceHeightsIndependent = areSubstanceHeightsIndependent,
            areRoutesMerged = areRoutesMerged,
            plannedLine = line
        )
    }

    val plannedIngestionColor: AdaptiveColor? get() = plannedLine?.color

    // highest combined intensity of the planned substance from the planned ingestion on
    val plannedPeak: TimelinePeak? by lazy {
        val option = plannedLine ?: return@lazy null
        val index = roaGroups.indexOfFirst { it.contains(option.substanceName, option.route) }
        val group = roaGroups.getOrNull(index) ?: return@lazy null
        // a line with an assumed dose (unknown or in other units) can't be expressed as a dose
        if (!group.isDoseEquivalentKnown) return@lazy null
        val referenceDose = group.routes.first { it.route == option.route }.referenceDose ?: return@lazy null
        val fullTimelines = groupDrawables[index].fullTimelines ?: return@lazy null
        val highestPoint = fullTimelines.highestPointFrom(Duration.between(startTime, option.startTime).seconds.toFloat())
        TimelinePeak(
            time = startTime.plusSeconds(highestPoint.x.toLong()),
            doseEquivalent = highestPoint.y * referenceDose,
            units = option.doseUnits ?: return@lazy null,
            route = if (group.isMerged) option.route else null
        )
    }

    // intensity of the planned substance's combined curve at the given time relative to its highest point
    fun plannedFractionOfPeakAt(time: Instant): Float? {
        val option = plannedLine ?: return null
        val index = roaGroups.indexOfFirst { it.contains(option.substanceName, option.route) }
        val fullTimelines = groupDrawables.getOrNull(index)?.fullTimelines ?: return null
        val maxHeight = fullTimelines.nonNormalisedHeight
        if (maxHeight <= 0f) return null
        return fullTimelines.heightAt(Duration.between(startTime, time).seconds.toFloat()) / maxHeight
    }

    // position of the planned ingestion on its curve: seconds from start and height from 0 to 1
    val plannedIngestionPoint: Pair<Float, Float>? by lazy {
        val option = plannedLine ?: return@lazy null
        val index = roaGroups.indexOfFirst { it.contains(option.substanceName, option.route) }
        val fullTimelines = groupDrawables.getOrNull(index)?.fullTimelines ?: return@lazy null
        val seconds = Duration.between(startTime, option.startTime).seconds.toFloat()
        Pair(seconds, fullTimelines.heightAt(seconds) / fullTimelines.referenceHeight)
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
                color = group.color,
                fractionOfPeak = height / maxHeight,
                doseEquivalents = if (group.isDoseEquivalentKnown) {
                    group.routes.map {
                        RouteDoseEquivalent(
                            route = it.route,
                            dose = it.referenceDose!! * height,
                            units = it.doseUnits!!
                        )
                    }
                } else {
                    emptyList()
                }
            )
        }
    }

    private companion object {
        fun createGroup(
            substanceName: String,
            routes: List<Map.Entry<AdministrationRoute, List<DataForOneEffectLine>>>
        ): RoaGroup {
            val routeInfos = routes.map { (route, linesOfRoute) ->
                val isKnown = linesOfRoute.all { it.referenceDose != null } &&
                        linesOfRoute.map { it.doseUnits }.distinct().size == 1
                RouteInfo(
                    route = route,
                    referenceDose = if (isKnown) linesOfRoute.first().referenceDose else null,
                    doseUnits = if (isKnown) linesOfRoute.first().doseUnits else null
                )
            }
            val lines = routes.flatMap { it.value }
            return RoaGroup(
                substanceName = substanceName,
                routes = routeInfos,
                color = lines.first().color,
                roaDuration = lines.first().roaDuration,
                weightedLines = lines.map {
                    WeightedLine(
                        startTime = it.startTime,
                        endTime = it.endTime,
                        horizontalWeight = it.horizontalWeight,
                        height = it.height,
                        roaDuration = it.roaDuration
                    )
                },
                isDoseEquivalentKnown = routeInfos.all { it.referenceDose != null }
            )
        }
    }
}
