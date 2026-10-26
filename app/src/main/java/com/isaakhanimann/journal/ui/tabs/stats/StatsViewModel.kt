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

package com.isaakhanimann.journal.ui.tabs.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    experienceRepo: ExperienceRepository
) : ViewModel() {

    private val _optionFlow = MutableStateFlow(TimePickerOption.WEEKS_26)
    private val optionFlow = _optionFlow.asStateFlow()

    private val consumerFlow = MutableStateFlow<String?>(null)

    fun onTapOption(timePickerOption: TimePickerOption) {
        viewModelScope.launch {
            _optionFlow.emit(timePickerOption)
        }
    }

    fun onChangeConsumer(consumerName: String?) {
        viewModelScope.launch {
            consumerFlow.emit(consumerName)
        }
    }

    private val startDateFlow = _optionFlow.map {
        return@map Instant.now().getEndOfDay().minus(it.allBucketSizes)
    }

    private val startDateTextFlow = startDateFlow.map {
        val dateTime = LocalDateTime.ofInstant(it, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")
        return@map dateTime.format(formatter)
    }

    private val allExperiencesSortedFlow: Flow<List<ExperienceWithIngestionsAndCompanions>> =
        experienceRepo.getSortedExperiencesWithIngestionsAndCustomUnitsFlow()

    private val areThereAnyIngestionsFlow =
        allExperiencesSortedFlow.combine(consumerFlow) { experiences, consumerName ->
            experiences.any { experience ->
                experience.ingestionsWithCompanionAndCustomUnit.any { it.ingestion.consumerName == consumerName }
            }
        }

    private val relevantExperiencesSortedFlow: Flow<List<ExperienceWithIngestionsAndCompanions>> =
        allExperiencesSortedFlow.combine(startDateFlow) { experiences, startDate ->
            return@combine experiences.dropWhile { it.sortInstant > Instant.now().getEndOfDay() }
                .takeWhile { it.sortInstant > startDate }
        }

    private val companionFlow = experienceRepo.getAllSubstanceCompanionsFlow()

    private val chartMetricFlow = MutableStateFlow(ChartMetric.EXPERIENCES)

    // null until the user picks one, then the substance with the most experiences is charted
    private val doseSubstanceFlow = MutableStateFlow<String?>(null)

    fun onChangeChartMetric(metric: ChartMetric) {
        chartMetricFlow.value = metric
    }

    fun onChooseDoseSubstance(substanceName: String) {
        doseSubstanceFlow.value = substanceName
    }

    private val chartIngestionBucketsFlow: Flow<List<List<ChartIngestion>>> =
        relevantExperiencesSortedFlow.combine(optionFlow) { sortedExperiences, option ->
            var remainingExperiences = sortedExperiences
            val buckets = mutableListOf<List<ExperienceWithIngestionsAndCompanions>>()
            var startInstant = Instant.now().getEndOfDay()
            for (i in 0 until option.bucketCount) {
                startInstant = startInstant.minus(option.oneBucketSize)
                val experiencesForBucket =
                    remainingExperiences.takeWhile { it.sortInstant > startInstant }
                buckets.add(experiencesForBucket)
                val numExperiences = experiencesForBucket.size
                remainingExperiences =
                    remainingExperiences.takeLast(remainingExperiences.size - numExperiences)
            }
            return@combine buckets
        }.combine(consumerFlow) { buckets, consumerName ->
            buckets.map { experiencesInBucket ->
                experiencesInBucket.flatMap { it.ingestionsWithCompanionAndCustomUnit }
                    .filter { it.ingestion.consumerName == consumerName }
                    .map { it.toChartIngestion() }
            }.reversed()
        }

    private val chartFlow: Flow<ChartModel> = combine(
        chartIngestionBucketsFlow,
        companionFlow,
        chartMetricFlow,
        doseSubstanceFlow
    ) { buckets, companions, metric, doseSubstance ->
        val allIngestions = buckets.reversed().flatten()
        val doseSubstances = doseSubstances(allIngestions)
        val choice = (doseSubstance?.takeIf { it in doseSubstances } ?: doseSubstances.firstOrNull())
            ?.let { doseChoice(allIngestions, it) }
        val colors = companions.associate { it.substanceName to it.color }
        ChartModel(
            metric = metric,
            buckets = buckets.map { barSegments(it, metric, colors, choice) },
            doseChoice = choice,
            doseSubstances = doseSubstances,
            dosesLeftOut = choice?.let { dosesLeftOut(allIngestions, it) } ?: 0
        )
    }

    // when each substance was taken, over the whole journal, for how long ago and how often
    private val datesTakenFlow: Flow<Map<String, List<LocalDate>>> =
        allExperiencesSortedFlow.combine(consumerFlow) { experiences, consumerName ->
            val now = Instant.now()
            experiences.flatMap { it.ingestionsWithCompanionAndCustomUnit }
                .map { it.ingestion }
                .filter { it.consumerName == consumerName && !it.time.isAfter(now) }
                .groupBy({ it.substanceName }, { it.time.atZone(ZoneId.systemDefault()).toLocalDate() })
        }

    private val statsFlowItem: Flow<List<StatItem>> = combine(
        relevantExperiencesSortedFlow,
        consumerFlow,
        companionFlow,
        datesTakenFlow
    ) { relevantExperiencesSorted, consumerName, companions, datesTaken ->
        val allIngestions =
            relevantExperiencesSorted.flatMap { experience -> experience.ingestionsWithCompanionAndCustomUnit.filter { it.ingestion.consumerName == consumerName } }
        val experienceNamesMap =
            relevantExperiencesSorted.map { experience ->
                experience.ingestionsWithCompanionAndCustomUnit.filter { it.ingestion.consumerName == consumerName }.map { it.ingestion.substanceName }
                    .toSet()
            }.flatten().groupBy { it }
        val map = allIngestions.groupBy { it.ingestion.substanceName }
        return@combine map.values.mapNotNull { groupedIngestions ->
            val name = groupedIngestions.firstOrNull()?.ingestion?.substanceName ?: return@mapNotNull null
            val oneCompanion =
                companions.firstOrNull { it.substanceName == name } ?: return@mapNotNull null
            val experienceCounts = experienceNamesMap[name]?.size ?: 0
            StatItem(
                substanceName = name,
                color = oneCompanion.color,
                experienceCount = experienceCounts,
                ingestionCount = groupedIngestions.size,
                routeCounts = getRouteCounts(groupedIngestions.map { it.ingestion }),
                doseTotals = totalDoses(groupedIngestions.map { it.toDoseEntry() }),
                lastTaken = datesTaken[name]?.maxOrNull()
            )
        }.sortedByDescending { it.experienceCount }
    }

    private fun getRouteCounts(groupedIngestions: List<Ingestion>): List<RouteCount> {
        val routeMap = groupedIngestions.groupBy { it.administrationRoute }
        return routeMap.values.mapNotNull {
            val route = it.firstOrNull()?.administrationRoute ?: return@mapNotNull null
            RouteCount(administrationRoute = route, count = it.size)
        }
    }

    val statsModelFlow: StateFlow<StatsModel> =
        optionFlow.combine(startDateTextFlow) { option, startDateText ->
            return@combine Pair(first = option, second = startDateText)
        }.combine(statsFlowItem) { pair, substanceStats ->
            return@combine Pair(first = pair, second = substanceStats)
        }.combine(areThereAnyIngestionsFlow) { pair, areThere ->
            return@combine Pair(first = pair, second = areThere)
        }.combine(consumerFlow) { pair, consumerName ->
            return@combine Pair(first = pair, second = consumerName)
        }.combine(chartFlow) { pair, chart ->
            return@combine StatsModel(
                areThereAnyIngestions = pair.first.second,
                selectedOption = pair.first.first.first.first,
                startDateText = pair.first.first.first.second,
                statItems = pair.first.first.second,
                chartBuckets = chart.buckets,
                consumerName = pair.second,
                chartMetric = chart.metric,
                doseChoice = chart.doseChoice,
                doseSubstances = chart.doseSubstances,
                dosesLeftOut = chart.dosesLeftOut
            )
        }.stateIn(
            initialValue = StatsModel(
                selectedOption = TimePickerOption.WEEKS_26,
                areThereAnyIngestions = false,
                startDateText = "",
                statItems = emptyList(),
                chartBuckets = emptyList(),
                consumerName = null
            ),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    val sortedConsumerNamesFlow =
        experienceRepo.getSortedIngestions(limit = 200).map { ingestions ->
            return@map ingestions.mapNotNull { it.consumerName }.distinct()
        }.stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )
}

data class StatsModel(
    val selectedOption: TimePickerOption,
    val areThereAnyIngestions: Boolean,
    val startDateText: String,
    val statItems: List<StatItem>,
    val chartBuckets: List<List<ColorCount>>,
    val consumerName: String?,
    val chartMetric: ChartMetric = ChartMetric.EXPERIENCES,
    val doseChoice: DoseChoice? = null,
    val doseSubstances: List<String> = emptyList(),
    val dosesLeftOut: Int = 0
)

data class ChartModel(
    val metric: ChartMetric,
    val buckets: List<List<ColorCount>>,
    val doseChoice: DoseChoice?,
    val doseSubstances: List<String>,
    val dosesLeftOut: Int
)

data class ColorCount(
    val color: AdaptiveColor,
    val value: Double
)

data class StatItem(
    val substanceName: String,
    val color: AdaptiveColor,
    val experienceCount: Int,
    val ingestionCount: Int,
    val routeCounts: List<RouteCount>,
    val doseTotals: DoseTotals,
    val lastTaken: LocalDate? = null,
)

data class RouteCount(
    val administrationRoute: AdministrationRoute,
    val count: Int
)

data class TotalDose(
    val dose: Double,
    val units: String,
    val isEstimate: Boolean,
    val estimatedDoseStandardDeviation: Double?
)

enum class TimePickerOption {
    DAYS_7 {
        override val displayText = "7D"
        override val longDisplayText = "Last week"
        override val tabIndex = 0
        override val bucketCount = 7
        override val oneBucketSize: Period = Period.ofDays(1)
        override val allBucketSizes: Period = Period.ofDays(7)
    },
    DAYS_30 {
        override val displayText = "30D"
        override val longDisplayText = "Last month"
        override val tabIndex = 1
        override val bucketCount = 30
        override val oneBucketSize: Period = Period.ofDays(1)
        override val allBucketSizes: Period = Period.ofDays(30)
    },
    WEEKS_26 {
        override val displayText = "26W"
        override val longDisplayText = "Half year"
        override val tabIndex = 2
        override val bucketCount = 26
        override val oneBucketSize: Period =
            Period.ofDays(7) // the max time unit that can be used for subtraction is days
        override val allBucketSizes: Period = Period.ofDays(7 * bucketCount)
    },
    MONTHS_12 {
        override val displayText = "12M"
        override val longDisplayText = "Last year"
        override val tabIndex = 3
        override val bucketCount = 12
        override val oneBucketSize: Period = Period.ofDays(30)
        override val allBucketSizes: Period = Period.ofDays(30 * bucketCount)
    },
    YEARS_10 {
        override val displayText = "10Y"
        override val longDisplayText = "10 years"
        override val tabIndex = 4
        override val bucketCount = 10
        override val oneBucketSize: Period = Period.ofDays(365)
        override val allBucketSizes: Period = Period.ofDays(365 * bucketCount)
    };

    abstract val displayText: String
    abstract val longDisplayText: String
    abstract val tabIndex: Int
    abstract val bucketCount: Int
    abstract val oneBucketSize: Period
    abstract val allBucketSizes: Period
}

fun Instant.getEndOfDay(): Instant {
    return this.atOffset(ZoneOffset.UTC)
        .with(LocalTime.of(23,59,59, this.nano))
        .toInstant()
}