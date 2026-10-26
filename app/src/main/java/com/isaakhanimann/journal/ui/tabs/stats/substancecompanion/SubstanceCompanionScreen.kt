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

package com.isaakhanimann.journal.ui.tabs.stats.substancecompanion

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.classes.Tolerance
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.CardWithTitle
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.ToleranceSection
import com.isaakhanimann.journal.ui.tabs.stats.BarChart
import com.isaakhanimann.journal.ui.tabs.stats.ColorCount
import com.isaakhanimann.journal.ui.tabs.stats.TimePickerOption
import com.isaakhanimann.journal.ui.theme.JournalTheme
import com.isaakhanimann.journal.ui.theme.horizontalPadding
import com.isaakhanimann.journal.ui.utils.getDateWithWeekdayText
import com.isaakhanimann.journal.ui.utils.getShortTimeText

@Composable
fun SubstanceCompanionScreen(
    viewModel: SubstanceCompanionViewModel = hiltViewModel()
) {
    val companion = viewModel.thisCompanionFlow.collectAsState().value
    if (companion == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {}
    } else {
        SubstanceCompanionScreen(
            substanceCompanion = companion,
            ingestionBursts = viewModel.ingestionBurstsFlow.collectAsState().value,
            tolerance = viewModel.tolerance,
            crossTolerances = viewModel.crossTolerances,
            consumerName = viewModel.consumerName,
            doseChart = viewModel.doseChartFlow.collectAsState().value,
            onTapDoseChartOption = viewModel::onTapDoseChartOption
        )
    }
}

@Preview
@Composable
fun SubstanceCompanionPreview(@PreviewParameter(SubstanceCompanionScreenPreviewProvider::class) pair: Pair<SubstanceCompanion, List<IngestionsBurst>>) {
    JournalTheme {
        SubstanceCompanionScreen(
            substanceCompanion = pair.first,
            ingestionBursts = pair.second,
            tolerance = Tolerance(
                full = "with prolonged use",
                half = "two weeks",
                zero = "1 month"
            ),
            crossTolerances = listOf(
                "dopamine",
                "stimulant"
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstanceCompanionScreen(
    substanceCompanion: SubstanceCompanion,
    ingestionBursts: List<IngestionsBurst>,
    tolerance: Tolerance?,
    crossTolerances: List<String>,
    consumerName: String? = null,
    doseChart: DoseChartModel? = null,
    onTapDoseChartOption: (TimePickerOption) -> Unit = {}
) {
    Scaffold(
        topBar = {
            val title = if (consumerName == null) {
                substanceCompanion.substanceName
            } else {
                "${substanceCompanion.substanceName} ($consumerName)"
            }
            TopAppBar(title = { Text(title) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                if (doseChart != null) {
                    DoseChartCard(
                        doseChart = doseChart,
                        color = substanceCompanion.color,
                        onTapOption = onTapDoseChartOption
                    )
                }
                if (tolerance != null || crossTolerances.isNotEmpty()) {
                    CardWithTitle(title = "Tolerance", modifier = Modifier.fillMaxWidth()) {
                        ToleranceSection(
                            tolerance = tolerance,
                            crossTolerances = crossTolerances
                        )
                    }
                }
                Text(text = "Now")
            }
            items(ingestionBursts) { burst ->
                TimeArrowUp(timeText = burst.timeUntil)
                ElevatedCard(modifier = Modifier.padding(vertical = 5.dp)) {
                    Column(modifier = Modifier.padding(horizontal = horizontalPadding)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Text(
                                text = burst.experience.title,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = burst.experience.sortDate.getDateWithWeekdayText(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        HorizontalDivider()
                        burst.ingestions.forEachIndexed { index, ingestion ->
                            IngestionRowOnSubstanceCompanionScreen(ingestionAndCustomUnit = ingestion)
                            if (index < burst.ingestions.size - 1) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseChartCard(
    doseChart: DoseChartModel,
    color: AdaptiveColor,
    onTapOption: (TimePickerOption) -> Unit
) {
    CardWithTitle(title = "Total dose", innerPaddingHorizontal = 0.dp, modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = doseChart.option.tabIndex,
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = Color.Transparent
        ) {
            TimePickerOption.entries.forEach { option ->
                Tab(
                    text = { Text(option.displayText) },
                    selected = doseChart.option == option,
                    onClick = { onTapOption(option) }
                )
            }
        }
        val chart = doseChart.chart
        if (chart == null) {
            Text(
                text = "No doses with a known amount since ${doseChart.startDateText}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
            )
        } else {
            Text(
                text = "In ${chart.units} since ${doseChart.startDateText}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp)
            )
            if (chart.dosesLeftOut > 0) {
                val doses = if (chart.dosesLeftOut == 1) "dose" else "doses"
                Text(
                    text = "${chart.dosesLeftOut} $doses with an unknown amount or another unit left out",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            BarChart(
                buckets = chart.buckets.map { dose -> if (dose > 0) listOf(ColorCount(color = color, value = dose)) else emptyList() },
                startDateText = doseChart.startDateText,
                isCount = false
            )
        }
    }
}

@Composable
fun IngestionRowOnSubstanceCompanionScreen(ingestionAndCustomUnit: IngestionsBurst.IngestionAndCustomUnit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val text = buildAnnotatedString {
            append(ingestionAndCustomUnit.doseDescription)
            if (ingestionAndCustomUnit.customUnit != null) {
                append(" " + ingestionAndCustomUnit.customUnit.name)
            }
            withStyle(style = SpanStyle(color = if (isSystemInDarkTheme()) Color.Gray else Color.LightGray )) {
                val routeText =
                    ingestionAndCustomUnit.ingestion.administrationRoute.displayText.lowercase()
                if (ingestionAndCustomUnit.customUnit == null) {
                    append(" $routeText")
                }
                ingestionAndCustomUnit.customUnitDose?.calculatedDoseDescription?.let {
                    append(" = $it $routeText")
                }
            }
        }
        Text(text = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        val dateString = ingestionAndCustomUnit.ingestion.time.getShortTimeText()
        Text(text = dateString)
    }
}