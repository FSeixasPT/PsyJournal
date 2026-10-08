# Merge Routes on the Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show one effect line per substance, combining all of its routes, with each route keeping its own timing and doses made comparable through the per-route dose ranges. This is controlled by a setting that is on by default.

**Architecture:** Each `WeightedLine` (one ingestion on the chart) carries its own route's timing, so the existing curve summation in `FullTimelines` adds routes together without further changes. `AllTimelinesModel` decides which routes of a substance share a group (all routes that are "mergeable"), and keeps per-route reference doses so that intensity details, redose chips and the peak can be expressed per route. A new preference flows from `UserPreferences` through the view models into `AllTimelinesModel`.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX DataStore, JUnit 4 (JVM unit tests), Gradle 8.9.

**Spec:** `docs/superpowers/specs/2026-10-08-merge-routes-design.md`

## Global Constraints

- Run Gradle with JDK 21. From Git Bash: `export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"`, then `./gradlew ...` in the repo root. Android Studio's bundled Java 25 fails with Gradle 8.9.
- Three unit tests already fail on `main` and are unrelated: `TestDates.dateDifferences`, `TestParse.testExtractSubstancesString` and `TestParse.noCrash`. "All tests pass" in this plan means every other test passes.
- New source files start with the PsyJournal GPL header. Copy it from `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TimelineQuantityDetail.kt`, lines 1-17. Do not change the headers of existing files.
- Bioavailability data is not used.
- The substance info page (`SubstanceViewModel`) never merges routes.
- Preference default: merging is **on** (`true`).
- Settings text, verbatim: switch label `Merge routes of same substance`. Explanation: `When enabled, ingestions of the same substance through different routes (e.g. insufflated and oral ketamine) are shown as one combined line. Each route keeps its own onset and duration, and doses are made comparable using each route's common dose range, so the line shows the combined effect rather than the combined amount. Routes without dose ranges in the database are always shown separately.`
- Display of a merged line: `● Ketamine · 74% of peak` followed by a second line `≈ 39 mg insufflated / ≈ 148 mg oral equivalent`. A single-route line is unchanged: `● MDMA · 85% of peak · ≈ 85 mg equivalent`.
- Peak line for a merged group: `Peak after redose: ≈ 52 mg insufflated equivalent at 00:45`. If the group is not merged, the route is not named.
- Test commands run from the repo root `D:/Documentos/Projetos/Psychonautwiki` in Git Bash.

## Review Focus

1. **Mixed units within one route of a merged group** (mg and µg logged for oral). The line should still merge and show the %, and should drop the equivalents. The test is in Task 2 (`mixedUnitsInOneRouteDropsEquivalentsButStillMerges`).
2. **An ingestion taken over a time range in one route, combined with a point ingestion in another route.** Each should use its own route's timing. The test is in Task 1 (`timeRangeLineUsesItsOwnDurations`).
3. **One route of a merged substance has an unknown dose.** That route gets no redose chip, the other route still gets one, and previewing it still merges. The test is in Task 3 (`unknownDoseRouteHasNoChipButOtherRouteStillMerges`).
4. **One route has only partial timing data** (e.g. sublingual ketamine has no peak/offset). That route stays a separate line, and the other two routes merge. The test is in Task 2 (`routeWithPartialDurationsStaysSeparate`).
5. **Turning the setting off restores exactly the per-route lines of today.** The test is in Task 2 (`mergingOffKeepsOneLinePerRoute`), and the 17 existing timeline tests guard the numbers.

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/WeightedLine.kt` | one ingestion as input to a curve | add `roaDuration` |
| `.../timeline/drawables/timelines/FullTimelines.kt` | curve maths | per-line timings, `FullDurations`, new factory |
| `.../timeline/drawables/GroupDrawable.kt` | one drawn line (group) | use the new factory; per-line timing for ranges |
| `.../experience/components/DataForOneEffectLine.kt` | chart input from the view model | add `isReferenceDoseFromDoseRanges` |
| `.../timeline/AllTimelinesModel.kt` | grouping, details, redose, peak | merged grouping; per-route data |
| `.../timeline/TimelineQuantityDetail.kt` | detail value object | `doseEquivalents` list |
| `.../timeline/TimelineQuantityDetails.kt` | detail UI | two-line text for merged lines |
| `.../timeline/TimelinePeak.kt` | peak value object | add `route` |
| `.../timeline/RedosePreviewRow.kt` | redose chips and peak line UI | route name in peak line |
| `.../experience/ExperienceViewModel.kt` | experience chart data | set the flag; pass the setting |
| `.../timeline/screen/TimelineScreenViewModel.kt` | full-screen chart data | pass the setting |
| `.../settings/combinations/UserPreferences.kt` | persisted settings | new key and flow |
| `.../settings/SettingsViewModel.kt` / `SettingsScreen.kt` | settings UI | new switch |
| `app/src/test/.../timeline/drawables/timelines/FullTimelinesTest.kt` | curve maths tests | new |
| `app/src/test/.../timeline/RouteMergingTest.kt` | grouping, details, redose tests | new |

In this table, `...` stands for `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal` (or `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal` for tests), except for the settings files, which are in `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/`.

---

### Task 1: Per-line timings in the curve maths

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/WeightedLine.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/drawables/timelines/FullTimelines.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/drawables/GroupDrawable.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/AllTimelinesModel.kt` (the `WeightedLine(...)` construction only)
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/drawables/timelines/FullTimelinesTest.kt`

**Interfaces:**
- Produces:
  - `data class WeightedLine(startTime: Instant, endTime: Instant?, horizontalWeight: Float, height: Float, roaDuration: RoaDuration?)`
  - `data class FullDurations(onset: FullDurationRange, comeup: FullDurationRange, peak: FullDurationRange, offset: FullDurationRange)`
  - `fun RoaDuration.toFullDurations(): FullDurations?`
  - `data class FullTimelines(weightedLines: List<WeightedLine>, startTimeGraph: Instant)`, keeping `heightAt(Float): Float`, `highestPointFrom(Float): Point`, `nonNormalisedHeight` and `referenceHeight`
  - `fun List<WeightedLine>.toFullTimelines(startTimeGraph: Instant): FullTimelines?`
- Removes: `fun RoaDuration.toFullTimelines(weightedLines, startTimeGraph)`.

- [ ] **Step 1: Write the failing test**

Create `FullTimelinesTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines

import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.WeightedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class FullTimelinesTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")

    private fun duration(onset: Float, comeup: Float, peak: Float, offset: Float) = RoaDuration(
        onset = DurationRange(onset, onset, DurationUnits.MINUTES),
        comeup = DurationRange(comeup, comeup, DurationUnits.MINUTES),
        peak = DurationRange(peak, peak, DurationUnits.MINUTES),
        offset = DurationRange(offset, offset, DurationUnits.MINUTES),
        total = null,
        afterglow = null
    )

    // fast: rises 10-20 min, peak 20-40 min, falls 40-60 min
    private val fast = duration(onset = 10f, comeup = 10f, peak = 20f, offset = 20f)

    // slow: rises 30-60 min, peak 60-120 min, falls 120-180 min
    private val slow = duration(onset = 30f, comeup = 30f, peak = 60f, offset = 60f)

    private fun line(minutes: Long, roaDuration: RoaDuration?, endMinutes: Long? = null) = WeightedLine(
        startTime = start.plus(minutes, ChronoUnit.MINUTES),
        endTime = endMinutes?.let { start.plus(it, ChronoUnit.MINUTES) },
        horizontalWeight = 0.5f,
        height = 1f,
        roaDuration = roaDuration
    )

    private fun seconds(minutes: Int) = minutes * 60f

    @Test
    fun linesWithDifferentDurationsAddUpWithTheirOwnTiming() {
        val timelines = listOf(line(0, fast), line(0, slow)).toFullTimelines(start)!!
        // 25 min: fast at peak (1), slow still in onset (0)
        assertEquals(1f, timelines.heightAt(seconds(25)), 0.001f)
        // 45 min: fast falling 5 of 20 min (0.75), slow rising 15 of 30 min (0.5)
        assertEquals(1.25f, timelines.heightAt(seconds(45)), 0.001f)
        // 90 min: fast gone, slow at peak
        assertEquals(1f, timelines.heightAt(seconds(90)), 0.001f)
    }

    @Test
    fun timeRangeLineUsesItsOwnDurations() {
        // a fast route sipped over 0-60 min and a slow point ingestion at 0
        val timelines = listOf(line(0, fast, endMinutes = 60), line(0, slow)).toFullTimelines(start)!!
        // after 2 h the fast range is fully worn off (ends at 60 + 10 + 10 + 20 + 20 = 120 min) and slow is at its peak end
        assertEquals(1f, timelines.heightAt(seconds(120)), 0.001f)
        // the fast range contributes while the slow line is still in its onset
        assertEquals(true, timelines.heightAt(seconds(25)) > 0f)
    }

    @Test
    fun noFullTimelinesWhenALineHasIncompleteDurations() {
        val incomplete = slow.copy(peak = null)
        assertNull(listOf(line(0, fast), line(0, incomplete)).toFullTimelines(start))
    }
}
```

- [ ] **Step 2: Run the test to verify that it fails**

Run: `export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"; ./gradlew :app:testDebugUnitTest --tests '*FullTimelinesTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: compilation errors such as `No parameter with name 'roaDuration' found` and `Unresolved reference 'toFullTimelines'`.

- [ ] **Step 3: Add the timing field to `WeightedLine`**

Replace the body of `WeightedLine.kt` after the package line with:

```kotlin
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import java.time.Instant

data class WeightedLine(
    val startTime: Instant,
    val endTime: Instant?,
    val horizontalWeight: Float,
    val height: Float,
    // timing of this ingestion's route, so ingestions of different routes can share one curve
    val roaDuration: RoaDuration?
)
```

- [ ] **Step 4: Make `FullTimelines` read the timings from each line**

In `FullTimelines.kt`:

(a) Replace the class header and properties:

```kotlin
data class FullTimelines(
    val onset: FullDurationRange,
    val comeup: FullDurationRange,
    val peak: FullDurationRange,
    val offset: FullDurationRange,
    val weightedLines: List<WeightedLine>,
    val startTimeGraph: Instant,
) : TimelineDrawable {

    data class WeightedLineRelativeToFirst(
        val startTimeRelativeToGroupInSeconds: Float,
        val horizontalWeight: Float,
        val height: Float
    )
```

with:

```kotlin
data class FullTimelines(
    val weightedLines: List<WeightedLine>,
    val startTimeGraph: Instant,
) : TimelineDrawable {

    data class WeightedLineRelativeToFirst(
        val startTimeRelativeToGroupInSeconds: Float,
        val horizontalWeight: Float,
        val height: Float,
        val durations: FullDurations
    )
```

(b) Directly above `private val finalPoints: List<FinalPoint>`, add:

```kotlin
    // created through List<WeightedLine>.toFullTimelines which guarantees full durations for every line
    private val durationsPerLine: List<FullDurations> =
        weightedLines.map { requireNotNull(it.roaDuration?.toFullDurations()) }
```

(c) In `init`, replace the `timeRangeLineSegments` and `weightedRelatives` blocks:

```kotlin
        val timeRangeLineSegments = weightedLines.flatMap {
            return@flatMap getLineSegments(weightedLine = it)
        }
        val weightedRelatives = weightedLines.filter { it.endTime == null }.map {
            WeightedLineRelativeToFirst(
                startTimeRelativeToGroupInSeconds = Duration.between(
                    startTimeGraph,
                    it.startTime
                ).seconds.toFloat(),
                horizontalWeight = it.horizontalWeight,
                height = it.height
            )
        }
```

with:

```kotlin
        val timeRangeLineSegments = weightedLines.zip(durationsPerLine).flatMap { (line, durations) ->
            return@flatMap getLineSegments(weightedLine = line, durations = durations)
        }
        val weightedRelatives = weightedLines.zip(durationsPerLine)
            .filter { (line, _) -> line.endTime == null }
            .map { (line, durations) ->
                WeightedLineRelativeToFirst(
                    startTimeRelativeToGroupInSeconds = Duration.between(
                        startTimeGraph,
                        line.startTime
                    ).seconds.toFloat(),
                    horizontalWeight = line.horizontalWeight,
                    height = line.height,
                    durations = durations
                )
            }
```

(d) In the `lineSegments` block inside `init`, replace every use of `onset.`, `comeup.`, `peak.` and `offset.` with `relative.durations.onset.`, `relative.durations.comeup.`, `relative.durations.peak.` and `relative.durations.offset.`. These are the four lines that compute `onsetEndX`, `comeupEndX`, `peakEndX` and `offsetEndX`.

(e) Replace the fallback in the `endOfLineRelativeToStartInSeconds` assignment:

```kotlin
        this.endOfLineRelativeToStartInSeconds = finalPoints.maxOfOrNull { it.x } ?: (onset.maxInSeconds + comeup.maxInSeconds + peak.maxInSeconds + offset.maxInSeconds)
```

with:

```kotlin
        this.endOfLineRelativeToStartInSeconds = finalPoints.maxOfOrNull { it.x }
            ?: durationsPerLine.maxOfOrNull { it.onset.maxInSeconds + it.comeup.maxInSeconds + it.peak.maxInSeconds + it.offset.maxInSeconds }
            ?: 0f
```

(f) Replace the signature and the first duration lines of `private fun getLineSegments(weightedLine: WeightedLine): List<LineSegment>`. The current code is:

```kotlin
    private fun getLineSegments(weightedLine: WeightedLine): List<LineSegment> {
```

It also contains these lines:

```kotlin
        val onsetInSeconds = onset.interpolateAtValueInSeconds(0.5f)
        val comeupInSeconds = comeup.interpolateAtValueInSeconds(0.5f)

        var horizontalWeightToUse = 0.5f
        if (rangeInSeconds < peak.minInSeconds) {
            // if the range is short enough we use the same duration as for point ingestion
            horizontalWeightToUse = weightedLine.horizontalWeight
        }
        val peakInSeconds = peak.interpolateAtValueInSeconds(horizontalWeightToUse)
        val offsetInSeconds = offset.interpolateAtValueInSeconds(horizontalWeightToUse)
```

Change them to:

```kotlin
    private fun getLineSegments(weightedLine: WeightedLine, durations: FullDurations): List<LineSegment> {
```

and

```kotlin
        val onsetInSeconds = durations.onset.interpolateAtValueInSeconds(0.5f)
        val comeupInSeconds = durations.comeup.interpolateAtValueInSeconds(0.5f)

        var horizontalWeightToUse = 0.5f
        if (rangeInSeconds < durations.peak.minInSeconds) {
            // if the range is short enough we use the same duration as for point ingestion
            horizontalWeightToUse = weightedLine.horizontalWeight
        }
        val peakInSeconds = durations.peak.interpolateAtValueInSeconds(horizontalWeightToUse)
        val offsetInSeconds = durations.offset.interpolateAtValueInSeconds(horizontalWeightToUse)
```

(g) Replace the factory at the end of the file (`fun RoaDuration.toFullTimelines(...)` up to the end) with:

```kotlin
data class FullDurations(
    val onset: FullDurationRange,
    val comeup: FullDurationRange,
    val peak: FullDurationRange,
    val offset: FullDurationRange,
)

fun RoaDuration.toFullDurations(): FullDurations? {
    val fullOnset = onset?.toFullDurationRange() ?: return null
    val fullComeup = comeup?.toFullDurationRange() ?: return null
    val fullPeak = peak?.toFullDurationRange() ?: return null
    val fullOffset = offset?.toFullDurationRange() ?: return null
    return FullDurations(onset = fullOnset, comeup = fullComeup, peak = fullPeak, offset = fullOffset)
}

fun List<WeightedLine>.toFullTimelines(startTimeGraph: Instant): FullTimelines? {
    return if (isNotEmpty() && all { it.roaDuration?.toFullDurations() != null }) {
        FullTimelines(weightedLines = this, startTimeGraph = startTimeGraph)
    } else {
        null
    }
}
```

Then remove the `import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration` line only if the compiler reports it as unused. `toFullDurations` still uses it, so it normally stays.

- [ ] **Step 5: Use the new factory in `GroupDrawable` and pass the timings in `AllTimelinesModel`**

In `GroupDrawable.kt`, replace:

```kotlin
            val fulls = roaDuration?.toFullTimelines(
                weightedLines = weightedLines,
                startTimeGraph = startTimeGraph,
            )
```

with:

```kotlin
            val fulls = weightedLines.toFullTimelines(startTimeGraph = startTimeGraph)
```

In the same file, replace `fullTimelineDurations = roaDuration?.toFullTimelineDurations(),` with `fullTimelineDurations = it.roaDuration?.toFullTimelineDurations(),`.

In `AllTimelinesModel.kt`, inside the `WeightedLine(` construction, add `roaDuration = it.roaDuration` after `height = it.height`:

```kotlin
                            WeightedLine(
                                startTime = it.startTime,
                                endTime = it.endTime,
                                horizontalWeight = it.horizontalWeight,
                                height = it.height,
                                roaDuration = it.roaDuration
                            )
```

- [ ] **Step 6: Run the new tests and all timeline tests to verify that they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*FullTimelinesTest*' --tests '*TimelineQuantityDetailsTest*' --tests '*RedosePreviewTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`, with 3 + 7 + 10 tests passing. Check this with `grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml`.

- [ ] **Step 7: Verify that the app compiles**

Run: `./gradlew :app:compileDebugKotlin --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src
git commit -m "Give each timeline line its own route timing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Merged grouping and per-route intensity details

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/components/DataForOneEffectLine.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/AllTimelinesModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TimelineQuantityDetail.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TimelineQuantityDetails.kt`
- Modify: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TimelineQuantityDetailsTest.kt`
- Modify: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/RedosePreviewTest.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/RouteMergingTest.kt`

**Interfaces:**
- Consumes (from Task 1): `WeightedLine(..., roaDuration)` and `RoaDuration.toFullDurations()`.
- Produces:
  - `DataForOneEffectLine.isReferenceDoseFromDoseRanges: Boolean = false`
  - `AllTimelinesModel(..., areRoutesMerged: Boolean = false, plannedOption, plannedIngestionTime)`. The new parameter comes right after `areSubstanceHeightsIndependent`.
  - `data class RouteDoseEquivalent(route: AdministrationRoute, dose: Double, units: String)`
  - `data class TimelineQuantityDetail(substanceName: String, color: AdaptiveColor, fractionOfPeak: Float, doseEquivalents: List<RouteDoseEquivalent>)`. The old `route`, `doseEquivalent` and `doseUnits` fields are removed.
  - `AllTimelinesModel.RouteInfo(route, referenceDose: Double?, doseUnits: String?)`
  - `AllTimelinesModel.RoaGroup(substanceName, routes: List<RouteInfo>, color, roaDuration, weightedLines, isDoseEquivalentKnown)`, with `isMerged: Boolean` and `contains(substanceName, route): Boolean`

- [ ] **Step 1: Write the failing tests**

Create `RouteMergingTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.AdministrationRoute.INSUFFLATED
import com.isaakhanimann.journal.data.substances.AdministrationRoute.ORAL
import com.isaakhanimann.journal.data.substances.AdministrationRoute.SUBLINGUAL
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class RouteMergingTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")

    private fun duration(onset: Float, comeup: Float, peak: Float, offset: Float) = RoaDuration(
        onset = DurationRange(onset, onset, DurationUnits.MINUTES),
        comeup = DurationRange(comeup, comeup, DurationUnits.MINUTES),
        peak = DurationRange(peak, peak, DurationUnits.MINUTES),
        offset = DurationRange(offset, offset, DurationUnits.MINUTES),
        total = null,
        afterglow = null
    )

    // insufflated: peak 20-40 min after ingestion; oral: peak 60-120 min after ingestion
    private val insufflatedDuration = duration(onset = 10f, comeup = 10f, peak = 20f, offset = 20f)
    private val oralDuration = duration(onset = 30f, comeup = 30f, peak = 60f, offset = 60f)

    private val referenceDoses = mapOf(INSUFFLATED to 52.5, ORAL to 200.0, SUBLINGUAL to 100.0)

    private fun line(
        minutes: Long,
        route: AdministrationRoute,
        dose: Double?,
        units: String = "mg",
        roaDuration: RoaDuration? = if (route == ORAL) oralDuration else insufflatedDuration,
        isReferenceDoseFromDoseRanges: Boolean = true,
    ): DataForOneEffectLine {
        val referenceDose = referenceDoses.getValue(route)
        return DataForOneEffectLine(
            substanceName = "Ketamine",
            route = route,
            roaDuration = roaDuration,
            height = if (dose != null) (dose / referenceDose).toFloat() else 1f,
            horizontalWeight = 0.5f,
            color = AdaptiveColor.PINK,
            startTime = start.plus(minutes, ChronoUnit.MINUTES),
            endTime = null,
            referenceDose = if (dose != null) referenceDose else null,
            doseUnits = if (dose != null) units else null,
            isReferenceDoseFromDoseRanges = isReferenceDoseFromDoseRanges
        )
    }

    private fun model(areRoutesMerged: Boolean, vararg lines: DataForOneEffectLine) = AllTimelinesModel(
        dataForLines = lines.toList(),
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false,
        areRoutesMerged = areRoutesMerged
    )

    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)

    @Test
    fun mergingOnCombinesRoutesIntoOneLine() {
        val details = model(true, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0)).getQuantityDetails(at(30))
        assertEquals(1, details.size)
    }

    @Test
    fun mergingOffKeepsOneLinePerRoute() {
        val details = model(false, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0)).getQuantityDetails(at(30))
        assertEquals(2, details.size)
    }

    @Test
    fun mergedLineListsTheEquivalentOfEachRouteInOrderTaken() {
        // at 30 min the insufflated 52.5 mg is at its peak and the oral dose is not taken yet: one reference dose
        val detail = model(true, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0)).getQuantityDetails(at(30)).single()
        assertEquals(listOf(INSUFFLATED, ORAL), detail.doseEquivalents.map { it.route })
        assertEquals(52.5, detail.doseEquivalents[0].dose, 0.01)
        assertEquals(200.0, detail.doseEquivalents[1].dose, 0.01)
        assertEquals("mg", detail.doseEquivalents[1].units)
    }

    @Test
    fun routesAddUpToTheCombinedEffect() {
        // oral 100 mg (height 0.5) at 0 min peaks 60-120 min; insufflated 52.5 mg (height 1) at 60 min peaks 80-100 min
        // at 90 min both are at their peak: 0.5 + 1 = 1.5
        val details = model(true, line(60, INSUFFLATED, 52.5), line(0, ORAL, 100.0)).getQuantityDetails(at(90)).single()
        assertEquals(1.5 * 52.5, details.doseEquivalents.first { it.route == INSUFFLATED }.dose, 0.01)
    }

    @Test
    fun routeWithoutDoseRangesStaysSeparate() {
        val details = model(
            true,
            line(0, INSUFFLATED, 52.5),
            line(300, ORAL, 200.0, isReferenceDoseFromDoseRanges = false)
        ).getQuantityDetails(at(30))
        assertEquals(2, details.size)
    }

    @Test
    fun routeWithPartialDurationsStaysSeparate() {
        val partial = oralDuration.copy(peak = null, offset = null)
        val model = model(
            true,
            line(0, INSUFFLATED, 52.5),
            line(10, ORAL, 200.0),
            line(20, SUBLINGUAL, 100.0, roaDuration = partial)
        )
        // insufflated and oral merge into one detail; sublingual has no full curve so it has no detail at all
        val detail = model.getQuantityDetails(at(30)).single()
        assertEquals(listOf(INSUFFLATED, ORAL), detail.doseEquivalents.map { it.route })
        assertEquals(2, model.groupDrawables.size)
    }

    @Test
    fun unknownDoseStillMergesButDropsEquivalents() {
        val detail = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, null)).getQuantityDetails(at(30)).single()
        assertTrue(detail.doseEquivalents.isEmpty())
        assertTrue(detail.fractionOfPeak > 0f)
    }

    @Test
    fun mixedUnitsInOneRouteDropsEquivalentsButStillMerges() {
        val detail = model(
            true,
            line(0, INSUFFLATED, 52.5),
            line(10, ORAL, 200.0, units = "mg"),
            line(20, ORAL, 200.0, units = "µg")
        ).getQuantityDetails(at(30)).single()
        assertTrue(detail.doseEquivalents.isEmpty())
    }
}
```

The test `routesAddUpToTheCombinedEffect` works like this. The model's start time is the earliest ingestion (oral at 0), and insufflated is taken at 60 min. At 90 min:
- the insufflated dose is 30 min in, i.e. at its peak: height 1;
- the oral 100 mg (height 0.5) is 90 min in, i.e. at its peak (60-120 min): height 0.5.

The sum is 1.5, so the insufflated equivalent is 1.5 × 52.5.

Update `TimelineQuantityDetailsTest.kt` to the new detail API, without changing any expected numbers:
- `assertEquals(100.0, detail.doseEquivalent!!, 0.01)` becomes `assertEquals(100.0, detail.doseEquivalents.single().dose, 0.01)`. Apply the same change to every `detail.doseEquivalent!!` (lines with 50.0 and 100.0).
- `assertEquals("mg", detail.doseUnits)` becomes `assertEquals("mg", detail.doseEquivalents.single().units)`.
- In `unknownDoseShowsPercentageWithoutDoseEquivalent`, replace `assertNull(detail.doseEquivalent)` and `assertNull(detail.doseUnits)` with `assertTrue(detail.doseEquivalents.isEmpty())`.
- In `mixedUnitsShowPercentageWithoutDoseEquivalent`, replace `assertNull(detail.doseEquivalent)` with `assertTrue(detail.doseEquivalents.isEmpty())`.
- Remove the then-unused `import org.junit.Assert.assertNull`.

Update `RedosePreviewTest.kt` in the same way:
- `assertEquals(50.0, detail.doseEquivalent!!, 0.01)` becomes `assertEquals(50.0, detail.doseEquivalents.single().dose, 0.01)`.
- `assertEquals(0.0, real.getQuantityDetails(at(180)).single().doseEquivalent!!, 0.01)` becomes `assertEquals(0.0, real.getQuantityDetails(at(180)).single().doseEquivalents.single().dose, 0.01)`.

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RouteMergingTest*' --console=plain 2>&1 | grep -E "^e: |BUILD" | head`
Expected: compilation errors such as `No parameter with name 'isReferenceDoseFromDoseRanges' found`, `No parameter with name 'areRoutesMerged' found` and `Unresolved reference 'doseEquivalents'`.

- [ ] **Step 3: Add the flag to `DataForOneEffectLine`**

Add a parameter after `val doseUnits: String? = null`, so that the data class's last parameters read:

```kotlin
    val referenceDose: Double? = null,
    val doseUnits: String? = null,
    // true if referenceDose comes from the dose ranges of this route; only then are routes comparable.
    // Describes the route, so it is also set when this ingestion's dose is unknown.
    val isReferenceDoseFromDoseRanges: Boolean = false
)
```

- [ ] **Step 4: Change `TimelineQuantityDetail`**

Replace the data class in `TimelineQuantityDetail.kt` (keep the header and imports):

```kotlin
data class TimelineQuantityDetail(
    val substanceName: String,
    val color: AdaptiveColor,
    // intensity at the selected time relative to the highest point of the curve, from 0 to 1
    val fractionOfPeak: Float,
    // for each route of the line: dose that would cause this intensity at its peak.
    // Empty if not all doses are known in consistent units.
    val doseEquivalents: List<RouteDoseEquivalent>
)

data class RouteDoseEquivalent(
    val route: AdministrationRoute,
    val dose: Double,
    val units: String
)
```

- [ ] **Step 5: Implement merged grouping in `AllTimelinesModel`**

(a) Add the parameter after `areSubstanceHeightsIndependent`:

```kotlin
    private val areSubstanceHeightsIndependent: Boolean,
    // combine the routes of a substance into one line if their doses are comparable
    private val areRoutesMerged: Boolean = false,
```

(b) Replace the `RoaGroup` data class with:

```kotlin
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
```

(c) Replace the whole `val roaGroups = dataForLines.groupBy { it.substanceName } ... }` expression (from `val roaGroups =` up to the line before `this.roaGroups = roaGroups`) with:

```kotlin
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
```

(d) Add the following inside the class, after `getQuantityDetails`:

```kotlin
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
```

(e) Replace the body of `getQuantityDetails` with:

```kotlin
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
```

(f) Keep the redose and peak code compiling (Task 3 rewrites it properly). In `redoseOptions`, `plannedPeak` and `plannedIngestionPoint`:
- replace `group.route` with `group.routes.first().route`;
- replace `group.referenceDose` with `group.routes.first().referenceDose`;
- replace `group.doseUnits` with `group.routes.first().doseUnits`;
- replace each `roaGroups.indexOfFirst { it.substanceName == option.substanceName && it.route == option.route }` with `roaGroups.indexOfFirst { it.contains(option.substanceName, option.route) }`.

In `withPlannedIngestion`, add `areRoutesMerged = areRoutesMerged,` after `areSubstanceHeightsIndependent = areSubstanceHeightsIndependent,`.

(g) Add `import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullDurations` to the imports.

- [ ] **Step 6: Update the details text**

In `TimelineQuantityDetails.kt`, replace the `Text(text = detail.toText(), ...)` inside the `Row`, and the `toText` function, so that merged lines get a second line.

Replace:

```kotlin
                Text(
                    text = detail.toText(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
```

with:

```kotlin
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = detail.toText(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (detail.doseEquivalents.size > 1) {
                        Text(
                            text = detail.doseEquivalents.joinToString(separator = " / ") {
                                "≈ ${it.dose.toReadableString()} ${it.units} ${it.route.displayText.lowercase()}"
                            } + " equivalent",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
```

Replace the `toText` function with:

```kotlin
private fun TimelineQuantityDetail.toText(): String {
    val percent = (fractionOfPeak * 100).roundToInt()
    val singleEquivalent = doseEquivalents.singleOrNull()
    val dose = if (singleEquivalent != null) {
        " · ≈ ${singleEquivalent.dose.toReadableString()} ${singleEquivalent.units} equivalent"
    } else {
        ""
    }
    return "$substanceName · $percent% of peak$dose"
}
```

`Column` is already imported in this file.

- [ ] **Step 7: Run all timeline tests to verify that they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RouteMergingTest*' --tests '*FullTimelinesTest*' --tests '*TimelineQuantityDetailsTest*' --tests '*RedosePreviewTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`, with 8 + 3 + 7 + 10 tests passing.

- [ ] **Step 8: Verify that the app compiles**

Run: `./gradlew :app:compileDebugKotlin --console=plain 2>&1 | grep -E "^e: |BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src
git commit -m "Merge comparable routes of a substance into one timeline line

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Redose chips per route, and the peak with merged lines

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/AllTimelinesModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TimelinePeak.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/RedosePreviewRow.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/RouteMergingTest.kt`

**Interfaces:**
- Consumes (from Task 2): `RoaGroup.routes`, `RoaGroup.isMerged`, `RoaGroup.contains`, `RouteInfo` and `areRoutesMerged`.
- Produces:
  - `data class TimelinePeak(time: Instant, doseEquivalent: Double, units: String, route: AdministrationRoute? = null)`. `route` is set only when the planned route's group is merged.
  - `redoseOptions` gives one option per substance and route.

- [ ] **Step 1: Write the failing tests**

Append these tests inside `RouteMergingTest`:

```kotlin
    @Test
    fun mergedSubstanceHasOneRedoseChipPerRoute() {
        val options = model(true, line(0, INSUFFLATED, 40.0), line(10, ORAL, 150.0)).redoseOptions
        assertEquals(listOf(INSUFFLATED, ORAL), options.map { it.route })
        assertEquals(40.0, options[0].dose, 0.01)
        assertEquals(150.0, options[1].dose, 0.01)
    }

    @Test
    fun plannedDoseOfOneRouteMergesIntoTheSubstanceLine() {
        val real = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, 200.0))
        val oral = real.redoseOptions.first { it.route == ORAL }
        val preview = real.withPlannedIngestion(oral, dose = 200.0, time = at(20))
        assertEquals(1, preview.getQuantityDetails(at(30)).size)
    }

    @Test
    fun peakOfMergedLineIsExpressedInThePlannedRouteAndNamesIt() {
        // only insufflated taken so far, as 52.5 mg at 0; plan 52.5 mg insufflated at 0 as well: peak 2 x 52.5 = 105 mg
        val real = model(true, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0))
        val insufflated = real.redoseOptions.first { it.route == INSUFFLATED }
        val peak = real.withPlannedIngestion(insufflated, dose = 52.5, time = at(0)).plannedPeak!!
        assertEquals(105.0, peak.doseEquivalent, 0.01)
        assertEquals("mg", peak.units)
        assertEquals(INSUFFLATED, peak.route)
    }

    @Test
    fun peakOfUnmergedLineDoesNotNameTheRoute() {
        val real = model(false, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0))
        val insufflated = real.redoseOptions.first { it.route == INSUFFLATED }
        val peak = real.withPlannedIngestion(insufflated, dose = 52.5, time = at(0)).plannedPeak!!
        assertEquals(null, peak.route)
    }

    @Test
    fun unknownDoseRouteHasNoChipButOtherRouteStillMerges() {
        val real = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, null))
        assertEquals(listOf(INSUFFLATED), real.redoseOptions.map { it.route })
        val preview = real.withPlannedIngestion(real.redoseOptions.single(), dose = 52.5, time = at(20))
        assertEquals(1, preview.getQuantityDetails(at(30)).size)
    }
```

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RouteMergingTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD" | head`
Expected: a compilation error `Unresolved reference 'route'` on `peak.route`.

- [ ] **Step 3: Add `route` to `TimelinePeak`**

Replace the data class in `TimelinePeak.kt`, and add `import com.isaakhanimann.journal.data.substances.AdministrationRoute`:

```kotlin
data class TimelinePeak(
    val time: Instant,
    val doseEquivalent: Double,
    val units: String,
    // route the dose equivalent is expressed in, only set if the line combines several routes
    val route: AdministrationRoute? = null
)
```

- [ ] **Step 4: Compute redose options per route, and the peak with merged groups**

In `AllTimelinesModel.kt`, replace the whole `val redoseOptions ... by lazy { ... }` with:

```kotlin
    val redoseOptions: List<RedoseOption> by lazy {
        dataForLines.groupBy { Pair(it.substanceName, it.route) }.mapNotNull { (key, lines) ->
            if (lines.any { it.referenceDose == null }) return@mapNotNull null
            if (lines.map { it.doseUnits }.distinct().size != 1) return@mapNotNull null
            if (lines.any { it.roaDuration?.toFullDurations() == null }) return@mapNotNull null
            val lastLine = lines.maxBy { it.startTime }
            RedoseOption(
                substanceName = key.first,
                route = key.second,
                dose = lastLine.height * lastLine.referenceDose!!,
                units = lastLine.doseUnits!!,
                color = lastLine.color
            )
        }
    }
```

Replace the whole `val plannedPeak ... by lazy { ... }` with:

```kotlin
    val plannedPeak: TimelinePeak? by lazy {
        val option = plannedOption ?: return@lazy null
        val time = plannedIngestionTime ?: return@lazy null
        val index = roaGroups.indexOfFirst { it.contains(option.substanceName, option.route) }
        val group = roaGroups.getOrNull(index) ?: return@lazy null
        val referenceDose = group.routes.first { it.route == option.route }.referenceDose ?: return@lazy null
        val fullTimelines = groupDrawables[index].fullTimelines ?: return@lazy null
        val highestPoint = fullTimelines.highestPointFrom(Duration.between(startTime, time).seconds.toFloat())
        TimelinePeak(
            time = startTime.plusSeconds(highestPoint.x.toLong()),
            doseEquivalent = highestPoint.y * referenceDose,
            units = option.units,
            route = if (group.isMerged) option.route else null
        )
    }
```

In `plannedIngestionPoint`, make sure the index lookup is `roaGroups.indexOfFirst { it.contains(option.substanceName, option.route) }` (already changed in Task 2, Step 5f).

- [ ] **Step 5: Name the route in the peak line**

In `RedosePreviewRow.kt`, replace the peak text expression:

```kotlin
            text = peak?.let {
                "Peak after redose: ≈ ${it.doseEquivalent.toReadableString()} ${it.units} equivalent at ${it.time.getShortTimeText()}"
            } ?: "",
```

with:

```kotlin
            text = peak?.let {
                val route = it.route?.let { route -> " ${route.displayText.lowercase()}" } ?: ""
                "Peak after redose: ≈ ${it.doseEquivalent.toReadableString()} ${it.units}$route equivalent at ${it.time.getShortTimeText()}"
            } ?: "",
```

- [ ] **Step 6: Run all timeline tests to verify that they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RouteMergingTest*' --tests '*FullTimelinesTest*' --tests '*TimelineQuantityDetailsTest*' --tests '*RedosePreviewTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`, with 13 + 3 + 7 + 10 tests passing.

- [ ] **Step 7: Commit**

```bash
git add app/src
git commit -m "Keep redose chips per route and name the route of a merged peak

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The setting, wiring and Settings screen

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/combinations/UserPreferences.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceViewModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/screen/TimelineScreenViewModel.kt`

**Interfaces:**
- Consumes (from Task 2): `AllTimelinesModel(..., areRoutesMerged = ...)` and `DataForOneEffectLine.isReferenceDoseFromDoseRanges`.
- Produces: `UserPreferences.areRoutesMergedFlow: Flow<Boolean>` (default `true`) and `UserPreferences.saveAreRoutesMerged(Boolean)`.

This task is wiring and UI. Its behaviour is covered by the unit tests of Tasks 2 and 3 plus the manual check in Step 7.

- [ ] **Step 1: Add the preference**

In `UserPreferences.kt`, add a key below `KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT`:

```kotlin
        val KEY_ARE_ROUTES_MERGED = booleanPreferencesKey("KEY_ARE_ROUTES_MERGED")
```

Below `areSubstanceHeightsIndependentFlow`, add:

```kotlin
    suspend fun saveAreRoutesMerged(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ARE_ROUTES_MERGED] = value
        }
    }

    val areRoutesMergedFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_ARE_ROUTES_MERGED] ?: true
        }
```

- [ ] **Step 2: Set the flag in `ExperienceViewModel.getDataForEffectTimelines`**

In the `DataForOneEffectLine(` construction, after `doseUnits = if (isDoseKnown) ingestionWithCompanion.originalUnit else null,`, add:

```kotlin
                    isReferenceDoseFromDoseRanges = roaDose?.averageCommonDose != null,
```

- [ ] **Step 3: Pass the setting in `ExperienceViewModel`**

(a) Below `areSubstanceHeightsIndependentFlow`, add:

```kotlin
    private val areRoutesMergedFlow =
        userPreferences.areRoutesMergedFlow.stateIn(
            initialValue = true,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    // both chart settings in one flow, because the timeline flow already combines six flows
    private val chartSettingsFlow =
        areSubstanceHeightsIndependentFlow.combine(areRoutesMergedFlow) { areSubstanceHeightsIndependent, areRoutesMerged ->
            Pair(areSubstanceHeightsIndependent, areRoutesMerged)
        }
```

(b) In `consumersWithIngestionsFlow`, replace `areSubstanceHeightsIndependentFlow` with `chartSettingsFlow` in the `combine(...)` arguments. Change the lambda parameters to `{ ingestions, isTimelineHidden, (areSubstanceHeightsIndependent, areRoutesMerged) ->`, and add `areRoutesMerged = areRoutesMerged` to its `AllTimelinesModel(` call.

(c) Do the same in `timelineDisplayOptionFlow`: replace `areSubstanceHeightsIndependentFlow` with `chartSettingsFlow`. Change the lambda's last parameter to `(areSubstanceHeightsIndependent, areRoutesMerged)`, and add `areRoutesMerged = areRoutesMerged` to its `AllTimelinesModel(` call.

`combine` and `stateIn` are already imported in this file. Check that `kotlinx.coroutines.flow.combine` is imported for the extension-style `a.combine(b)`, and add it if the compiler reports it missing.

- [ ] **Step 4: Pass the setting in `TimelineScreenViewModel`**

Below `areSubstanceHeightsIndependentFlow`, add:

```kotlin
    private val areRoutesMergedFlow =
        userPreferences.areRoutesMergedFlow.stateIn(
            initialValue = true,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )
```

In `timelineDisplayOptionFlow`, add `areRoutesMergedFlow` as the fifth argument of `combine(`. Change the lambda to `{ dataForEffectLines, ratings, timedNotesSorted, areSubstanceHeightsIndependent, areRoutesMerged ->`, and add `areRoutesMerged = areRoutesMerged` to the `AllTimelinesModel(` call.

- [ ] **Step 5: Add the switch to the Settings screen**

In `SettingsViewModel.kt`, below `saveAreSubstanceHeightsIndependent`, add:

```kotlin
    fun saveAreRoutesMerged(value: Boolean) = viewModelScope.launch {
        userPreferences.saveAreRoutesMerged(value)
    }
```

Below `areSubstanceHeightsIndependentFlow`, add:

```kotlin
    val areRoutesMergedFlow = userPreferences.areRoutesMergedFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )
```

In `SettingsScreen.kt`, make these changes:
- In the preview call (where `areSubstanceHeightsIndependent = false,` and `saveAreSubstanceHeightsIndependent = {},` are passed), add `areRoutesMerged = true,` and `saveAreRoutesMerged = {},`.
- In the real call, add `areRoutesMerged = viewModel.areRoutesMergedFlow.collectAsState().value,` and `saveAreRoutesMerged = viewModel::saveAreRoutesMerged,`.
- In the inner `SettingsScreen` signature, after `saveAreSubstanceHeightsIndependent: (Boolean) -> Unit,`, add `areRoutesMerged: Boolean,` and `saveAreRoutesMerged: (Boolean) -> Unit,`.
- Directly after the closing `}` of the `Row` that contains the "Independent substance heights" `Switch` (the `Switch(checked = areSubstanceHeightsIndependent, ...)` followed by `}`), insert:

```kotlin
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    var showBottomSheet by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clickable {
                                showBottomSheet = true
                            }
                            .padding(end = ButtonDefaults.IconSpacing)
                    ) {
                        Text(text = "Merge routes of same substance")
                        if (showBottomSheet) {
                            ModalBottomSheet(
                                onDismissRequest = {
                                    showBottomSheet = false
                                },
                                sheetState = sheetState
                            ) {
                                Text(
                                    text = "When enabled, ingestions of the same substance through different routes (e.g. insufflated and oral ketamine) are shown as one combined line. Each route keeps its own onset and duration, and doses are made comparable using each route's common dose range, so the line shows the combined effect rather than the combined amount. Routes without dose ranges in the database are always shown separately.",
                                    modifier = Modifier
                                        .padding(horizontal = horizontalPadding)
                                        .padding(bottom = 15.dp)
                                        .verticalScroll(state = rememberScrollState())
                                )
                            }
                        }
                        Icon(Icons.Outlined.Info, contentDescription = "Show more info")
                    }
                    Switch(
                        checked = areRoutesMerged,
                        onCheckedChange = saveAreRoutesMerged
                    )
                }
```

All of these composables and modifiers are already imported in `SettingsScreen.kt`, because the "independent heights" row uses them.

- [ ] **Step 6: Build, run all tests and install on the emulator**

Run:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --continue --console=plain 2>&1 | grep -E "^e: |FAILED|tests completed|BUILD"
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected:
- no `e:` lines;
- `tests completed` with exactly 3 failures, which are the 3 known failures listed in Global Constraints;
- `Success` from adb.

- [ ] **Step 7: Manual check on the emulator (with the user)**

1. Log ketamine **insufflated 40 mg** about 30 min ago and ketamine **oral 150 mg** about 20 min ago, in one experience.
2. The experience screen shows **one** pink ketamine line. Under the chart: `● Ketamine · N% of peak` with `≈ X mg insufflated / ≈ Y mg oral equivalent` below it, where Y / X ≈ 3.8.
3. There are two redose chips (insufflated 40 mg and oral 150 mg). Holding either one updates the single line. The peak line names the route.
4. The full-screen timeline shows the same thing.
5. Settings → turn **Merge routes of same substance** off → the experience shows two lines again, as before this feature.

- [ ] **Step 8: Commit**

```bash
git add app/src
git commit -m "Add setting to merge routes of the same substance

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
