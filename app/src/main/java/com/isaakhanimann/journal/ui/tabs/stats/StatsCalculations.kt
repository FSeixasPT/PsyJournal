package com.isaakhanimann.journal.ui.tabs.stats

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

// the median, so a long break or a binge doesn't decide what's usual
fun usualGapInDays(useDates: List<LocalDate>): Int? {
    val days = useDates.distinct().sorted()
    if (days.size < 2) return null
    val gaps = days.zipWithNext { earlier, later -> ChronoUnit.DAYS.between(earlier, later) }.sorted()
    val middle = gaps.size / 2
    val median = if (gaps.size % 2 == 1) gaps[middle].toDouble() else (gaps[middle - 1] + gaps[middle]) / 2.0
    return Math.round(median).toInt()
}

fun usualGapText(days: Int): String = when {
    days == 1 -> "usually every day"
    days < 60 -> "usually every $days days"
    else -> "usually every ${Math.round(days / DAYS_PER_MONTH)} months"
}

private const val DAYS_PER_MONTH = 30.44
