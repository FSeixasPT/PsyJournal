package com.isaakhanimann.journal.ui.tabs.stats

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DoseEntry(
    val dose: Double?,
    val units: String?,
    val isEstimate: Boolean,
    val standardDeviation: Double?
)

fun IngestionWithCompanionAndCustomUnit.toDoseEntry() = DoseEntry(
    dose = pureDose,
    units = originalUnit,
    isEstimate = isEstimate,
    standardDeviation = pureDoseStandardDeviation
)

data class DoseTotals(val totals: List<TotalDose>, val unknownCount: Int)

// one total per unit, the most used unit first, so a single unknown dose doesn't hide the known ones
fun totalDoses(entries: List<DoseEntry>): DoseTotals {
    val (known, unknown) = entries.partition { it.dose != null && it.units != null }
    val totals = known.groupBy { it.units!! }.entries
        .sortedByDescending { it.value.size }
        .map { (units, sameUnits) ->
            val sumStandardDeviations = sameUnits.sumOf { it.standardDeviation ?: 0.0 }
            TotalDose(
                dose = sameUnits.sumOf { it.dose!! },
                units = units,
                isEstimate = sameUnits.any { it.isEstimate },
                estimatedDoseStandardDeviation = sumStandardDeviations.takeIf { it > 0 }
            )
        }
    return DoseTotals(totals = totals, unknownCount = unknown.size)
}

fun lastTakenText(lastTaken: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(lastTaken, today)
    val months = ChronoUnit.MONTHS.between(lastTaken, today)
    val years = ChronoUnit.YEARS.between(lastTaken, today)
    val ago = when {
        days <= 0 -> return "last taken today"
        days == 1L -> return "last taken yesterday"
        days < 60 -> "$days days"
        years < 2 -> "$months months"
        else -> "$years years"
    }
    return "last taken $ago ago"
}

enum class ChartMetric(val displayText: String) {
    EXPERIENCES("Experiences"),
    INGESTIONS("Ingestions"),
    TOTAL_DOSE("Total dose")
}

data class ChartIngestion(
    val experienceId: Int,
    val substanceName: String,
    val dose: Double?,
    val units: String?
)

fun IngestionWithCompanionAndCustomUnit.toChartIngestion() = ChartIngestion(
    experienceId = ingestion.experienceId,
    substanceName = ingestion.substanceName,
    dose = pureDose,
    units = originalUnit
)

// doses of different substances can't be stacked, so the dose chart shows one substance in one unit
data class DoseChoice(val substanceName: String, val units: String)

fun doseChoice(ingestions: List<ChartIngestion>, substanceName: String): DoseChoice? {
    val units = ingestions.filter { it.substanceName == substanceName && it.dose != null && it.units != null }
        .groupingBy { it.units!! }.eachCount()
        .maxByOrNull { it.value }?.key ?: return null
    return DoseChoice(substanceName = substanceName, units = units)
}

// the order of the list below the chart: most experiences first, the most recent first on a tie
fun doseSubstances(ingestionsNewestFirst: List<ChartIngestion>): List<String> =
    ingestionsNewestFirst.groupBy { it.substanceName }.entries
        .sortedByDescending { (_, ingestions) -> ingestions.map { it.experienceId }.distinct().size }
        .map { it.key }
        .filter { doseChoice(ingestionsNewestFirst, it) != null }

private fun ChartIngestion.isCharted(choice: DoseChoice) =
    substanceName == choice.substanceName && dose != null && units == choice.units

fun dosesLeftOut(ingestions: List<ChartIngestion>, choice: DoseChoice): Int =
    ingestions.count { it.substanceName == choice.substanceName && !it.isCharted(choice) }

fun barSegments(
    ingestions: List<ChartIngestion>,
    metric: ChartMetric,
    colors: Map<String, AdaptiveColor>,
    doseChoice: DoseChoice? = null
): List<ColorCount> {
    val values: Map<String, Double> = when (metric) {
        ChartMetric.EXPERIENCES -> ingestions.groupBy { it.substanceName }
            .mapValues { (_, sameSubstance) -> sameSubstance.map { it.experienceId }.distinct().size.toDouble() }
        ChartMetric.INGESTIONS -> ingestions.groupingBy { it.substanceName }.eachCount()
            .mapValues { it.value.toDouble() }
        ChartMetric.TOTAL_DOSE -> {
            if (doseChoice == null) return emptyList()
            val charted = ingestions.filter { it.isCharted(doseChoice) }
            if (charted.isEmpty()) emptyMap() else mapOf(doseChoice.substanceName to charted.sumOf { it.dose!! })
        }
    }
    return values.mapNotNull { (name, value) ->
        val color = colors[name] ?: return@mapNotNull null
        ColorCount(color = color, value = value)
    }.sortedByDescending { it.value }
}
