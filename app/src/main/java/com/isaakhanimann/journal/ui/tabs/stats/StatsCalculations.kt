package com.isaakhanimann.journal.ui.tabs.stats

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import java.time.Instant
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
    INGESTIONS("Ingestions")
}

data class ChartIngestion(
    val experienceId: Int,
    val substanceName: String
)

fun IngestionWithCompanionAndCustomUnit.toChartIngestion() = ChartIngestion(
    experienceId = ingestion.experienceId,
    substanceName = ingestion.substanceName
)

fun barSegments(
    ingestions: List<ChartIngestion>,
    metric: ChartMetric,
    colors: Map<String, AdaptiveColor>
): List<ColorCount> {
    val values: Map<String, Double> = when (metric) {
        ChartMetric.EXPERIENCES -> ingestions.groupBy { it.substanceName }
            .mapValues { (_, sameSubstance) -> sameSubstance.map { it.experienceId }.distinct().size.toDouble() }
        ChartMetric.INGESTIONS -> ingestions.groupingBy { it.substanceName }.eachCount()
            .mapValues { it.value.toDouble() }
    }
    return values.mapNotNull { (name, value) ->
        val color = colors[name] ?: return@mapNotNull null
        ColorCount(color = color, value = value)
    }.sortedByDescending { it.value }
}

data class TimedDose(val time: Instant, val dose: Double?, val units: String?)

data class DoseChart(
    val units: String,
    // oldest first, like the main chart
    val buckets: List<Double>,
    val dosesLeftOut: Int
)

// one substance's doses summed per bucket, in the unit used most in the range, since other units can't be added to it
fun doseChart(doses: List<TimedDose>, option: TimePickerOption, end: Instant): DoseChart? {
    val start = end.minus(option.allBucketSizes)
    val inRange = doses.filter { it.time > start && it.time <= end }
    val units = inRange.filter { it.dose != null && it.units != null }
        .groupingBy { it.units!! }.eachCount()
        .maxByOrNull { it.value }?.key ?: return null
    val charted = inRange.filter { it.dose != null && it.units == units }
    var bucketEnd = end
    val buckets = (0 until option.bucketCount).map {
        val bucketStart = bucketEnd.minus(option.oneBucketSize)
        val sum = charted.filter { it.time > bucketStart && it.time <= bucketEnd }.sumOf { it.dose!! }
        bucketEnd = bucketStart
        sum
    }.reversed()
    return DoseChart(units = units, buckets = buckets, dosesLeftOut = inRange.size - charted.size)
}
