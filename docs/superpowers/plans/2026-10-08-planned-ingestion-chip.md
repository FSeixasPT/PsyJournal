# Planned-Ingestion Chip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-route redose chips with one chip that holds a planned ingestion of any substance, route and dose. The chip has a searchable editor and a quick "took it" log.

**Architecture:** Pure Kotlin functions do the work: build a chart line from the substance database (`buildPlannedLine`), choose defaults and the colour, build the picker list, and build the quick-log ingestion. They are tested as JVM unit tests. `AllTimelinesModel.withPlannedIngestion(line)` takes the ready-made line. A shared `PlannedIngestionHelper` creates a `PlannedIngestionActions` object for each timeline view model. The two screens render one `PlannedIngestionSection`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-08-planned-ingestion-chip-design.md`

## Global Constraints

- Gradle runs with JDK 21. In Git Bash: `export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"`, then `./gradlew ...` from `D:/Documentos/Projetos/Psychonautwiki`.
- These three tests already fail and are unrelated: `TestDates.dateDifferences`, `TestParse.testExtractSubstancesString` and `TestParse.noCrash`. "All tests pass" means everything except these.
- New source files start with the PsyJournal GPL header, copied from lines 1-17 of `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TimelineQuantityDetail.kt`.
- A route is previewable when its `roaDuration` has full onset, come-up, peak and offset (`toFullDurations() != null`) **and** `roaDose?.averageCommonDose != null`.
- The planned dose is always in the database unit of its route. A planned ingestion whose units differ from `roaDose.units` cannot be previewed.
- Dose pre-fill: the last dose of that substance and route in this experience if it was logged in the database unit, otherwise `commonMin`.
- Picker reasons, verbatim: `Can't be previewed: no full duration data for any route` and `Can't be previewed: no dose ranges for any route`. When the routes fail for different reasons, the duration reason is shown. Route reasons, verbatim: `no full duration data` and `no dose ranges`.
- Chip texts, verbatim: header `Hold to preview taking now:`, empty chip `Choose a substance to preview`, chip `<substance> · <dose> <units> <route lowercase>`.
- Quick-log dialog: title `Log ingestion now?`, second line `now (HH:MM) · this experience · you`, buttons `Cancel` and `Log`.
- The saved pin format is unchanged, so pins saved by the current version still load.
- Custom substances are never listed.

## Review Focus

1. **A pin for a substance that isn't in the experience** (e.g. planned MDMA while on ketamine). Logging ketamine must not end it; logging MDMA by that route must. Test: Task 2 `pinOfAnotherSubstanceEndsOnlyWhenThatSubstanceIsLogged`.
2. **Changing the dose of a pinned chip** keeps the original pin time. Otherwise an ingestion logged between the original and the new pin time would no longer end the pin. Test: Task 2 `changingThePinnedDoseKeepsThePinTime`.
3. **Typing a dose with a comma, or 0, or text.** `0,5` must work; `0`, `-1` and `abc` must disable Done. Test: Task 3 `doseTextIsParsedWithCommaAndRejectsNonPositive`.
4. **A search that matches nothing** gives an empty list instead of a crash. Test: Task 3 `searchWithNoMatchesGivesNoEntries`.
5. **Interactions when a substance was taken several times, or when the planned substance is already in the experience.** Each other substance should be listed once, and never the planned substance against itself. Test: Task 4 `interactionsListEachOtherSubstanceOnce`.

---

## File Structure

All paths are under `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/`, unless noted (tests are under `app/src/test/java/...` with the same package).

| File | Responsibility | Change |
|---|---|---|
| `components/getStrengthRelativeToCommonDose.kt` | dose → strength helpers | add `horizontalWeightFromDots` |
| `ExperienceViewModel.kt` | experience data | use `horizontalWeightFromDots`; expose `plannedIngestionActions` |
| `timeline/PlannedIngestion.kt` | planned ingestion type and pure rules | new |
| `timeline/SubstancePicker.kt` | picker entries, route pre-selection, dose parsing, ranges text | new |
| `timeline/QuickLog.kt` | building the quick-log ingestion, companion and interactions | new |
| `timeline/PlannedIngestionActions.kt` | interface plus the shared Hilt helper | new |
| `timeline/PlannedIngestionUi.kt` | state, chip, editor sheet, quick-log dialog | new; replaces `RedosePreviewRow.kt` and `RedosePreviewState.kt` |
| `timeline/AllTimelinesModel.kt` | chart model | take a planned line; `realLines`, `colors`, `resolvePinnedPlan` |
| `timeline/PinnedRedose.kt` | saved pin | built from `PlannedIngestion` |
| `timeline/RedoseOption.kt`, `timeline/RedosePreviewRow.kt`, `timeline/RedosePreviewState.kt` | old chips | deleted in Task 6 |
| `timeline/screen/TimelineScreenViewModel.kt`, `timeline/screen/TimelineScreen.kt`, `ExperienceScreen.kt` | screens | use the new section |
| test `timeline/TestSubstances.kt` | factory for substances in tests | new |
| tests `PlannedLineTest.kt`, `PlannedIngestionDefaultsTest.kt`, `SubstancePickerTest.kt`, `QuickLogTest.kt` | new tests | new |
| tests `RedosePreviewTest.kt`, `RouteMergingTest.kt`, `PinnedRedoseTest.kt` | existing tests | adapted |

---

### Task 1: Planned ingestion type and building its line

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/components/getStrengthRelativeToCommonDose.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceViewModel.kt` (the horizontal weight in `getDataForEffectTimelines`)
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestion.kt`
- Create: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TestSubstances.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedLineTest.kt`

**Interfaces:**
- Produces:
  - `fun horizontalWeightFromDots(numDots: Int?): Float`
  - `data class PlannedIngestion(substanceName: String, route: AdministrationRoute, dose: Double, units: String)`
  - `enum class PreviewProblem { NO_FULL_DURATION, NO_DOSE_RANGES }`
  - `fun Roa.previewProblem(): PreviewProblem?`
  - `fun buildPlannedLine(planned: PlannedIngestion, substance: Substance, color: AdaptiveColor, time: Instant): DataForOneEffectLine?`
  - `fun choosePlannedColor(substanceName: String, companions: List<SubstanceCompanion>, colorsOnChart: List<AdaptiveColor>): AdaptiveColor`
  - Test util: `fun testSubstance(name: String, vararg roas: Roa): Substance`, `fun exactDuration(onset: Float, comeup: Float, peak: Float, offset: Float): RoaDuration` (minutes), and `val ketamine: Substance`, defined as follows:
    - insufflated: previewable, mg, light 5, common 30, strong 75, heavy 175, durations 10/10/20/20 min;
    - oral: dose ranges but no peak duration;
    - sublingual: full durations but no dose ranges.

- [ ] **Step 1: Write the test utility and the failing tests**

Create `TestSubstances.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration

fun exactDuration(onset: Float, comeup: Float, peak: Float, offset: Float) = RoaDuration(
    onset = DurationRange(onset, onset, DurationUnits.MINUTES),
    comeup = DurationRange(comeup, comeup, DurationUnits.MINUTES),
    peak = DurationRange(peak, peak, DurationUnits.MINUTES),
    offset = DurationRange(offset, offset, DurationUnits.MINUTES),
    total = null,
    afterglow = null
)

fun testSubstance(name: String, vararg roas: Roa) = Substance(
    name = name,
    commonNames = emptyList(),
    url = "",
    isApproved = true,
    tolerance = null,
    crossTolerances = emptyList(),
    addictionPotential = null,
    toxicities = emptyList(),
    categories = emptyList(),
    summary = null,
    effectsSummary = null,
    dosageRemark = null,
    generalRisks = null,
    longtermRisks = null,
    saferUse = emptyList(),
    interactions = null,
    roas = roas.toList()
)

val ketamineInsufflatedDose = RoaDose(units = "mg", lightMin = 5.0, commonMin = 30.0, strongMin = 75.0, heavyMin = 175.0)
val ketamineInsufflatedDuration = exactDuration(onset = 10f, comeup = 10f, peak = 20f, offset = 20f)

val ketamine = testSubstance(
    "Ketamine",
    Roa(AdministrationRoute.INSUFFLATED, ketamineInsufflatedDose, ketamineInsufflatedDuration, null),
    Roa(
        AdministrationRoute.ORAL,
        RoaDose(units = "mg", lightMin = 50.0, commonMin = 100.0, strongMin = 300.0, heavyMin = 450.0),
        exactDuration(onset = 30f, comeup = 30f, peak = 60f, offset = 60f).copy(peak = null),
        null
    ),
    Roa(AdministrationRoute.SUBLINGUAL, null, exactDuration(onset = 5f, comeup = 10f, peak = 30f, offset = 30f), null)
)
```

Create `PlannedLineTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.horizontalWeightFromDots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PlannedLineTest {

    private val time = Instant.parse("2026-01-01T21:00:00Z")

    private fun planned(dose: Double = 52.5, route: AdministrationRoute = AdministrationRoute.INSUFFLATED, units: String = "mg") =
        PlannedIngestion(substanceName = "Ketamine", route = route, dose = dose, units = units)

    @Test
    fun plannedLineIsScaledByTheReferenceDoseOfItsRoute() {
        val line = buildPlannedLine(planned(dose = 52.5), ketamine, AdaptiveColor.PINK, time)!!
        assertEquals("Ketamine", line.substanceName)
        assertEquals(AdministrationRoute.INSUFFLATED, line.route)
        assertEquals(1f, line.height, 0.001f)
        assertEquals(52.5, line.referenceDose!!, 0.001)
        assertEquals("mg", line.doseUnits)
        assertEquals(true, line.isReferenceDoseFromDoseRanges)
        assertEquals(ketamineInsufflatedDuration, line.roaDuration)
        assertEquals(AdaptiveColor.PINK, line.color)
        assertEquals(time, line.startTime)
        assertNull(line.endTime)
        assertEquals(horizontalWeightFromDots(ketamineInsufflatedDose.getNumDots(52.5, "mg")), line.horizontalWeight, 0.001f)
    }

    @Test
    fun noLineWithoutFullDurations() {
        assertNull(buildPlannedLine(planned(dose = 150.0, route = AdministrationRoute.ORAL), ketamine, AdaptiveColor.PINK, time))
    }

    @Test
    fun noLineWithoutDoseRanges() {
        assertNull(buildPlannedLine(planned(route = AdministrationRoute.SUBLINGUAL), ketamine, AdaptiveColor.PINK, time))
    }

    @Test
    fun noLineWhenUnitsDifferFromTheDatabase() {
        assertNull(buildPlannedLine(planned(units = "µg"), ketamine, AdaptiveColor.PINK, time))
    }

    @Test
    fun noLineForARouteTheSubstanceDoesNotHave() {
        assertNull(buildPlannedLine(planned(route = AdministrationRoute.RECTAL), ketamine, AdaptiveColor.PINK, time))
    }

    @Test
    fun previewProblemOfEachRoute() {
        assertNull(ketamine.getRoa(AdministrationRoute.INSUFFLATED)!!.previewProblem())
        assertEquals(PreviewProblem.NO_FULL_DURATION, ketamine.getRoa(AdministrationRoute.ORAL)!!.previewProblem())
        assertEquals(PreviewProblem.NO_DOSE_RANGES, ketamine.getRoa(AdministrationRoute.SUBLINGUAL)!!.previewProblem())
    }

    @Test
    fun horizontalWeightGrowsWithDotsUpToFour() {
        assertEquals(0.5f, horizontalWeightFromDots(null), 0.001f)
        assertEquals(0.25f, horizontalWeightFromDots(1), 0.001f)
        assertEquals(0.5f, horizontalWeightFromDots(2), 0.001f)
        assertEquals(1f, horizontalWeightFromDots(4), 0.001f)
        assertEquals(1f, horizontalWeightFromDots(6), 0.001f)
    }

    @Test
    fun colorIsTheCompanionColorIfThereIsOne() {
        val companions = listOf(SubstanceCompanion("Ketamine", AdaptiveColor.TEAL))
        assertEquals(AdaptiveColor.TEAL, choosePlannedColor("Ketamine", companions, listOf(AdaptiveColor.RED)))
    }

    @Test
    fun colorIsTheFirstColorNotOnTheChartOtherwise() {
        val expected = AdaptiveColor.entries.first { it != AdaptiveColor.RED }
        assertEquals(expected, choosePlannedColor("Ketamine", emptyList(), listOf(AdaptiveColor.RED)))
        assertEquals(AdaptiveColor.entries.first(), choosePlannedColor("Ketamine", emptyList(), AdaptiveColor.entries.toList()))
    }
}
```

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlannedLineTest*' --console=plain 2>&1 | grep -E "^e: |BUILD" | head -5`
Expected: compilation errors such as `Unresolved reference 'PlannedIngestion'` and `Unresolved reference 'horizontalWeightFromDots'`. If `AdaptiveColor.TEAL` doesn't exist, use any other constant of `AdaptiveColor` that isn't `RED`, and record a ruling in the ledger.

- [ ] **Step 3: Add `horizontalWeightFromDots` and use it in the view model**

Append to `components/getStrengthRelativeToCommonDose.kt`:

```kotlin

// stronger doses last a bit longer: the dots of a dose (1 to 4) set where in the duration ranges the line ends
fun horizontalWeightFromDots(numDots: Int?): Float {
    return if (numDots == null) {
        0.5f
    } else if (numDots > 4) {
        1f
    } else {
        numDots.toFloat() / 4f
    }
}
```

In `ExperienceViewModel.getDataForEffectTimelines`, replace:

```kotlin
                val horizontalWeight = if (oneElement.numDots == null) {
                    0.5f
                } else if (oneElement.numDots > 4) {
                    1f
                } else {
                    oneElement.numDots.toFloat() / 4f
                }
```

with:

```kotlin
                val horizontalWeight = horizontalWeightFromDots(oneElement.numDots)
```

Then add `import com.isaakhanimann.journal.ui.tabs.journal.experience.components.horizontalWeightFromDots` next to the existing `getCommonDose` import.

- [ ] **Step 4: Create `PlannedIngestion.kt`** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.horizontalWeightFromDots
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullDurations
import java.time.Instant

// an ingestion that is previewed on the chart and can be logged, the dose is in the database unit of the route
data class PlannedIngestion(
    val substanceName: String,
    val route: AdministrationRoute,
    val dose: Double,
    val units: String
)

enum class PreviewProblem { NO_FULL_DURATION, NO_DOSE_RANGES }

// null if a planned ingestion of this route can be drawn and compared with other ingestions
fun Roa.previewProblem(): PreviewProblem? = when {
    roaDuration?.toFullDurations() == null -> PreviewProblem.NO_FULL_DURATION
    roaDose?.averageCommonDose == null -> PreviewProblem.NO_DOSE_RANGES
    else -> null
}

fun buildPlannedLine(
    planned: PlannedIngestion,
    substance: Substance,
    color: AdaptiveColor,
    time: Instant
): DataForOneEffectLine? {
    val roa = substance.getRoa(planned.route) ?: return null
    if (roa.previewProblem() != null) return null
    val roaDose = roa.roaDose ?: return null
    if (planned.units != roaDose.units) return null
    val referenceDose = roaDose.averageCommonDose ?: return null
    return DataForOneEffectLine(
        substanceName = planned.substanceName,
        route = planned.route,
        roaDuration = roa.roaDuration,
        height = (planned.dose / referenceDose).toFloat(),
        horizontalWeight = horizontalWeightFromDots(roaDose.getNumDots(planned.dose, planned.units)),
        color = color,
        startTime = time,
        endTime = null,
        referenceDose = referenceDose,
        doseUnits = planned.units,
        isReferenceDoseFromDoseRanges = true
    )
}

// the color the substance already has in the journal, otherwise one that is not yet on the chart
fun choosePlannedColor(
    substanceName: String,
    companions: List<SubstanceCompanion>,
    colorsOnChart: List<AdaptiveColor>
): AdaptiveColor {
    companions.firstOrNull { it.substanceName == substanceName }?.let { return it.color }
    return AdaptiveColor.entries.firstOrNull { it !in colorsOnChart } ?: AdaptiveColor.entries.first()
}
```

- [ ] **Step 5: Run the tests to verify that they pass, and check that the app compiles**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests '*PlannedLineTest*' --tests '*TimelineQuantityDetailsTest*' --tests '*RouteMergingTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`. Check with `grep -ho 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml` that PlannedLineTest has 9 tests and 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Build a chart line for a planned ingestion from the substance database

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The chart model takes a planned line; defaults and pin rules

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/AllTimelinesModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestion.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PinnedRedose.kt`
- Modify tests: `RedosePreviewTest.kt`, `RouteMergingTest.kt`, `PinnedRedoseTest.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestionDefaultsTest.kt`

**Interfaces:**
- Consumes (Task 1): `PlannedIngestion`, `Roa.previewProblem()`, `ketamine`, `testSubstance` and `exactDuration`.
- Produces:
  - `AllTimelinesModel.withPlannedIngestion(line: DataForOneEffectLine): AllTimelinesModel`
  - `AllTimelinesModel.realLines: List<DataForOneEffectLine>` (logged lines only)
  - `AllTimelinesModel.colors: List<AdaptiveColor>`
  - `AllTimelinesModel.plannedIngestionTime: Instant?` (now derived from the planned line)
  - `AllTimelinesModel.resolvePinnedPlan(pin: PinnedRedose, now: Instant): PlannedIngestion?`
  - `fun suggestedDose(substanceName: String, route: AdministrationRoute, realLines: List<DataForOneEffectLine>, roaDose: RoaDose): Double?`
  - `fun defaultPlannedIngestion(realLines: List<DataForOneEffectLine>, getSubstance: (String) -> Substance?): PlannedIngestion?`
  - `fun PlannedIngestion.chipText(): String`
  - `fun PlannedIngestion.toPinnedRedose(experienceId: Int, currentPin: PinnedRedose?, now: Instant): PinnedRedose`
- Keeps until Task 6, so the current UI still compiles (each marked `// removed in Task 6`):
  - `redoseOptions`
  - `withPlannedIngestion(option: RedoseOption, dose: Double, time: Instant)`
  - `resolvePinnedRedose`
  - `RedoseOption.toPinnedRedose`

- [ ] **Step 1: Write the failing tests**

Create `PlannedIngestionDefaultsTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class PlannedIngestionDefaultsTest {

    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)

    // MDMA has dose ranges but no durations, so it can't be previewed
    private val mdma = testSubstance(
        "MDMA",
        Roa(AdministrationRoute.ORAL, ketamineInsufflatedDose.copy(units = "mg"), null, null)
    )
    private val substances = mapOf("Ketamine" to ketamine, "MDMA" to mdma)

    private fun line(
        substance: String,
        route: AdministrationRoute,
        minutes: Long,
        dose: Double?,
        units: String = "mg",
        referenceDose: Double = 52.5
    ) = DataForOneEffectLine(
        substanceName = substance,
        route = route,
        roaDuration = ketamineInsufflatedDuration,
        height = if (dose != null) (dose / referenceDose).toFloat() else 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.PINK,
        startTime = at(minutes),
        endTime = null,
        referenceDose = if (dose != null) referenceDose else null,
        doseUnits = if (dose != null) units else null,
        isReferenceDoseFromDoseRanges = true
    )

    @Test
    fun defaultIsTheLatestPreviewableIngestion() {
        val lines = listOf(
            line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40.0),
            line("MDMA", AdministrationRoute.ORAL, 30, dose = 100.0)
        )
        val planned = defaultPlannedIngestion(lines) { substances[it] }!!
        assertEquals("Ketamine", planned.substanceName)
        assertEquals(AdministrationRoute.INSUFFLATED, planned.route)
        assertEquals(40.0, planned.dose, 0.01)
        assertEquals("mg", planned.units)
    }

    @Test
    fun defaultSkipsIngestionsWithUnknownDose() {
        val lines = listOf(
            line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40.0),
            line("Ketamine", AdministrationRoute.INSUFFLATED, 30, dose = null)
        )
        assertEquals(40.0, defaultPlannedIngestion(lines) { substances[it] }!!.dose, 0.01)
    }

    @Test
    fun doseLoggedInOtherUnitsFallsBackToTheStartOfTheCommonRange() {
        val lines = listOf(line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40000.0, units = "µg"))
        assertEquals(30.0, defaultPlannedIngestion(lines) { substances[it] }!!.dose, 0.01)
    }

    @Test
    fun noDefaultWhenNothingCanBePreviewed() {
        val lines = listOf(line("MDMA", AdministrationRoute.ORAL, 0, dose = 100.0))
        assertNull(defaultPlannedIngestion(lines) { substances[it] })
    }

    @Test
    fun suggestedDoseIsTheLastDoseInTheDatabaseUnit() {
        val lines = listOf(
            line("Ketamine", AdministrationRoute.INSUFFLATED, 0, dose = 40.0),
            line("Ketamine", AdministrationRoute.INSUFFLATED, 30, dose = 25.0)
        )
        assertEquals(25.0, suggestedDose("Ketamine", AdministrationRoute.INSUFFLATED, lines, ketamineInsufflatedDose)!!, 0.01)
        assertEquals(30.0, suggestedDose("Ketamine", AdministrationRoute.INSUFFLATED, emptyList(), ketamineInsufflatedDose)!!, 0.01)
    }

    @Test
    fun chipTextAlwaysNamesTheRoute() {
        val planned = PlannedIngestion("Ketamine", AdministrationRoute.INSUFFLATED, 40.0, "mg")
        assertEquals("Ketamine · 40 mg insufflated", planned.chipText())
    }

    @Test
    fun changingThePinnedDoseKeepsThePinTime() {
        val first = PlannedIngestion("Ketamine", AdministrationRoute.INSUFFLATED, 40.0, "mg")
            .toPinnedRedose(experienceId = 7, currentPin = null, now = at(10))
        val changed = PlannedIngestion("Ketamine", AdministrationRoute.INSUFFLATED, 30.0, "mg")
            .toPinnedRedose(experienceId = 7, currentPin = first, now = at(20))
        assertEquals(at(10), changed.pinnedAt)
        assertEquals(30.0, changed.dose, 0.01)
        val other = PlannedIngestion("MDMA", AdministrationRoute.ORAL, 100.0, "mg")
            .toPinnedRedose(experienceId = 7, currentPin = first, now = at(20))
        assertEquals(at(20), other.pinnedAt)
    }
}
```

Adapt the existing tests to the new API. The expected numbers don't change.

In `RedosePreviewTest.kt`:
1. Replace every match of the regex `real\.withPlannedIngestion\(real\.redoseOptions\.single\(\), dose = ([0-9.]+), time = at\(([0-9]+)\)\)` with `real.withPlannedIngestion(line($2, dose = $1))`.
2. Delete the four tests that test the old per-route chips: `redoseOptionUsesLastDoseOfSubstanceAndRoute`, `thereIsOneRedoseOptionPerSubstanceAndRoute`, `noRedoseOptionWhenADoseIsUnknown` and `noRedoseOptionWhenDurationIsIncomplete`.
3. Afterwards, `grep -n "redoseOptions" RedosePreviewTest.kt` must return nothing.

In `RouteMergingTest.kt`:
1. Delete `mergedSubstanceHasOneRedoseChipPerRoute` and `chipNamesTheRouteOnlyWhenTheSubstanceHasSeveralChips`.
2. Replace these tests with the following versions:

```kotlin
    @Test
    fun plannedDoseOfOneRouteMergesIntoTheSubstanceLine() {
        val real = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, 200.0))
        val preview = real.withPlannedIngestion(line(20, ORAL, 200.0))
        assertEquals(1, preview.getQuantityDetails(at(30)).size)
    }

    @Test
    fun peakOfMergedLineIsExpressedInThePlannedRouteAndNamesIt() {
        // only insufflated taken so far, as 52.5 mg at 0; plan 52.5 mg insufflated at 0 as well: peak 2 x 52.5 = 105 mg
        val real = model(true, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0))
        val peak = real.withPlannedIngestion(line(0, INSUFFLATED, 52.5)).plannedPeak!!
        assertEquals(105.0, peak.doseEquivalent, 0.01)
        assertEquals("mg", peak.units)
        assertEquals(INSUFFLATED, peak.route)
    }

    @Test
    fun peakOfUnmergedLineDoesNotNameTheRoute() {
        val real = model(false, line(0, INSUFFLATED, 52.5), line(300, ORAL, 200.0))
        val peak = real.withPlannedIngestion(line(0, INSUFFLATED, 52.5)).plannedPeak!!
        assertEquals(null, peak.route)
    }

    @Test
    fun plannedDoseMergesWhenAnotherRouteHasAnUnknownDose() {
        val real = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, null))
        val preview = real.withPlannedIngestion(line(20, INSUFFLATED, 52.5))
        assertEquals(1, preview.getQuantityDetails(at(30)).size)
    }

    @Test
    fun noPeakWhenAnotherRouteOfTheLineHasAnUnknownDose() {
        // the oral dose is unknown, so the combined line contains an assumed dose and its peak can't be expressed as a dose
        val real = model(true, line(0, INSUFFLATED, 52.5), line(10, ORAL, null))
        val preview = real.withPlannedIngestion(line(20, INSUFFLATED, 52.5))
        assertEquals(null, preview.plannedPeak)
    }

    @Test
    fun plannedDifferentSubstanceGetsItsOwnLine() {
        val real = model(true, line(0, INSUFFLATED, 52.5))
        val mdma = line(30, ORAL, 200.0).copy(substanceName = "MDMA", color = AdaptiveColor.BLUE)
        val preview = real.withPlannedIngestion(mdma)
        assertEquals(2, preview.groupDrawables.size)
        assertEquals(AdaptiveColor.BLUE, preview.plannedIngestionColor)
        assertEquals(200.0, preview.plannedPeak!!.doseEquivalent, 0.01)
        assertEquals(null, preview.plannedPeak!!.route)
    }
```

The old test `unknownDoseRouteHasNoChipButOtherRouteStillMerges` is replaced by `plannedDoseMergesWhenAnotherRouteHasAnUnknownDose` above, so delete it. Afterwards, `grep -n "redoseOptions\|chipText" RouteMergingTest.kt` must return nothing.

In `PinnedRedoseTest.kt`:
1. Replace every `.resolvePinnedRedose(` with `.resolvePinnedPlan(`.
2. Delete `pinEndsWhenItsChipNoLongerExists`.
3. Append these tests:

```kotlin
    @Test
    fun pinSavedByThePreviousVersionStillLoads() {
        val saved = listOf("7", "Ketamine", "INSUFFLATED", "30.0", "mg", "2026-01-01T20:10:00Z").joinToString("\u001F")
        assertEquals(pin(dose = 30.0, pinnedAt = at(10)), PinnedRedose.fromPreferenceString(saved))
    }

    @Test
    fun pinOfAnotherSubstanceEndsOnlyWhenThatSubstanceIsLogged() {
        val mdmaPin = pin(pinnedAt = at(10)).copy(substanceName = "MDMA", route = AdministrationRoute.ORAL, dose = 100.0)
        val ketamineLater = model(line(0, dose = 40.0), line(30, dose = 30.0))
        assertEquals("MDMA", ketamineLater.resolvePinnedPlan(mdmaPin, now = at(40))!!.substanceName)
        val mdmaLater = model(
            line(0, dose = 40.0),
            line(30, dose = 100.0, route = AdministrationRoute.ORAL).copy(substanceName = "MDMA")
        )
        assertNull(mdmaLater.resolvePinnedPlan(mdmaPin, now = at(40)))
    }
```

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlannedIngestionDefaultsTest*' --tests '*RedosePreviewTest*' --tests '*RouteMergingTest*' --tests '*PinnedRedoseTest*' --console=plain 2>&1 | grep -E "^e: |BUILD" | head -5`
Expected: compilation errors such as `Unresolved reference 'defaultPlannedIngestion'`, `Unresolved reference 'resolvePinnedPlan'`, and an argument-type mismatch for `withPlannedIngestion(DataForOneEffectLine)`.

- [ ] **Step 3: Change `AllTimelinesModel`**

(a) Replace the constructor parameters

```kotlin
    // set if this model is a preview of a planned ingestion that is not logged
    private val plannedOption: RedoseOption? = null,
    private val plannedLine: DataForOneEffectLine? = null,
    val plannedIngestionTime: Instant? = null,
) {
```

with

```kotlin
    // set if this model is a preview of a planned ingestion that is not logged
    private val plannedLine: DataForOneEffectLine? = null,
) {
    val plannedIngestionTime: Instant? get() = plannedLine?.startTime

    // the logged lines without the planned one
    val realLines: List<DataForOneEffectLine> =
        if (plannedLine == null) dataForLines else dataForLines - plannedLine
```

(b) Below `this.groupDrawables = groupDrawables` in `init`, nothing changes. After the `init` block, add:

```kotlin
    val colors: List<AdaptiveColor> get() = roaGroups.map { it.color }.distinct()
```

(c) Replace `resolvePinnedRedose` and the old `withPlannedIngestion` (from `    // the option to preview for a pin` up to the end of `withPlannedIngestion`) with:

```kotlin
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

    // removed in Task 6
    fun resolvePinnedRedose(pin: PinnedRedose, now: Instant): RedoseOption? {
        val plan = resolvePinnedPlan(pin, now) ?: return null
        val option = redoseOptions.firstOrNull { it.substanceName == plan.substanceName && it.route == plan.route }
            ?: return null
        return option.copy(dose = plan.dose)
    }

    // removed in Task 6
    fun withPlannedIngestion(option: RedoseOption, dose: Double, time: Instant): AllTimelinesModel {
        val lastLine = realLines
            .filter { it.substanceName == option.substanceName && it.route == option.route }
            .maxBy { it.startTime }
        val referenceDose = lastLine.referenceDose ?: return this
        return withPlannedIngestion(
            lastLine.copy(height = (dose / referenceDose).toFloat(), startTime = time, endTime = null)
        )
    }
```

Add `// removed in Task 6` above `val redoseOptions`.

(d) In `plannedPeak`, `plannedFractionOfPeakAt` and `plannedIngestionPoint`, replace `val option = plannedOption ?: return@lazy null` (or `?: return null`) with `val option = plannedLine ?: return@lazy null` (or `?: return null`), keeping the existing lookups `option.substanceName` and `option.route`.

In `plannedPeak`:
- replace `units = option.units,` with `units = option.doseUnits ?: return@lazy null,`;
- delete the line `val time = plannedIngestionTime ?: return@lazy null` and use `option.startTime` in place of `time`.

In `plannedIngestionPoint`, do the same: delete `val time = ...` and use `option.startTime`.

(e) `withPlannedIngestion(line)` must not change `plannedIngestionColor`, which already reads `plannedLine?.color`. Check this with `grep -n "plannedOption" AllTimelinesModel.kt`, which must return nothing.

- [ ] **Step 4: Extend `PlannedIngestion.kt` and `PinnedRedose.kt`**

Append to `PlannedIngestion.kt`, adding the imports `com.isaakhanimann.journal.data.substances.classes.roa.RoaDose` and `com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString`:

```kotlin

// the last dose of that substance and route if it was logged in the database unit, else the start of the common range
fun suggestedDose(
    substanceName: String,
    route: AdministrationRoute,
    realLines: List<DataForOneEffectLine>,
    roaDose: RoaDose
): Double? {
    val lastLine = realLines
        .filter { it.substanceName == substanceName && it.route == route && it.referenceDose != null }
        .maxByOrNull { it.startTime }
    if (lastLine != null && lastLine.doseUnits == roaDose.units) {
        return lastLine.height * lastLine.referenceDose!!
    }
    return roaDose.commonMin
}

// the latest logged ingestion with a known dose whose substance and route can be previewed
fun defaultPlannedIngestion(
    realLines: List<DataForOneEffectLine>,
    getSubstance: (String) -> Substance?
): PlannedIngestion? {
    for (line in realLines.sortedByDescending { it.startTime }) {
        if (line.referenceDose == null) continue
        val roa = getSubstance(line.substanceName)?.getRoa(line.route) ?: continue
        if (roa.previewProblem() != null) continue
        val roaDose = roa.roaDose ?: continue
        val dose = suggestedDose(line.substanceName, line.route, realLines, roaDose) ?: continue
        return PlannedIngestion(line.substanceName, line.route, dose, roaDose.units)
    }
    return null
}

fun PlannedIngestion.chipText(): String =
    "$substanceName · ${dose.toReadableString()} $units ${route.displayText.lowercase()}"
```

In `PinnedRedose.kt`, add next to `RedoseOption.toPinnedRedose`:

```kotlin
// keeps the original pin time if the same substance and route stays pinned, e.g. when only its dose changes
fun PlannedIngestion.toPinnedRedose(experienceId: Int, currentPin: PinnedRedose?, now: Instant): PinnedRedose {
    val isSamePlan = currentPin != null && currentPin.experienceId == experienceId &&
            currentPin.substanceName == substanceName && currentPin.route == route
    return PinnedRedose(
        experienceId = experienceId,
        substanceName = substanceName,
        route = route,
        dose = dose,
        units = units,
        pinnedAt = if (isSamePlan) currentPin!!.pinnedAt else now
    )
}
```

Mark `RedoseOption.toPinnedRedose` with `// removed in Task 6`.

- [ ] **Step 5: Run the tests to verify that they pass, and check that the app compiles**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests '*PlannedIngestionDefaultsTest*' --tests '*RedosePreviewTest*' --tests '*RouteMergingTest*' --tests '*PinnedRedoseTest*' --tests '*PlannedLineTest*' --tests '*TimelineQuantityDetailsTest*' --tests '*FullTimelinesTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL` with no failures.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Let the timeline take any planned line and resolve pins to planned ingestions

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Substance picker logic

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/SubstancePicker.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/SubstancePickerTest.kt`

**Interfaces:**
- Consumes (Task 1): `PreviewProblem` and `Roa.previewProblem()`.
- Produces:
  - `data class PickerEntry(substanceName: String, isInExperience: Boolean, problem: PreviewProblem?)`
  - `fun buildPickerEntries(matchingSubstances: List<Substance>, experienceSubstanceNamesByRecency: List<String>): List<PickerEntry>`
  - `fun Substance.substancePreviewProblem(): PreviewProblem?`
  - `fun preselectedRoute(substance: Substance, experienceRoutesByRecency: List<AdministrationRoute>): AdministrationRoute?`
  - `fun parseDose(text: String): Double?`
  - `fun RoaDose.rangesText(): String`

- [ ] **Step 1: Write the failing tests**

Create `SubstancePickerTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.Roa
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstancePickerTest {

    private val fullDuration = exactDuration(10f, 10f, 20f, 20f)
    private val dose = RoaDose("mg", 5.0, 30.0, 75.0, 175.0)

    private val cocaine = testSubstance("Cocaine", Roa(AdministrationRoute.INSUFFLATED, dose, fullDuration, null))
    private val mdma = testSubstance("MDMA", Roa(AdministrationRoute.ORAL, dose, null, null))
    private val lsd = testSubstance("LSD", Roa(AdministrationRoute.SUBLINGUAL, null, fullDuration, null))
    private val mixed = testSubstance(
        "Mixed",
        Roa(AdministrationRoute.ORAL, null, fullDuration, null),
        Roa(AdministrationRoute.INSUFFLATED, dose, null, null)
    )

    @Test
    fun substancesOfTheExperienceComeFirstInOrderOfRecency() {
        val entries = buildPickerEntries(
            matchingSubstances = listOf(cocaine, ketamine, lsd, mdma),
            experienceSubstanceNamesByRecency = listOf("Ketamine", "MDMA")
        )
        assertEquals(listOf("Ketamine", "MDMA", "Cocaine", "LSD"), entries.map { it.substanceName })
        assertEquals(listOf(true, true, false, false), entries.map { it.isInExperience })
        assertEquals(listOf(null, PreviewProblem.NO_FULL_DURATION, null, PreviewProblem.NO_DOSE_RANGES), entries.map { it.problem })
    }

    @Test
    fun substanceOfTheExperienceThatDoesNotMatchTheSearchIsLeftOut() {
        val entries = buildPickerEntries(listOf(cocaine), listOf("Ketamine"))
        assertEquals(listOf("Cocaine"), entries.map { it.substanceName })
    }

    @Test
    fun searchWithNoMatchesGivesNoEntries() {
        assertTrue(buildPickerEntries(emptyList(), listOf("Ketamine")).isEmpty())
    }

    @Test
    fun durationReasonWinsWhenRoutesFailForDifferentReasons() {
        assertEquals(PreviewProblem.NO_FULL_DURATION, mixed.substancePreviewProblem())
        assertEquals(PreviewProblem.NO_FULL_DURATION, testSubstance("Empty").substancePreviewProblem())
    }

    @Test
    fun preselectedRouteIsTheLatestPreviewableRouteOfTheExperience() {
        assertEquals(
            AdministrationRoute.INSUFFLATED,
            preselectedRoute(ketamine, listOf(AdministrationRoute.ORAL, AdministrationRoute.INSUFFLATED))
        )
        assertEquals(AdministrationRoute.INSUFFLATED, preselectedRoute(ketamine, emptyList()))
        assertNull(preselectedRoute(mdma, emptyList()))
    }

    @Test
    fun doseTextIsParsedWithCommaAndRejectsNonPositive() {
        assertEquals(0.5, parseDose("0,5")!!, 0.0001)
        assertEquals(40.0, parseDose(" 40 ")!!, 0.0001)
        assertNull(parseDose("0"))
        assertNull(parseDose("-1"))
        assertNull(parseDose("abc"))
        assertNull(parseDose(""))
    }

    @Test
    fun rangesTextListsTheKnownRanges() {
        assertEquals("light 5–30 · common 30–75 · strong 75–175 · heavy 175+ mg", dose.rangesText())
        assertEquals("common 30–75 · strong 75–175 · heavy 175+ mg", dose.copy(lightMin = null).rangesText())
    }
}
```

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*SubstancePickerTest*' --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3`
Expected: `Unresolved reference 'buildPickerEntries'`.

- [ ] **Step 3: Create `SubstancePicker.kt`** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString

data class PickerEntry(
    val substanceName: String,
    val isInExperience: Boolean,
    // null if at least one route can be previewed
    val problem: PreviewProblem?
)

// the substances of the experience first (most recent first), then the other matches in their given order
fun buildPickerEntries(
    matchingSubstances: List<Substance>,
    experienceSubstanceNamesByRecency: List<String>
): List<PickerEntry> {
    val byName = matchingSubstances.associateBy { it.name }
    val inExperience = experienceSubstanceNamesByRecency.distinct().mapNotNull { byName[it] }
    val others = matchingSubstances.filter { it.name !in experienceSubstanceNamesByRecency }
    return inExperience.map { PickerEntry(it.name, isInExperience = true, problem = it.substancePreviewProblem()) } +
            others.map { PickerEntry(it.name, isInExperience = false, problem = it.substancePreviewProblem()) }
}

// the reason why no route of the substance can be previewed, missing durations win over missing dose ranges
fun Substance.substancePreviewProblem(): PreviewProblem? {
    val problems = roas.map { it.previewProblem() }
    return when {
        problems.any { it == null } -> null
        problems.isNotEmpty() && problems.all { it == PreviewProblem.NO_DOSE_RANGES } -> PreviewProblem.NO_DOSE_RANGES
        else -> PreviewProblem.NO_FULL_DURATION
    }
}

fun preselectedRoute(substance: Substance, experienceRoutesByRecency: List<AdministrationRoute>): AdministrationRoute? {
    val previewableRoutes = substance.roas.filter { it.previewProblem() == null }.map { it.route }
    return experienceRoutesByRecency.firstOrNull { it in previewableRoutes } ?: previewableRoutes.firstOrNull()
}

fun parseDose(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

fun RoaDose.rangesText(): String {
    val parts = listOfNotNull(
        if (lightMin != null && commonMin != null) "light ${lightMin.toReadableString()}–${commonMin.toReadableString()}" else null,
        if (commonMin != null && strongMin != null) "common ${commonMin.toReadableString()}–${strongMin.toReadableString()}" else null,
        if (strongMin != null && heavyMin != null) "strong ${strongMin.toReadableString()}–${heavyMin.toReadableString()}" else null,
        if (heavyMin != null) "heavy ${heavyMin.toReadableString()}+" else null,
    )
    return parts.joinToString(" · ") + " $units"
}
```

- [ ] **Step 4: Run the tests to verify that they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*SubstancePickerTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`, with 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Add the logic of the substance picker for planned ingestions

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Quick-log logic

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/QuickLog.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/QuickLogTest.kt`

**Interfaces:**
- Consumes (Task 1): `PlannedIngestion`.
- Produces:
  - `fun PlannedIngestion.toIngestion(experienceId: Int, now: Instant): Ingestion`
  - `fun companionToSave(substanceName: String, existing: SubstanceCompanion?, color: AdaptiveColor): SubstanceCompanion`
  - `fun interactionsWith(plannedSubstanceName: String, otherSubstanceNames: List<String>, lookup: (String, String) -> Interaction?): List<Interaction>`

- [ ] **Step 1: Write the failing tests**

Create `QuickLogTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.InteractionType
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.Interaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class QuickLogTest {

    private val now = Instant.parse("2026-01-01T21:34:00Z")
    private val planned = PlannedIngestion("Ketamine", AdministrationRoute.INSUFFLATED, 40.0, "mg")

    @Test
    fun ingestionIsTakenNowByYouInThisExperience() {
        val ingestion = planned.toIngestion(experienceId = 7, now = now)
        assertEquals(0, ingestion.id)
        assertEquals("Ketamine", ingestion.substanceName)
        assertEquals(AdministrationRoute.INSUFFLATED, ingestion.administrationRoute)
        assertEquals(40.0, ingestion.dose!!, 0.001)
        assertEquals("mg", ingestion.units)
        assertEquals(now, ingestion.time)
        assertNull(ingestion.endTime)
        assertEquals(now, ingestion.creationDate)
        assertEquals(false, ingestion.isDoseAnEstimate)
        assertNull(ingestion.estimatedDoseStandardDeviation)
        assertEquals(7, ingestion.experienceId)
        assertNull(ingestion.consumerName)
        assertNull(ingestion.notes)
        assertNull(ingestion.stomachFullness)
        assertNull(ingestion.customUnitId)
    }

    @Test
    fun existingCompanionIsKept() {
        val existing = SubstanceCompanion("Ketamine", AdaptiveColor.PINK)
        assertEquals(existing, companionToSave("Ketamine", existing, AdaptiveColor.BLUE))
    }

    @Test
    fun newCompanionUsesThePreviewColor() {
        assertEquals(SubstanceCompanion("Ketamine", AdaptiveColor.BLUE), companionToSave("Ketamine", null, AdaptiveColor.BLUE))
    }

    @Test
    fun interactionsListEachOtherSubstanceOnce() {
        val lookups = mutableListOf<Pair<String, String>>()
        val interactions = interactionsWith("Ketamine", listOf("MDMA", "Ketamine", "MDMA", "Cannabis")) { a, b ->
            lookups.add(Pair(a, b))
            if (b == "MDMA") Interaction(a, b, InteractionType.DANGEROUS) else null
        }
        assertEquals(listOf(Pair("Ketamine", "MDMA"), Pair("Ketamine", "Cannabis")), lookups)
        assertEquals(listOf(Interaction("Ketamine", "MDMA", InteractionType.DANGEROUS)), interactions)
    }
}
```

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuickLogTest*' --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3`
Expected: `Unresolved reference 'toIngestion'`.

- [ ] **Step 3: Create `QuickLog.kt`** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.Interaction
import java.time.Instant

// the planned ingestion as a real ingestion taken now by you in this experience
fun PlannedIngestion.toIngestion(experienceId: Int, now: Instant) = Ingestion(
    substanceName = substanceName,
    time = now,
    endTime = null,
    creationDate = now,
    administrationRoute = route,
    dose = dose,
    isDoseAnEstimate = false,
    estimatedDoseStandardDeviation = null,
    units = units,
    experienceId = experienceId,
    notes = null,
    stomachFullness = null,
    consumerName = null,
    customUnitId = null
)

fun companionToSave(substanceName: String, existing: SubstanceCompanion?, color: AdaptiveColor): SubstanceCompanion =
    existing ?: SubstanceCompanion(substanceName = substanceName, color = color)

fun interactionsWith(
    plannedSubstanceName: String,
    otherSubstanceNames: List<String>,
    lookup: (String, String) -> Interaction?
): List<Interaction> = otherSubstanceNames
    .distinct()
    .filter { it != plannedSubstanceName }
    .mapNotNull { lookup(plannedSubstanceName, it) }
```

- [ ] **Step 4: Run the tests to verify that they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuickLogTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`, with 4 tests passing. If `Ingestion`'s constructor doesn't accept one of these named parameters, check `data/room/experiences/entities/Ingestion.kt` and record a ruling.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Add the logic of quickly logging a planned ingestion

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Actions shared by both timeline view models

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestionActions.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceViewModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/screen/TimelineScreenViewModel.kt`

**Interfaces:**
- Consumes (Tasks 1-4): `PlannedIngestion`, `buildPlannedLine`, `choosePlannedColor`, `defaultPlannedIngestion`, `suggestedDose`, `buildPickerEntries`, `PickerEntry`, `toIngestion`, `companionToSave`, `interactionsWith`, `PlannedIngestion.toPinnedRedose`, `UserPreferences.pinnedRedoseFlow` and `savePinnedRedose`.
- Produces:

```kotlin
interface PlannedIngestionActions {
    val pinnedRedoseFlow: StateFlow<PinnedRedose?>
    fun pin(planned: PlannedIngestion?)
    fun getSubstance(substanceName: String): Substance?
    fun buildLine(planned: PlannedIngestion, time: Instant, colorsOnChart: List<AdaptiveColor>): DataForOneEffectLine?
    fun defaultPlan(realLines: List<DataForOneEffectLine>): PlannedIngestion?
    fun searchSubstances(searchText: String, experienceSubstanceNamesByRecency: List<String>): List<PickerEntry>
    fun companionColor(substanceName: String): AdaptiveColor?
    fun interactions(planned: PlannedIngestion, otherSubstanceNames: List<String>): List<Interaction>
    fun log(planned: PlannedIngestion, color: AdaptiveColor)
}
```

  and `val plannedIngestionActions: PlannedIngestionActions` on both `ExperienceViewModel` and `TimelineScreenViewModel`.

This task is wiring. It is covered by the unit tests of Tasks 1-4, and checked by compiling and in Task 6's manual check.

- [ ] **Step 1: Create `PlannedIngestionActions.kt`** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.repositories.SearchRepository
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.Interaction
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.InteractionChecker
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

// everything the planned-ingestion chip needs from outside the chart
interface PlannedIngestionActions {
    val pinnedRedoseFlow: StateFlow<PinnedRedose?>
    fun pin(planned: PlannedIngestion?)
    fun getSubstance(substanceName: String): Substance?
    fun buildLine(planned: PlannedIngestion, time: Instant, colorsOnChart: List<AdaptiveColor>): DataForOneEffectLine?
    fun defaultPlan(realLines: List<DataForOneEffectLine>): PlannedIngestion?
    fun searchSubstances(searchText: String, experienceSubstanceNamesByRecency: List<String>): List<PickerEntry>
    fun companionColor(substanceName: String): AdaptiveColor?
    fun interactions(planned: PlannedIngestion, otherSubstanceNames: List<String>): List<Interaction>
    fun log(planned: PlannedIngestion, color: AdaptiveColor)
}

class PlannedIngestionHelper @Inject constructor(
    private val substanceRepo: SubstanceRepository,
    private val searchRepo: SearchRepository,
    private val experienceRepo: ExperienceRepository,
    private val interactionChecker: InteractionChecker,
    private val userPreferences: UserPreferences,
) {
    fun actionsFor(experienceId: Int, scope: CoroutineScope): PlannedIngestionActions {
        val started = SharingStarted.WhileSubscribed(5000)
        val companions: StateFlow<List<SubstanceCompanion>> =
            experienceRepo.getAllSubstanceCompanionsFlow().stateIn(scope, started, emptyList())
        val recentNames: StateFlow<List<String>> =
            experienceRepo.getSortedLastUsedSubstanceNamesFlow(limit = 200).stateIn(scope, started, emptyList())
        val pinFlow: StateFlow<PinnedRedose?> = userPreferences.pinnedRedoseFlow
            .map { pin -> pin?.takeIf { it.experienceId == experienceId } }
            .stateIn(scope, started, null)

        return object : PlannedIngestionActions {
            override val pinnedRedoseFlow = pinFlow

            override fun pin(planned: PlannedIngestion?) {
                scope.launch {
                    userPreferences.savePinnedRedose(
                        planned?.toPinnedRedose(experienceId, currentPin = pinFlow.value, now = Instant.now())
                    )
                }
            }

            override fun getSubstance(substanceName: String) = substanceRepo.getSubstance(substanceName)

            override fun buildLine(planned: PlannedIngestion, time: Instant, colorsOnChart: List<AdaptiveColor>): DataForOneEffectLine? {
                val substance = substanceRepo.getSubstance(planned.substanceName) ?: return null
                val color = choosePlannedColor(planned.substanceName, companions.value, colorsOnChart)
                return buildPlannedLine(planned, substance, color, time)
            }

            override fun defaultPlan(realLines: List<DataForOneEffectLine>) =
                defaultPlannedIngestion(realLines) { substanceRepo.getSubstance(it) }

            override fun searchSubstances(searchText: String, experienceSubstanceNamesByRecency: List<String>): List<PickerEntry> {
                val matching = searchRepo.getMatchingSubstances(
                    searchText = searchText,
                    filterCategories = emptyList(),
                    recentlyUsedSubstanceNamesSorted = recentNames.value
                ).map { it.substance }
                return buildPickerEntries(matching, experienceSubstanceNamesByRecency)
            }

            override fun companionColor(substanceName: String) =
                companions.value.firstOrNull { it.substanceName == substanceName }?.color

            override fun interactions(planned: PlannedIngestion, otherSubstanceNames: List<String>) =
                interactionsWith(planned.substanceName, otherSubstanceNames, interactionChecker::getInteractionBetween)

            override fun log(planned: PlannedIngestion, color: AdaptiveColor) {
                scope.launch {
                    val existing = companions.value.firstOrNull { it.substanceName == planned.substanceName }
                    experienceRepo.insertIngestionAndCompanion(
                        ingestion = planned.toIngestion(experienceId, Instant.now()),
                        substanceCompanion = companionToSave(planned.substanceName, existing, color)
                    )
                }
            }
        }
    }
}
```

If `getSortedLastUsedSubstanceNamesFlow`, `getAllSubstanceCompanionsFlow` or `insertIngestionAndCompanion` have different names or parameter names in `ExperienceRepository`, adapt the call and record a ruling.

- [ ] **Step 2: Expose the actions in both view models**

In `ExperienceViewModel`:
1. Add `plannedIngestionHelper: PlannedIngestionHelper,` as the last constructor parameter, before `state: SavedStateHandle`.
2. Directly after the `init` block that sets `experienceId`, add:

```kotlin
    val plannedIngestionActions: PlannedIngestionActions =
        plannedIngestionHelper.actionsFor(experienceId, viewModelScope)
```

3. Remove the existing `pinnedRedoseFlow` property and `pinRedose` function (from the pin feature), and the imports they alone used: `RedoseOption` and `toPinnedRedose`.

In `TimelineScreenViewModel`, do the same: add the constructor parameter `plannedIngestionHelper: PlannedIngestionHelper`, then after the line `private val experienceID = timelineRoute.experienceId`, add:

```kotlin
    val plannedIngestionActions: PlannedIngestionActions =
        plannedIngestionHelper.actionsFor(experienceID, viewModelScope)
```

Remove its `pinnedRedoseFlow` and `pinRedose`, and the then-unused imports.

The screens still reference `viewModel.pinnedRedoseFlow` and `viewModel::pinRedose` until Task 6. To keep this task compiling, replace those references in `ExperienceScreen.kt` and `TimelineScreen.kt`:
- `viewModel.pinnedRedoseFlow.collectAsState().value` becomes `viewModel.plannedIngestionActions.pinnedRedoseFlow.collectAsState().value`;
- `viewModel::pinRedose` becomes:

```kotlin
{ option: RedoseOption? ->
    viewModel.plannedIngestionActions.pin(
        option?.let { PlannedIngestion(it.substanceName, it.route, it.dose, it.units) }
    )
}
```

Add the import of `PlannedIngestion` to both screens. This adapter is removed in Task 6.

- [ ] **Step 3: Check that the app compiles and all tests pass**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --continue --console=plain 2>&1 | grep -E "^e: |FAILED|tests completed|BUILD"`
Expected: no `e:` lines, and only the 3 known failures.

- [ ] **Step 4: Commit**

```bash
git add app/src
git commit -m "Share the planned-ingestion actions between both timeline screens

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The single chip, editor sheet and quick-log dialog

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestionUi.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceScreen.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/screen/TimelineScreen.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/AllTimelinesModel.kt` (remove the Task 2 compatibility code)
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PinnedRedose.kt` (remove `RedoseOption.toPinnedRedose`)
- Delete: `timeline/RedoseOption.kt`, `timeline/RedosePreviewRow.kt`, `timeline/RedosePreviewState.kt`

**Interfaces:**
- Consumes: `PlannedIngestionActions` (Task 5); everything from Tasks 1-4; `AllTimelinesModel.withPlannedIngestion(line)`, `realLines`, `colors`, `resolvePinnedPlan`, `plannedPeak` and `plannedFractionOfPeakAt` (Task 2).
- Produces:
  - `@Composable fun rememberPlannedIngestionState(realModel: AllTimelinesModel, actions: PlannedIngestionActions): PlannedIngestionState`
  - `@Composable fun PlannedIngestionSection(state: PlannedIngestionState, actions: PlannedIngestionActions, selectedTime: Instant?, modifier: Modifier = Modifier)`

UI is checked by building the app and by the manual check in Step 6. The logic it uses is unit-tested in Tasks 1-4.

- [ ] **Step 1: Create `PlannedIngestionUi.kt`** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import com.isaakhanimann.journal.ui.utils.getShortTimeText
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.roundToInt

class PlannedIngestionState(
    // the chart to draw: with the planned ingestion while held or pinned, otherwise the real one
    val displayedModel: AllTimelinesModel,
    val realModel: AllTimelinesModel,
    val planned: PlannedIngestion?,
    val plannedLine: DataForOneEffectLine?,
    val isPinned: Boolean,
    val isPossibleNow: Boolean,
    val now: Instant,
    val experienceSubstanceNamesByRecency: List<String>,
    val experienceRoutesByRecency: (String) -> List<AdministrationRoute>,
    val setHeld: (Boolean) -> Unit,
    val choose: (PlannedIngestion) -> Unit,
    val clearChoice: () -> Unit,
)

@Composable
fun rememberCurrentTime(): Instant {
    var currentTime by remember { mutableStateOf(Instant.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        currentTime = Instant.now()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            currentTime = Instant.now()
        }
    }
    return currentTime
}

@Composable
fun rememberPlannedIngestionState(realModel: AllTimelinesModel, actions: PlannedIngestionActions): PlannedIngestionState {
    val now = rememberCurrentTime()
    val pin = actions.pinnedRedoseFlow.collectAsState().value
    var chosen by remember { mutableStateOf<PlannedIngestion?>(null) }
    var isHeld by remember { mutableStateOf(false) }
    val realLines = realModel.realLines
    val defaultPlan = remember(realLines) { actions.defaultPlan(realLines) }
    val pinnedPlan = pin?.let { realModel.resolvePinnedPlan(it, now) }
    val planned = pinnedPlan ?: chosen ?: defaultPlan
    val plannedLine = planned?.let { actions.buildLine(it, now, realModel.colors) }
    val isPinInvalid = pin != null && (pinnedPlan == null || plannedLine == null)
    val currentActions by rememberUpdatedState(actions)
    LaunchedEffect(isPinInvalid) {
        if (isPinInvalid) currentActions.pin(null)
    }
    val isPreviewing = (isHeld || pinnedPlan != null) && plannedLine != null
    val displayedModel = if (isPreviewing) realModel.withPlannedIngestion(plannedLine!!) else realModel
    val linesByRecency = realLines.sortedByDescending { it.startTime }
    return PlannedIngestionState(
        displayedModel = displayedModel,
        realModel = realModel,
        planned = planned,
        plannedLine = plannedLine,
        isPinned = pinnedPlan != null,
        isPossibleNow = now < realModel.startTime.plusSeconds(realModel.widthInSeconds.toLong()),
        now = now,
        experienceSubstanceNamesByRecency = linesByRecency.map { it.substanceName }.distinct(),
        experienceRoutesByRecency = { name -> linesByRecency.filter { it.substanceName == name }.map { it.route }.distinct() },
        setHeld = { isHeld = it },
        choose = { newPlan ->
            chosen = newPlan
            if (pinnedPlan != null) actions.pin(newPlan)
        },
        clearChoice = { chosen = null },
    )
}

@Composable
fun PlannedIngestionSection(
    state: PlannedIngestionState,
    actions: PlannedIngestionActions,
    selectedTime: Instant?,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var isConfirmingLog by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Hold to preview taking now:", style = MaterialTheme.typography.labelMedium)
        PlannedIngestionChip(
            state = state,
            onEdit = { isEditing = true },
            onTogglePin = { actions.pin(if (state.isPinned) null else state.planned) },
            onTookIt = { isConfirmingLog = true }
        )
        // both lines are always laid out so that nothing moves when they appear
        val model = state.displayedModel
        val fractionLine = model.plannedFractionOfPeakAt(selectedTime ?: state.now)?.let {
            val time = selectedTime?.let { time -> "at ${time.getShortTimeText()}" } ?: "now"
            "With redose: ${(it * 100).roundToInt()}% of peak $time"
        }
        val peakLine = model.plannedPeak?.let {
            val route = it.route?.let { route -> " ${route.displayText.lowercase()}" } ?: ""
            "Peak after redose: ≈ ${it.doseEquivalent.toReadableString()} ${it.units}$route equivalent at ${it.time.getShortTimeText()}"
        }
        Text(
            text = listOfNotNull(fractionLine, peakLine).joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            minLines = 2
        )
    }
    if (isEditing) {
        PlannedIngestionEditorSheet(
            initial = state.planned,
            state = state,
            actions = actions,
            onDismiss = { isEditing = false },
            onDone = {
                state.choose(it)
                isEditing = false
            }
        )
    }
    val planned = state.planned
    if (isConfirmingLog && planned != null) {
        QuickLogDialog(
            planned = planned,
            color = state.plannedLine?.color ?: AdaptiveColor.entries.first(),
            now = state.now,
            interactions = actions.interactions(planned, state.experienceSubstanceNamesByRecency),
            onDismiss = { isConfirmingLog = false },
            onLog = {
                actions.log(planned, state.plannedLine?.color ?: AdaptiveColor.entries.first())
                state.clearChoice()
                isConfirmingLog = false
            }
        )
    }
}

@Composable
private fun PlannedIngestionChip(
    state: PlannedIngestionState,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onTookIt: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val planned = state.planned
    val currentSetHeld by rememberUpdatedState(state.setHeld)
    val canPreview = planned != null && state.plannedLine != null
    var isPressed by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = if (isPressed || state.isPinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pointerInput(canPreview) {
                        if (!canPreview) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                currentSetHeld(true)
                                tryAwaitRelease()
                                isPressed = false
                                currentSetHeld(false)
                            }
                        )
                    }
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
            ) {
                val color = state.plannedLine?.color
                if (color != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color.getComposeColor(isDarkTheme), CircleShape)
                    )
                }
                Text(
                    text = planned?.chipText() ?: "Choose a substance to preview",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            if (canPreview) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (state.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (state.isPinned) "Unpin preview" else "Pin preview",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "Change planned ingestion", modifier = Modifier.size(18.dp))
            }
            if (canPreview) {
                IconButton(onClick = onTookIt, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "I took it now", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlannedIngestionEditorSheet(
    initial: PlannedIngestion?,
    state: PlannedIngestionState,
    actions: PlannedIngestionActions,
    onDismiss: () -> Unit,
    onDone: (PlannedIngestion) -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    var substanceName by remember { mutableStateOf(initial?.substanceName) }
    var route by remember { mutableStateOf(initial?.route) }
    var doseText by remember { mutableStateOf(initial?.dose?.toReadableString() ?: "") }
    var searchText by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val selectedName = substanceName
            val substance = selectedName?.let { actions.getSubstance(it) }
            if (selectedName == null || substance == null) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search substance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val entries = actions.searchSubstances(searchText, state.experienceSubstanceNamesByRecency)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    val inExperience = entries.filter { it.isInExperience }
                    val others = entries.filter { !it.isInExperience }
                    if (inExperience.isNotEmpty()) {
                        item { Text("In this experience", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp)) }
                        items(inExperience) { entry -> PickerRow(entry, actions.companionColor(entry.substanceName), isDarkTheme) { pick(entry, actions, state) { name, newRoute, dose -> substanceName = name; route = newRoute; doseText = dose } } }
                    }
                    if (others.isNotEmpty()) {
                        item { Text("All substances", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp)) }
                        items(others) { entry -> PickerRow(entry, actions.companionColor(entry.substanceName), isDarkTheme) { pick(entry, actions, state) { name, newRoute, dose -> substanceName = name; route = newRoute; doseText = dose } } }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { substanceName = null }) { Text("Change") }
                }
                Text("Route", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    substance.roas.forEach { roa ->
                        val problem = roa.previewProblem()
                        FilterChip(
                            selected = route == roa.route,
                            enabled = problem == null,
                            onClick = {
                                route = roa.route
                                val roaDose = roa.roaDose
                                if (roaDose != null) {
                                    doseText = suggestedDose(selectedName, roa.route, state.realModel.realLines, roaDose)?.toReadableString() ?: ""
                                }
                            },
                            label = {
                                Column {
                                    Text(roa.route.displayText)
                                    when (problem) {
                                        PreviewProblem.NO_FULL_DURATION -> Text("no full duration data", style = MaterialTheme.typography.labelSmall)
                                        PreviewProblem.NO_DOSE_RANGES -> Text("no dose ranges", style = MaterialTheme.typography.labelSmall)
                                        null -> {}
                                    }
                                }
                            }
                        )
                    }
                }
                val roaDose = route?.let { substance.getRoa(it)?.roaDose }
                OutlinedTextField(
                    value = doseText,
                    onValueChange = { doseText = it },
                    label = { Text("Dose") },
                    suffix = { Text(roaDose?.units ?: "") },
                    isError = parseDose(doseText) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (roaDose != null) {
                    Text(roaDose.rangesText(), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    val dose = parseDose(doseText)
                    val selectedRoute = route
                    Button(
                        enabled = dose != null && selectedRoute != null && roaDose != null,
                        onClick = {
                            if (dose != null && selectedRoute != null && roaDose != null) {
                                onDone(PlannedIngestion(selectedName, selectedRoute, dose, roaDose.units))
                            }
                        }
                    ) { Text("Done") }
                }
            }
        }
    }
}

// selecting a substance pre-selects its route and pre-fills the dose
private fun pick(
    entry: PickerEntry,
    actions: PlannedIngestionActions,
    state: PlannedIngestionState,
    apply: (String, AdministrationRoute?, String) -> Unit,
) {
    val substance = actions.getSubstance(entry.substanceName) ?: return
    val route = preselectedRoute(substance, state.experienceRoutesByRecency(entry.substanceName))
    val roaDose = route?.let { substance.getRoa(it)?.roaDose }
    val dose = roaDose?.let { suggestedDose(entry.substanceName, route, state.realModel.realLines, it) }
    apply(entry.substanceName, route, dose?.toReadableString() ?: "")
}

@Composable
private fun PickerRow(entry: PickerEntry, color: AdaptiveColor?, isDarkTheme: Boolean, onClick: () -> Unit) {
    val isEnabled = entry.problem == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color?.getComposeColor(isDarkTheme) ?: MaterialTheme.colorScheme.surface, CircleShape)
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            val textColor = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            Text(entry.substanceName, color = textColor)
            when (entry.problem) {
                PreviewProblem.NO_FULL_DURATION -> Text("Can't be previewed: no full duration data for any route", style = MaterialTheme.typography.labelSmall, color = textColor)
                PreviewProblem.NO_DOSE_RANGES -> Text("Can't be previewed: no dose ranges for any route", style = MaterialTheme.typography.labelSmall, color = textColor)
                null -> {}
            }
        }
    }
}

@Composable
private fun QuickLogDialog(
    planned: PlannedIngestion,
    color: AdaptiveColor,
    now: Instant,
    interactions: List<com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions.Interaction>,
    onDismiss: () -> Unit,
    onLog: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log ingestion now?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(color.getComposeColor(isDarkTheme), CircleShape))
                    Text(planned.chipText(), modifier = Modifier.padding(start = 6.dp))
                }
                Text("now (${now.getShortTimeText()}) · this experience · you", style = MaterialTheme.typography.bodySmall)
                interactions.forEach { interaction ->
                    Text(
                        "⚠ ${interaction.interactionType.name.lowercase().replaceFirstChar { it.uppercase() }} interaction with ${interaction.bName}",
                        color = interaction.interactionType.color
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onLog) { Text("Log") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

- [ ] **Step 2: Use the section in `ExperienceScreen.kt`**

1. In the public `ExperienceScreen(viewModel, ...)`, replace

```kotlin
        pinnedRedose = viewModel.plannedIngestionActions.pinnedRedoseFlow.collectAsState().value,
        onPinRedose = <the adapter from Task 5>
```

with `plannedIngestionActions = viewModel.plannedIngestionActions`.

2. In the inner `ExperienceScreen(...)` signature and in `MyTimelineSection(...)`, replace the parameters `pinnedRedose: PinnedRedose? = null, onPinRedose: (RedoseOption?) -> Unit = {},` (`MyTimelineSection` has them without defaults) with `plannedIngestionActions: PlannedIngestionActions? = null` (`MyTimelineSection`: `plannedIngestionActions: PlannedIngestionActions?`). Pass it from the one to the other.

3. In `MyTimelineSection`, replace the block from `var previewedOption by remember ...` up to the end of the `if (previewState.isRedosePossibleNow) { RedosePreviewRow(...) }` block with:

```kotlin
                    val planState = plannedIngestionActions?.let { rememberPlannedIngestionState(timelineModel, it) }
                    val displayedModel = planState?.displayedModel ?: timelineModel
                    AllTimelines(
                        model = displayedModel,
                        timeDisplayOption = timeDisplayOption,
                        isShowingCurrentTime = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        onSelectedTimeChange = { selectedTime = it }
                    )
                    // the details always describe the real ingestions, the planned one is described below the chip
                    TimelineQuantityDetails(model = timelineModel, selectedTime = selectedTime)
                    if (planState != null && plannedIngestionActions != null && planState.isPossibleNow) {
                        PlannedIngestionSection(
                            state = planState,
                            actions = plannedIngestionActions,
                            selectedTime = selectedTime,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
```

4. Fix the imports. Remove `RedoseOption`, `RedosePreviewRow`, `rememberRedosePreviewState` and `PinnedRedose`, and add `PlannedIngestionActions`, `PlannedIngestionSection` and `rememberPlannedIngestionState` from `...experience.timeline`.

- [ ] **Step 3: Use the section in `TimelineScreen.kt`**

1. In the public `TimelineScreen(viewModel)`, replace the `pinnedRedose` and `onPinRedose` arguments with `plannedIngestionActions = if (viewModel.consumerName == YOU) viewModel.plannedIngestionActions else null,`, and remove the `isRedosePreviewAvailable` argument.
2. In the inner signature, replace `isRedosePreviewAvailable: Boolean, pinnedRedose: PinnedRedose? = null, onPinRedose: (RedoseOption?) -> Unit = {},` with `plannedIngestionActions: PlannedIngestionActions? = null,`.
3. Replace the block from `var previewedOption by remember ...` through `val displayedModel = previewState?.displayedModel` with:

```kotlin
            val realModel = (timelineDisplayOption as? TimelineDisplayOption.Shown)?.allTimelinesModel
            val planState = if (realModel != null && plannedIngestionActions != null) {
                rememberPlannedIngestionState(realModel, plannedIngestionActions)
            } else {
                null
            }
            val displayedModel = planState?.displayedModel ?: realModel
```

4. In the `AllTimelines(` call, keep `model = displayedModel ?: timelineDisplayOption.allTimelinesModel`.
5. Replace the `if (isRedosePreviewAvailable && previewState?.isRedosePossibleNow == true) { RedosePreviewRow(...) }` block with:

```kotlin
                if (planState != null && plannedIngestionActions != null && planState.isPossibleNow) {
                    PlannedIngestionSection(
                        state = planState,
                        actions = plannedIngestionActions,
                        selectedTime = selectedTime,
                        modifier = Modifier.padding(horizontal = horizontalPadding)
                    )
                }
```

6. Fix the imports as in Step 2, and remove the now-unused `RedoseOption` and `PinnedRedose` imports.

- [ ] **Step 4: Remove the old chip code**

- Delete `timeline/RedoseOption.kt`, `timeline/RedosePreviewRow.kt` and `timeline/RedosePreviewState.kt`.
- In `AllTimelinesModel.kt`, delete `redoseOptions`, `resolvePinnedRedose` and `withPlannedIngestion(option, dose, time)`, i.e. everything marked `// removed in Task 6`.
- In `PinnedRedose.kt`, delete `RedoseOption.toPinnedRedose`.

`grep -rn "RedoseOption\|redoseOptions\|RedosePreviewRow\|rememberRedosePreviewState" app/src` must return nothing.

- [ ] **Step 5: Build, run all tests and install**

Run:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --continue --console=plain 2>&1 | grep -E "^e: |FAILED|tests completed|BUILD"
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: no `e:` lines, only the 3 known test failures, and `Success`.

- [ ] **Step 6: Manual check with the user on the emulator**

1. In an experience with ketamine insufflated 40 mg logged, the single chip reads `● Ketamine · 40 mg insufflated` with 📌 ✎ ✓. Holding it shows the solid and dashed preview.
2. Tap ✎. The list shows "In this experience" (Ketamine), then "All substances". Searching "mdm" finds MDMA. Substances that can't be previewed are greyed out with the reason.
3. Choose MDMA, oral. The dose is pre-filled with the start of the common range, and the ranges line is shown. Done. Holding the chip now shows a separate MDMA line, so the overlap is visible.
4. Pin it, leave and return: still pinned.
5. Tap ✓. The dialog shows the substance and time, and the interaction warning with ketamine if the database has one. Log. The MDMA ingestion appears, the pin is gone, and the chip default is now MDMA.
6. The full-screen timeline shows the same chip.

- [ ] **Step 7: Commit**

```bash
git add -A app/src
git commit -m "Replace the redose chips with one planned-ingestion chip with editor and quick log

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
