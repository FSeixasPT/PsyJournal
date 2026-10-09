# Live Timeline Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An ongoing notification that follows the current experience's estimated timeline (text per substance, mini chart, pinned plan) with Add ingestion, Log planned and Stop actions, started from the experience screen or automatically after logging.

**Architecture:** Pure Kotlin builds what to show from the existing `AllTimelinesModel` (phases from each substance's summed line, hints, texts) and is unit-tested. A `@Singleton` controller loads the live experience from Room, posts the notification through a notifier (RemoteViews + a chart drawn with `CanvasDrawScope`), and schedules inexact alarms that a broadcast receiver turns into refreshes. Navigation from the notification goes through `MainActivity` intent extras.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore Preferences, NotificationCompat, AlarmManager, JUnit4 JVM tests.

**Spec:** `docs/superpowers/specs/2026-10-09-live-notification-design.md`

## Global Constraints

- Build with JDK 21: `export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"` (Git Bash). Android Studio's bundled Java 25 fails.
- Unit tests: `./gradlew :app:testDebugUnitTest -q` (whole suite) or `--tests '*Name*'`. Three tests already fail before this work and stay failing: `TestDates.dateDifferences`, `TestParse.testExtractSubstancesString`, `TestParse.noCrash`. "Suite green" means everything except these three.
- Results of a run: `app/build/test-results/testDebugUnitTest/*.xml` (`grep -l "<failure"` lists failing classes).
- minSdk 26, targetSdk 35. `POST_NOTIFICATIONS` is only requested on Android 13+ (API 33).
- Every new Kotlin file starts with the 17-line GPL header copied verbatim from an existing file, e.g. the first 17 lines of `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TimelineQuantityDetail.kt`.
- Nothing is copied from the closed-source official app: no code, texts, assets or layouts.
- Code style: match the surrounding code; short comments that say why, as in `PlannedTimeText.kt`.
- Commit messages end with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Work on branch `live-notification`.
- Paths below are relative to `app/src/main/java/com/isaakhanimann/journal/` (main) and `app/src/test/java/com/isaakhanimann/journal/` (test) unless they start with `app/` or `docs/`.
- New package for the feature: `ui/tabs/journal/experience/livenotification/` (`com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification`).

## Review Focus

1. The live experience is deleted, or its id is reused by a new experience: the notification disappears and never shows the other experience. Pinned by `RefreshDecisionTest` (Task 7).
2. Notifications are blocked or the permission is revoked after starting: the state is cleared instead of retrying forever. Pinned by `RefreshDecisionTest.blockedNotificationsStop` (Task 7).
3. A substance whose durations have no offset and no total: it must not keep the notification alive forever. Pinned by `FallbackPhaseTest.unknownEndCountsAsEndedAfter12Hours` (Task 3).
4. More substances than fit the collapsed view: three lines and "+N more". Pinned by `LiveNotificationContentTest.collapsedShowsThreeLinesAndCountsTheRest` (Task 4).
5. Auto-start for ingestions logged in the past or the future: only within the last 60 minutes or the next 5. Pinned by `AutoStartTest` (Task 10).

---

### Task 1: Expose each substance's summed line from `AllTimelinesModel`

**Files:**
- Create: `ui/tabs/journal/experience/timeline/SubstanceCurve.kt`
- Modify: `ui/tabs/journal/experience/timeline/AllTimelinesModel.kt` (add `substanceCurves()` after `getQuantityDetails`)
- Test: `ui/tabs/journal/experience/timeline/SubstanceCurveTest.kt`

**Interfaces:**
- Produces:
  - `data class SubstanceCurve(val substanceName: String, val routes: List<AdministrationRoute>, val color: AdaptiveColor, val line: CurveLine?)`
  - `class CurveLine { val maxHeight: Float; val endTime: Instant; fun heightAt(time: Instant): Float }`
  - `fun AllTimelinesModel.substanceCurves(): List<SubstanceCurve>`, in the order the lines are drawn.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SubstanceCurveTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)

    // onset 30m, comeup 30m, peak 60m, offset 60m
    private val exactDuration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null)

    private fun line(route: AdministrationRoute, minutesAfterStart: Long, roaDuration: RoaDuration = exactDuration) =
        DataForOneEffectLine(
            substanceName = "TestSubstance",
            route = route,
            roaDuration = roaDuration,
            height = 1f,
            horizontalWeight = 0.5f,
            color = AdaptiveColor.BLUE,
            startTime = at(minutesAfterStart),
            endTime = null,
        )

    private fun model(vararg lines: DataForOneEffectLine, areRoutesMerged: Boolean = false) = AllTimelinesModel(
        dataForLines = lines.toList(),
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false,
        areRoutesMerged = areRoutesMerged
    )

    @Test
    fun aFullLineGivesItsHeightOverTime() {
        val curve = model(line(AdministrationRoute.ORAL, 0)).substanceCurves().single()
        val line = curve.line!!
        assertEquals("TestSubstance", curve.substanceName)
        assertEquals(listOf(AdministrationRoute.ORAL), curve.routes)
        assertEquals(0f, line.heightAt(at(20)), 0.001f)
        assertEquals(0.5f, line.heightAt(at(45)) / line.maxHeight, 0.001f)
        assertEquals(1f, line.heightAt(at(90)) / line.maxHeight, 0.001f)
        assertEquals(at(180), line.endTime)
    }

    @Test
    fun unmergedRoutesAreSeparateCurves() {
        val curves = model(line(AdministrationRoute.ORAL, 0), line(AdministrationRoute.INSUFFLATED, 10)).substanceCurves()
        assertEquals(listOf(listOf(AdministrationRoute.ORAL), listOf(AdministrationRoute.INSUFFLATED)), curves.map { it.routes })
    }

    @Test
    fun aDurationWithoutOffsetHasNoLine() {
        val withoutOffset = exactDuration.copy(offset = null)
        val curve = model(line(AdministrationRoute.ORAL, 0, withoutOffset)).substanceCurves().single()
        assertNull(curve.line)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SubstanceCurveTest*' -q`
Expected: compilation error "Unresolved reference 'substanceCurves'".

- [ ] **Step 3: Implement**

`ui/tabs/journal/experience/timeline/SubstanceCurve.kt` (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.FullTimelines
import java.time.Duration
import java.time.Instant

// a line of the chart: a substance, or the merged routes of a substance
data class SubstanceCurve(
    val substanceName: String,
    val routes: List<AdministrationRoute>,
    val color: AdaptiveColor,
    // null if the durations are too incomplete to draw the summed line
    val line: CurveLine?,
)

// the summed line of all ingestions of a curve, as drawn on the chart
class CurveLine(private val startTime: Instant, private val fullTimelines: FullTimelines) {
    val maxHeight: Float get() = fullTimelines.nonNormalisedHeight

    val endTime: Instant get() = startTime.plusSeconds(fullTimelines.endOfLineRelativeToStartInSeconds.toLong())

    fun heightAt(time: Instant): Float =
        fullTimelines.heightAt(Duration.between(startTime, time).toMillis() / 1000f)
}
```

In `AllTimelinesModel.kt`, after `getQuantityDetails(...)`:

```kotlin
    fun substanceCurves(): List<SubstanceCurve> = roaGroups.zip(groupDrawables).map { (group, drawable) ->
        SubstanceCurve(
            substanceName = group.substanceName,
            routes = group.routes.map { it.route },
            color = group.color,
            line = drawable.fullTimelines?.let { CurveLine(startTime, it) }
        )
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*SubstanceCurveTest*' -q`
Expected: 3 tests pass. If `aFullLineGivesItsHeightOverTime` fails only on `endTime`, check what `endOfLineRelativeToStartInSeconds` measures in `FullTimelines.kt` and use the x of the last point instead (ledger a ruling).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/SubstanceCurve.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/AllTimelinesModel.kt app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/SubstanceCurveTest.kt
git commit -m "Expose the summed line of each substance from the timeline model"
```

---

### Task 2: Phases and hints from a summed line

**Files:**
- Create: `ui/tabs/journal/experience/livenotification/CurvePhase.kt`
- Test: `ui/tabs/journal/experience/livenotification/CurvePhaseTest.kt`

**Interfaces:**
- Consumes: `CurveLine` (Task 1).
- Produces:
  - `enum class Phase { WAITING_FOR_ONSET, COMING_UP, PEAKING, STEADY, COMING_DOWN, ACTIVE, ENDED, NO_TIMELINE }`
  - `enum class HintKind { STARTS, PEAKS, COMES_DOWN, RISES_AGAIN, ENDS }`
  - `data class TimeHint(val kind: HintKind, val time: Instant)`
  - `data class PhaseWithHint(val phase: Phase, val hint: TimeHint?)`
  - `val Phase.word: String`
  - `val HintKind.word: String`
  - `fun CurveLine.phaseAt(time: Instant): Phase`
  - `fun CurveLine.phaseWithHint(now: Instant): PhaseWithHint`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.CurveLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class CurvePhaseTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)

    // onset 30m, comeup 30m, peak 60m, offset 60m: rises 20:30-21:00, peaks until 22:00, ends 23:00
    private val exactDuration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null)

    private fun dose(minutesAfterStart: Long, height: Float) = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = exactDuration,
        height = height,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = at(minutesAfterStart),
        endTime = null,
    )

    private fun curve(vararg doses: DataForOneEffectLine): CurveLine = AllTimelinesModel(
        dataForLines = doses.toList(),
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = false
    ).substanceCurves().single().line!!

    private fun assertHint(kind: HintKind, expected: Instant, actual: PhaseWithHint) {
        val hint = actual.hint!!
        assertEquals(kind, hint.kind)
        assertTrue("$expected vs ${hint.time}", Duration.between(expected, hint.time).abs() <= Duration.ofMinutes(1))
    }

    private val single get() = curve(dose(0, 1f))

    @Test
    fun beforeTheOnsetEndsItIsWaiting() {
        val result = single.phaseWithHint(at(10))
        assertEquals(Phase.WAITING_FOR_ONSET, result.phase)
        assertHint(HintKind.STARTS, at(30), result)
    }

    @Test
    fun duringTheComeupItIsComingUp() {
        val result = single.phaseWithHint(at(45))
        assertEquals(Phase.COMING_UP, result.phase)
        assertHint(HintKind.PEAKS, at(60), result)
    }

    @Test
    fun duringThePeakItIsPeaking() {
        val result = single.phaseWithHint(at(90))
        assertEquals(Phase.PEAKING, result.phase)
        assertHint(HintKind.COMES_DOWN, at(120), result)
    }

    @Test
    fun duringTheOffsetItIsComingDown() {
        val result = single.phaseWithHint(at(150))
        assertEquals(Phase.COMING_DOWN, result.phase)
        assertHint(HintKind.ENDS, at(180), result)
    }

    @Test
    fun afterTheOffsetItHasEnded() {
        val result = single.phaseWithHint(at(210))
        assertEquals(Phase.ENDED, result.phase)
        assertNull(result.hint)
    }

    @Test
    fun aSmallTopUpDuringTheComedownStillComesDown() {
        // the top-up rises 22:30-23:00 while the first dose falls faster
        val result = curve(dose(0, 1f), dose(120, 0.1f)).phaseWithHint(at(160))
        assertEquals(Phase.COMING_DOWN, result.phase)
        // the top-up's peak until 00:00 is flat and its offset ends at 01:00
        assertHint(HintKind.ENDS, at(300), result)
    }

    @Test
    fun aTopUpThatLiftsTheLineRisesAgain() {
        // the second dose rises 22:15-22:45 while the first falls 22:00-23:00
        val line = curve(dose(0, 1f), dose(105, 1f))
        val beforeTheRise = line.phaseWithHint(at(125))
        assertEquals(Phase.COMING_DOWN, beforeTheRise.phase)
        assertHint(HintKind.RISES_AGAIN, at(135), beforeTheRise)
        assertEquals(Phase.COMING_UP, line.phaseAt(at(140)))
    }

    @Test
    fun aLowFlatLineIsSteady() {
        // after the first dose ends at 23:00 the second dose's peak stays at 0.3 until 00:00
        val result = curve(dose(0, 1f), dose(120, 0.3f)).phaseWithHint(at(210))
        assertEquals(Phase.STEADY, result.phase)
        assertHint(HintKind.COMES_DOWN, at(240), result)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*CurvePhaseTest*' -q`
Expected: compilation errors "Unresolved reference 'Phase'" / "'phaseWithHint'".

- [ ] **Step 3: Implement** `livenotification/CurvePhase.kt` (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.CurveLine
import java.time.Duration
import java.time.Instant
import kotlin.math.max

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
    // 5 % per 10 minutes of the current height, but at least of a tenth of the highest point
    val threshold = 0.01f * max(height, 0.1f * maxHeight)
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
                else -> endOfRiseAfter(change)?.let { TimeHint(HintKind.PEAKS, it) }
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*CurvePhaseTest*' -q`
Expected: 8 tests pass. If a top-up test fails, print `line.heightAt(...)` around the asserted time in a scratch test to check that the chart's summed line adds the doses. If it doesn't, ledger a ruling and adjust only the expected times, never the thresholds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/CurvePhase.kt app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/CurvePhaseTest.kt
git commit -m "Work out the live notification phases from the summed line"
```

---

### Task 3: Phases for substances without a summed line

**Files:**
- Create: `ui/tabs/journal/experience/livenotification/FallbackPhase.kt`
- Test: `ui/tabs/journal/experience/livenotification/FallbackPhaseTest.kt`

**Interfaces:**
- Consumes: `Phase`, `HintKind`, `TimeHint`, `PhaseWithHint` (Task 2).
- Produces:
  - `data class FallbackResult(val phaseWithHint: PhaseWithHint, val knownEnd: Instant?)`
  - `fun fallbackPhase(lines: List<DataForOneEffectLine>, now: Instant): FallbackResult`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class FallbackPhaseTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)

    // onset 30m, comeup 30m, peak 60m, no offset
    private val withoutOffset = RoaDuration(minutes(30f), minutes(30f), minutes(60f), null, null, null)

    private fun line(minutesAfterStart: Long, duration: RoaDuration?, endMinutes: Long? = null) = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = duration,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = at(minutesAfterStart),
        endTime = endMinutes?.let { at(it) },
    )

    @Test
    fun knownPhasesHaveHints() {
        val result = fallbackPhase(listOf(line(0, withoutOffset)), at(90)).phaseWithHint
        assertEquals(PhaseWithHint(Phase.PEAKING, TimeHint(HintKind.COMES_DOWN, at(120))), result)
    }

    @Test
    fun afterTheLastKnownBoundaryThePhaseHasNoHint() {
        val result = fallbackPhase(listOf(line(0, withoutOffset)), at(150))
        assertEquals(PhaseWithHint(Phase.COMING_DOWN, null), result.phaseWithHint)
        assertNull(result.knownEnd)
    }

    @Test
    fun unknownEndCountsAsEndedAfter12Hours() {
        val result = fallbackPhase(listOf(line(0, withoutOffset)), at(12 * 60 + 1))
        assertEquals(Phase.ENDED, result.phaseWithHint.phase)
    }

    @Test
    fun noDurationsMeanNoTimeline() {
        assertEquals(Phase.NO_TIMELINE, fallbackPhase(listOf(line(0, null)), at(10)).phaseWithHint.phase)
    }

    @Test
    fun onlyATotalDurationIsActiveUntilItEnds() {
        val totalOnly = RoaDuration(null, null, null, null, minutes(240f), null)
        val during = fallbackPhase(listOf(line(0, totalOnly)), at(60))
        assertEquals(PhaseWithHint(Phase.ACTIVE, TimeHint(HintKind.ENDS, at(240))), during.phaseWithHint)
        assertEquals(at(240), during.knownEnd)
        assertEquals(Phase.ENDED, fallbackPhase(listOf(line(0, totalOnly)), at(250)).phaseWithHint.phase)
    }

    @Test
    fun theMostRecentActiveIngestionDecides() {
        val result = fallbackPhase(listOf(line(0, withoutOffset), line(120, withoutOffset)), at(135)).phaseWithHint
        assertEquals(PhaseWithHint(Phase.WAITING_FOR_ONSET, TimeHint(HintKind.STARTS, at(150))), result)
    }

    @Test
    fun aTimeRangePeaksUntilItsEndPlusThePeak() {
        val result = fallbackPhase(listOf(line(0, withoutOffset, endMinutes = 90)), at(135)).phaseWithHint
        assertEquals(PhaseWithHint(Phase.PEAKING, TimeHint(HintKind.COMES_DOWN, at(150))), result)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*FallbackPhaseTest*' -q`
Expected: compilation error "Unresolved reference 'fallbackPhase'".

- [ ] **Step 3: Implement** `livenotification/FallbackPhase.kt` (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullDurationRange
import java.time.Duration
import java.time.Instant

data class FallbackResult(val phaseWithHint: PhaseWithHint, val knownEnd: Instant?)

// a substance with an unknown end counts as ended this long after its ingestion
private val unknownEndAfter: Duration = Duration.ofHours(12)

private class FallbackTimes(
    val start: Instant,
    val onsetEnd: Instant?,
    val comeupEnd: Instant?,
    val peakEnd: Instant?,
    val offsetEnd: Instant?,
    val totalEnd: Instant?,
) {
    val knownEnd: Instant? get() = offsetEnd ?: totalEnd
    val end: Instant get() = knownEnd ?: (start + unknownEndAfter)

    fun phaseWithHint(now: Instant): PhaseWithHint {
        if (now >= end) return PhaseWithHint(Phase.ENDED, null)
        if (onsetEnd == null) return PhaseWithHint(Phase.ACTIVE, totalEnd?.let { TimeHint(HintKind.ENDS, it) })
        val steps = listOf(
            Triple(Phase.WAITING_FOR_ONSET, onsetEnd, HintKind.STARTS),
            Triple(Phase.COMING_UP, comeupEnd, HintKind.PEAKS),
            Triple(Phase.PEAKING, peakEnd, HintKind.COMES_DOWN),
            Triple(Phase.COMING_DOWN, offsetEnd, HintKind.ENDS),
        )
        for ((phase, boundary, kind) in steps) {
            // the last phase that can be worked out
            if (boundary == null) return PhaseWithHint(phase, totalEnd?.let { TimeHint(HintKind.ENDS, it) })
            if (now < boundary) return PhaseWithHint(phase, TimeHint(kind, boundary))
        }
        return PhaseWithHint(Phase.ENDED, null)
    }
}

// the middle of a duration range, the same weight the chart uses for onset and comeup
private fun DurationRange?.middleInSeconds(): Long? =
    this?.toFullDurationRange()?.interpolateAtValueInSeconds(0.5f)?.toLong()

private fun DataForOneEffectLine.fallbackTimes(): FallbackTimes? {
    val duration = roaDuration ?: return null
    val onsetEnd = duration.onset.middleInSeconds()?.let { startTime.plusSeconds(it) }
    val comeupEnd = onsetEnd?.let { onset -> duration.comeup.middleInSeconds()?.let { onset.plusSeconds(it) } }
    // a time range keeps peaking until its end plus the peak duration
    val peakStart = comeupEnd?.let { comeup -> endTime?.let { maxOf(comeup, it) } ?: comeup }
    val peakEnd = peakStart?.let { peak -> duration.peak.middleInSeconds()?.let { peak.plusSeconds(it) } }
    val offsetEnd = peakEnd?.let { peak -> duration.offset.middleInSeconds()?.let { peak.plusSeconds(it) } }
    val totalEnd = duration.total.middleInSeconds()?.let { (endTime ?: startTime).plusSeconds(it) }
    if (onsetEnd == null && totalEnd == null) return null
    return FallbackTimes(startTime, onsetEnd, comeupEnd, peakEnd, offsetEnd, totalEnd)
}

// for substances whose durations are too incomplete for a summed line: the most recent ingestion that hasn't ended decides
fun fallbackPhase(lines: List<DataForOneEffectLine>, now: Instant): FallbackResult {
    val timed = lines.mapNotNull { it.fallbackTimes() }
    if (timed.isEmpty()) return FallbackResult(PhaseWithHint(Phase.NO_TIMELINE, null), null)
    val active = timed.filter { now < it.end }.maxByOrNull { it.start }
        ?: return FallbackResult(PhaseWithHint(Phase.ENDED, null), null)
    return FallbackResult(active.phaseWithHint(now), active.knownEnd)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*FallbackPhaseTest*' -q`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/FallbackPhase.kt app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/FallbackPhaseTest.kt
git commit -m "Work out phases for substances with incomplete durations"
```

---

### Task 4: The notification content

**Files:**
- Create: `ui/tabs/journal/experience/livenotification/LiveNotificationContent.kt`
- Test: `ui/tabs/journal/experience/livenotification/LiveNotificationContentTest.kt`

**Interfaces:**
- Consumes:
  - `substanceCurves()` (Task 1);
  - `phaseWithHint`, `word` (Task 2);
  - `fallbackPhase` (Task 3);
  - existing `plannedTimeText(time, now, experienceStart, option)`;
  - existing `PlannedIngestion.chipText()`;
  - existing `SavedTimeDisplayOption`, `TimeDisplayOption`.
- Produces:
  - `data class SubstanceLine(val label: String, val color: AdaptiveColor, val phase: Phase, val percentOfPeak: Int?, val hint: TimeHint?, val endsAt: Instant?, val text: String)` with `val isActive: Boolean`
  - `data class LiveNotificationContent(val title: String, val lines: List<SubstanceLine>, val plannedLine: String?, val endsAt: Instant?, val isFinished: Boolean, val nextRefreshAt: Instant)` with `fun collapsedLines(max: Int = 3): Pair<List<SubstanceLine>, Int>`
  - `fun liveTimeDisplayOption(saved: SavedTimeDisplayOption): TimeDisplayOption`
  - `fun buildLiveNotificationContent(title: String, model: AllTimelinesModel, lines: List<DataForOneEffectLine>, plannedIngestion: PlannedIngestion?, savedTimeDisplayOption: SavedTimeDisplayOption, now: Instant): LiveNotificationContent`
  - `fun hasActiveEffects(lines: List<DataForOneEffectLine>, areSubstanceHeightsIndependent: Boolean, areRoutesMerged: Boolean, now: Instant): Boolean`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.PlannedIngestion
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.chipText
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.plannedTimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class LiveNotificationContentTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)
    private val exactDuration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null)

    private fun line(
        name: String = "TestSubstance",
        route: AdministrationRoute = AdministrationRoute.ORAL,
        minutesAfterStart: Long = 0,
        duration: RoaDuration? = exactDuration,
    ) = DataForOneEffectLine(
        substanceName = name,
        route = route,
        roaDuration = duration,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = at(minutesAfterStart),
        endTime = null,
    )

    private fun content(
        lines: List<DataForOneEffectLine>,
        now: Instant,
        option: SavedTimeDisplayOption = SavedTimeDisplayOption.AUTO,
        planned: PlannedIngestion? = null,
        areRoutesMerged: Boolean = false,
    ) = buildLiveNotificationContent(
        title = "Experience",
        model = AllTimelinesModel(lines, emptyList(), emptyList(), false, areRoutesMerged),
        lines = lines,
        plannedIngestion = planned,
        savedTimeDisplayOption = option,
        now = now
    )

    @Test
    fun autoTimesAreRelativeToNow() {
        assertEquals(
            "TestSubstance · peaking · 100% · comes down in 30 min",
            content(listOf(line()), at(90)).lines.single().text
        )
    }

    @Test
    fun regularTimesAreClockTimes() {
        val text = content(listOf(line()), at(90), SavedTimeDisplayOption.REGULAR).lines.single().text
        assertEquals("TestSubstance · peaking · 100% · comes down " + plannedTimeText(at(120), at(90), at(0), TimeDisplayOption.REGULAR), text)
    }

    @Test
    fun timesRelativeToStartCountFromTheFirstIngestion() {
        val text = content(listOf(line()), at(90), SavedTimeDisplayOption.RELATIVE_TO_START).lines.single().text
        assertEquals("TestSubstance · peaking · 100% · comes down " + plannedTimeText(at(120), at(90), at(0), TimeDisplayOption.RELATIVE_TO_START), text)
    }

    @Test
    fun thePercentIsTheShareOfTheHighestPoint() {
        assertEquals(50, content(listOf(line()), at(45)).lines.single().percentOfPeak)
    }

    @Test
    fun unmergedRoutesOfOneSubstanceNameTheirRoute() {
        val lines = listOf(line(route = AdministrationRoute.ORAL), line(route = AdministrationRoute.INSUFFLATED, minutesAfterStart = 10))
        assertEquals(listOf("TestSubstance (oral)", "TestSubstance (insufflated)"), content(lines, at(90)).lines.map { it.label })
    }

    @Test
    fun aPinnedPlanAddsALine() {
        val planned = PlannedIngestion("TestSubstance", AdministrationRoute.ORAL, 50.0, "mg")
        assertEquals("Planned: " + planned.chipText(), content(listOf(line()), at(90), planned = planned).plannedLine)
        assertNull(content(listOf(line()), at(90)).plannedLine)
    }

    @Test
    fun itEndsWhenEveryLineHasEnded() {
        val during = content(listOf(line()), at(90))
        assertFalse(during.isFinished)
        assertEquals(at(180), during.endsAt)
        assertTrue(content(listOf(line()), at(200)).isFinished)
    }

    @Test
    fun substancesWithoutTimelinesDontKeepItAlive() {
        val result = content(listOf(line(name = "Custom", duration = null)), at(10))
        assertEquals(Phase.NO_TIMELINE, result.lines.single().phase)
        assertTrue(result.isFinished)
    }

    @Test
    fun theNextRefreshIsWithinFiveMinutesOrAtTheNextChange() {
        assertEquals(at(95), content(listOf(line()), at(90)).nextRefreshAt)
        assertEquals(at(120), content(listOf(line()), at(118)).nextRefreshAt)
        assertEquals(at(120), content(listOf(line()), at(119)).nextRefreshAt)
    }

    @Test
    fun collapsedShowsThreeLinesAndCountsTheRest() {
        val lines = (1..5).map { line(name = "Substance $it", minutesAfterStart = it.toLong()) }
        val (shown, more) = content(lines, at(90)).collapsedLines()
        assertEquals(listOf("Substance 1", "Substance 2", "Substance 3"), shown.map { it.label })
        assertEquals(2, more)
    }

    @Test
    fun activeEffectsNeedALineThatHasntEnded() {
        assertFalse(hasActiveEffects(emptyList(), false, true, at(90)))
        assertTrue(hasActiveEffects(listOf(line()), false, true, at(90)))
        assertFalse(hasActiveEffects(listOf(line()), false, true, at(200)))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveNotificationContentTest*' -q`
Expected: compilation error "Unresolved reference 'buildLiveNotificationContent'".

- [ ] **Step 3: Implement** `livenotification/LiveNotificationContent.kt` (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.PlannedIngestion
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.chipText
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.plannedTimeText
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

data class SubstanceLine(
    val label: String,
    val color: AdaptiveColor,
    val phase: Phase,
    val percentOfPeak: Int?,
    val hint: TimeHint?,
    // when the line reaches zero, null if unknown
    val endsAt: Instant?,
    val text: String,
) {
    val isActive: Boolean get() = phase != Phase.ENDED && phase != Phase.NO_TIMELINE
}

data class LiveNotificationContent(
    val title: String,
    // in the order the lines are drawn on the chart
    val lines: List<SubstanceLine>,
    val plannedLine: String?,
    // when the last active line ends, null if one of them has an unknown end
    val endsAt: Instant?,
    val isFinished: Boolean,
    val nextRefreshAt: Instant,
) {
    // the lines that fit in the collapsed notification, and how many more there are
    fun collapsedLines(max: Int = 3): Pair<List<SubstanceLine>, Int> =
        lines.take(max) to (lines.size - max).coerceAtLeast(0)
}

// the notification is about the ongoing experience, so Auto is relative to now, like the redose chip
fun liveTimeDisplayOption(saved: SavedTimeDisplayOption): TimeDisplayOption = when (saved) {
    SavedTimeDisplayOption.AUTO, SavedTimeDisplayOption.RELATIVE_TO_NOW -> TimeDisplayOption.RELATIVE_TO_NOW
    SavedTimeDisplayOption.RELATIVE_TO_START -> TimeDisplayOption.RELATIVE_TO_START
    SavedTimeDisplayOption.TIME_BETWEEN -> TimeDisplayOption.TIME_BETWEEN
    SavedTimeDisplayOption.REGULAR -> TimeDisplayOption.REGULAR
}

private val longestRefreshInterval: Duration = Duration.ofMinutes(5)
private val shortestRefreshInterval: Duration = Duration.ofMinutes(1)

fun buildLiveNotificationContent(
    title: String,
    model: AllTimelinesModel,
    lines: List<DataForOneEffectLine>,
    plannedIngestion: PlannedIngestion?,
    savedTimeDisplayOption: SavedTimeDisplayOption,
    now: Instant,
): LiveNotificationContent {
    val timeOption = liveTimeDisplayOption(savedTimeDisplayOption)
    val curves = model.substanceCurves()
    val substanceLines = curves.map { curve ->
        val hasSeveralLines = curves.count { it.substanceName == curve.substanceName } > 1
        val label = if (hasSeveralLines) {
            "${curve.substanceName} (${curve.routes.joinToString { it.displayText.lowercase() }})"
        } else {
            curve.substanceName
        }
        val line = curve.line
        val phaseWithHint: PhaseWithHint
        val percent: Int?
        val endsAt: Instant?
        if (line != null) {
            phaseWithHint = line.phaseWithHint(now)
            val isOnTheLine = phaseWithHint.phase != Phase.ENDED && phaseWithHint.phase != Phase.WAITING_FOR_ONSET
            percent = if (isOnTheLine) (line.heightAt(now) / line.maxHeight * 100).roundToInt() else null
            endsAt = line.endTime
        } else {
            val result = fallbackPhase(lines.filter { it.substanceName == curve.substanceName && it.route in curve.routes }, now)
            phaseWithHint = result.phaseWithHint
            percent = null
            endsAt = result.knownEnd
        }
        val hintText = phaseWithHint.hint?.let { "${it.kind.word} ${plannedTimeText(it.time, now, model.startTime, timeOption)}" }
        val text = listOfNotNull(label, phaseWithHint.phase.word, percent?.let { "$it%" }, hintText).joinToString(" · ")
        SubstanceLine(label, curve.color, phaseWithHint.phase, percent, phaseWithHint.hint, endsAt, text)
    }
    val active = substanceLines.filter { it.isActive }
    val endsAt = if (active.isNotEmpty() && active.all { it.endsAt != null }) active.maxOf { it.endsAt!! } else null
    val latestRefresh = now + longestRefreshInterval
    val nextChange = active.mapNotNull { it.hint?.time }.filter { it > now }.minOrNull()
    val nextRefreshAt = maxOf(minOf(nextChange ?: latestRefresh, latestRefresh), now + shortestRefreshInterval)
    return LiveNotificationContent(
        title = title,
        lines = substanceLines,
        plannedLine = plannedIngestion?.let { "Planned: " + it.chipText() },
        endsAt = endsAt,
        isFinished = active.isEmpty(),
        nextRefreshAt = nextRefreshAt,
    )
}

// whether a live notification would show anything, used for the switch on the experience screen
fun hasActiveEffects(
    lines: List<DataForOneEffectLine>,
    areSubstanceHeightsIndependent: Boolean,
    areRoutesMerged: Boolean,
    now: Instant,
): Boolean {
    if (lines.isEmpty()) return false
    val model = AllTimelinesModel(lines, emptyList(), emptyList(), areSubstanceHeightsIndependent, areRoutesMerged)
    return !buildLiveNotificationContent("", model, lines, null, SavedTimeDisplayOption.REGULAR, now).isFinished
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveNotificationContentTest*' -q`
Expected: 11 tests pass. `nextRefreshAt` at 21:58 gives the hint at 22:00. If the hint lands at 22:01 because of the 1-minute steps, the assertion at `at(118)` fails. In that case ledger a ruling and assert `at(121)`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/LiveNotificationContent.kt app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/LiveNotificationContentTest.kt
git commit -m "Build the live notification texts from the timeline"
```

---

### Task 5: The user's own effect lines outside the view model

**Files:**
- Modify: `ui/tabs/journal/experience/ExperienceViewModel.kt` (move `getIngestionElements` into the `companion object`)
- Create: `ui/tabs/journal/experience/MyEffectLines.kt`
- Test: `ui/tabs/journal/experience/MyEffectLinesTest.kt`

**Interfaces:**
- Produces: `fun myEffectLines(ingestionsWithCompanions: List<IngestionWithCompanionAndCustomUnit>, substanceRepo: SubstanceRepositoryInterface): List<DataForOneEffectLine>`. These are the lines of the experience screen's main timeline: consumer `null`, sorted by time.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience

import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Category
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.SubstanceWithCategories
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepositoryInterface
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MyEffectLinesTest {
    private object NoSubstances : SubstanceRepositoryInterface {
        override fun getAllSubstances(): List<Substance> = emptyList()
        override fun getAllSubstancesWithCategories(): List<SubstanceWithCategories> = emptyList()
        override fun getAllCategories(): List<Category> = emptyList()
        override fun getSubstance(substanceName: String): Substance? = null
        override fun getCategory(categoryName: String): Category? = null
        override fun getSubstanceWithCategories(substanceName: String): SubstanceWithCategories? = null
    }

    private fun ingestion(name: String, time: String, consumer: String? = null) = IngestionWithCompanionAndCustomUnit(
        ingestion = Ingestion(
            substanceName = name,
            time = Instant.parse(time),
            administrationRoute = AdministrationRoute.ORAL,
            dose = 10.0,
            isDoseAnEstimate = false,
            estimatedDoseStandardDeviation = null,
            units = "mg",
            experienceId = 1,
            notes = null,
            stomachFullness = null,
            consumerName = consumer,
            customUnitId = null
        ),
        substanceCompanion = null,
        customUnit = null
    )

    @Test
    fun onlyTheUsersOwnIngestionsInTimeOrder() {
        val lines = myEffectLines(
            listOf(
                ingestion("B", "2026-01-01T21:00:00Z"),
                ingestion("A", "2026-01-01T20:00:00Z"),
                ingestion("C", "2026-01-01T20:30:00Z", consumer = "Friend"),
            ),
            NoSubstances
        )
        assertEquals(listOf("A", "B"), lines.map { it.substanceName })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*MyEffectLinesTest*' -q`
Expected: compilation error "Unresolved reference 'myEffectLines'".

- [ ] **Step 3: Implement**

In `ExperienceViewModel.kt`:
- cut the `private fun getIngestionElements(sortedIngestionsWith: List<IngestionWithAssociatedData>): List<IngestionElement> { ... }` member, unchanged;
- paste it as `fun getIngestionElements(...)` (not private) inside `companion object`, next to `getDataForEffectTimelines`.

The calls inside the class keep compiling unqualified.

`ui/tabs/journal/experience/MyEffectLines.kt` (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience

import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepositoryInterface
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine

// the lines of the experience screen's main timeline, for use outside the screen
fun myEffectLines(
    ingestionsWithCompanions: List<IngestionWithCompanionAndCustomUnit>,
    substanceRepo: SubstanceRepositoryInterface,
): List<DataForOneEffectLine> {
    val mine = ingestionsWithCompanions
        .filter { it.ingestion.consumerName == null }
        .sortedBy { it.ingestion.time }
        .map { ingestionWithCompanion ->
            val ingestion = ingestionWithCompanion.ingestion
            val roa = substanceRepo.getSubstance(ingestion.substanceName)?.getRoa(ingestion.administrationRoute)
            IngestionWithAssociatedData(
                ingestionWithCompanionAndCustomUnit = ingestionWithCompanion,
                roaDuration = roa?.roaDuration,
                roaDose = roa?.roaDose
            )
        }
    val elements = ExperienceViewModel.getIngestionElements(mine)
    val substances = elements.mapNotNull {
        substanceRepo.getSubstance(it.ingestionWithCompanionAndCustomUnit.ingestion.substanceName)
    }
    return ExperienceViewModel.getDataForEffectTimelines(ingestionElements = elements, substances = substances)
}
```

- [ ] **Step 4: Run the test and the suite**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: `MyEffectLinesTest` passes. The suite is green except the three known failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceViewModel.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/MyEffectLines.kt app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/MyEffectLinesTest.kt
git commit -m "Build the main timeline lines outside the experience view model"
```

---

### Task 6: Remember the live experience and the two settings

**Files:**
- Create: `ui/tabs/journal/experience/livenotification/LiveExperience.kt`
- Modify: `ui/tabs/settings/combinations/UserPreferences.kt`
- Test: `ui/tabs/journal/experience/livenotification/LiveExperienceTest.kt`

**Interfaces:**
- Produces:
  - `data class LiveExperience(val experienceId: Int, val experienceCreatedAt: Instant)`, with:
    - `fun toPreferenceString(): String`
    - `fun isFor(experience: Experience): Boolean`
    - `companion fun fromPreferenceString(value: String?): LiveExperience?`
  - `UserPreferences`:
    - `val liveExperienceFlow: Flow<LiveExperience?>`
    - `suspend fun saveLiveExperience(value: LiveExperience?)`
    - `val isLiveAutoStartFlow: Flow<Boolean>`
    - `suspend fun saveIsLiveAutoStart(value: Boolean)`
    - `val isLiveShownOnLockScreenFlow: Flow<Boolean>`
    - `suspend fun saveIsLiveShownOnLockScreen(value: Boolean)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveExperienceTest {
    private val createdAt = Instant.parse("2026-01-01T20:00:00.123Z")
    private fun experience(id: Int, creationDate: Instant) =
        Experience(id = id, title = "T", text = "", creationDate = creationDate, sortDate = creationDate, location = null)

    @Test
    fun roundTrips() {
        val live = LiveExperience(3, createdAt)
        assertEquals(live, LiveExperience.fromPreferenceString(live.toPreferenceString()))
    }

    @Test
    fun malformedValuesAreAbsent() {
        assertNull(LiveExperience.fromPreferenceString(null))
        assertNull(LiveExperience.fromPreferenceString("abc"))
        assertNull(LiveExperience.fromPreferenceString("3|not a date"))
    }

    @Test
    fun aReusedIdIsAnotherExperience() {
        val live = LiveExperience(3, createdAt)
        assertTrue(live.isFor(experience(3, createdAt)))
        assertFalse(live.isFor(experience(3, createdAt.plusSeconds(60))))
        assertFalse(live.isFor(experience(4, createdAt)))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveExperienceTest*' -q`
Expected: compilation error "Unresolved reference 'LiveExperience'".

- [ ] **Step 3: Implement** `livenotification/LiveExperience.kt` (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import java.time.Instant
import java.time.temporal.ChronoUnit

// experience ids are reused after deleting an experience, so the creation date tells them apart
data class LiveExperience(val experienceId: Int, val experienceCreatedAt: Instant) {
    fun toPreferenceString(): String = "$experienceId|$experienceCreatedAt"

    fun isFor(experience: Experience): Boolean =
        experience.id == experienceId &&
                experience.creationDate.truncatedTo(ChronoUnit.MILLIS) == experienceCreatedAt.truncatedTo(ChronoUnit.MILLIS)

    companion object {
        fun fromPreferenceString(value: String?): LiveExperience? {
            val parts = value?.split("|") ?: return null
            if (parts.size != 2) return null
            val id = parts[0].toIntOrNull() ?: return null
            val createdAt = runCatching { Instant.parse(parts[1]) }.getOrNull() ?: return null
            return LiveExperience(id, createdAt)
        }
    }
}
```

In `UserPreferences.kt`, add to `PreferencesKeys`:

```kotlin
        val KEY_LIVE_EXPERIENCE = stringPreferencesKey("KEY_LIVE_EXPERIENCE")
        val KEY_LIVE_AUTO_START = booleanPreferencesKey("KEY_LIVE_AUTO_START")
        val KEY_LIVE_SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("KEY_LIVE_SHOW_ON_LOCK_SCREEN")
```

and at the end of the class:

```kotlin
    // the experience shown in the live notification, if any
    val liveExperienceFlow: Flow<LiveExperience?> = dataStore.data
        .map { preferences -> LiveExperience.fromPreferenceString(preferences[PreferencesKeys.KEY_LIVE_EXPERIENCE]) }
        .distinctUntilChanged()

    suspend fun saveLiveExperience(value: LiveExperience?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(PreferencesKeys.KEY_LIVE_EXPERIENCE)
            } else {
                preferences[PreferencesKeys.KEY_LIVE_EXPERIENCE] = value.toPreferenceString()
            }
        }
    }

    val isLiveAutoStartFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.KEY_LIVE_AUTO_START] ?: false }

    suspend fun saveIsLiveAutoStart(value: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.KEY_LIVE_AUTO_START] = value }
    }

    val isLiveShownOnLockScreenFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.KEY_LIVE_SHOW_ON_LOCK_SCREEN] ?: false }

    suspend fun saveIsLiveShownOnLockScreen(value: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.KEY_LIVE_SHOW_ON_LOCK_SCREEN] = value }
    }
```

Import `com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.LiveExperience`. `distinctUntilChanged` is already imported for `routeVisibilityFlow`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveExperienceTest*' -q`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/LiveExperience.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/combinations/UserPreferences.kt app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/LiveExperienceTest.kt
git commit -m "Remember the live experience and its settings"
```

---

### Task 7: Notifier, chart, controller, receiver and alarms

**Files:**
- Create in `ui/tabs/journal/experience/livenotification/`:
  - `RefreshDecision.kt`
  - `LiveNotificationIntents.kt`
  - `LiveNotificationChart.kt`
  - `LiveNotificationNotifier.kt`
  - `LiveNotificationController.kt`
  - `LiveNotificationReceiver.kt`
- Create resources:
  - `app/src/main/res/layout/live_notification_collapsed.xml`
  - `app/src/main/res/layout/live_notification_expanded.xml`
  - `app/src/main/res/drawable/ic_live_timeline.xml`
- Modify:
  - `app/src/main/AndroidManifest.xml`
  - `di/JournalApplication.kt`
- Test: `ui/tabs/journal/experience/livenotification/RefreshDecisionTest.kt`

**Interfaces:**
- Consumes:
  - `buildLiveNotificationContent`, `collapsedLines` (Task 4);
  - `myEffectLines` (Task 5);
  - `LiveExperience` and the `UserPreferences` flows (Task 6);
  - existing `PinnedRedose.belongsTo`, `AllTimelinesModel.resolvePinnedPlan`.
- Produces:
  - `sealed interface RefreshDecision { data object Stop; data class Post(val experienceId: Int, val content: LiveNotificationContent, val model: AllTimelinesModel) }`
  - `suspend fun decideRefresh(live: LiveExperience?, experience: Experience?, canPost: Boolean, build: suspend (Experience) -> RefreshDecision.Post?): RefreshDecision`
  - `LiveNotificationController`:
    - `fun observe()`
    - `suspend fun start(experienceId: Int)`
    - `suspend fun stop()`
    - `suspend fun refresh()`
    - `fun onIngestionLogged(experienceId: Int, ingestionTime: Instant)`
    - `val liveExperienceFlow: Flow<LiveExperience?>`
  - `sealed interface NotificationTarget { data class Experience(val experienceId: Int, val openQuickLog: Boolean); data object AddIngestion }`
  - `fun Intent.notificationTarget(): NotificationTarget?`
  - Intent action constants in `LiveNotificationIntents`.

Only `decideRefresh` is JVM-testable. The rest is Android glue: its gate is a passing build here and the emulator checks in Task 11.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class RefreshDecisionTest {
    private val createdAt = Instant.parse("2026-01-01T19:00:00Z")
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)
    private val experience = Experience(id = 1, title = "T", text = "", creationDate = createdAt, sortDate = start, location = null)
    private val live = LiveExperience(1, createdAt)

    private fun post(now: Instant): RefreshDecision.Post {
        val lines = listOf(
            DataForOneEffectLine(
                substanceName = "TestSubstance",
                route = AdministrationRoute.ORAL,
                roaDuration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null),
                height = 1f,
                horizontalWeight = 0.5f,
                color = AdaptiveColor.BLUE,
                startTime = start,
                endTime = null,
            )
        )
        val model = AllTimelinesModel(lines, emptyList(), emptyList(), false, true)
        return RefreshDecision.Post(1, buildLiveNotificationContent("T", model, lines, null, SavedTimeDisplayOption.AUTO, now), model)
    }

    private fun decide(live: LiveExperience?, experience: Experience?, canPost: Boolean = true, now: Instant = at(90)) =
        runBlocking { decideRefresh(live, experience, canPost) { post(now) } }

    @Test
    fun anActiveExperienceIsPosted() {
        assertTrue(decide(live, experience) is RefreshDecision.Post)
    }

    @Test
    fun aDeletedExperienceStops() {
        assertEquals(RefreshDecision.Stop, decide(live, null))
    }

    @Test
    fun aReusedIdStops() {
        assertEquals(RefreshDecision.Stop, decide(live, experience.copy(creationDate = createdAt.plusSeconds(3600))))
    }

    @Test
    fun blockedNotificationsStop() {
        assertEquals(RefreshDecision.Stop, decide(live, experience, canPost = false))
    }

    @Test
    fun aFinishedExperienceStops() {
        assertEquals(RefreshDecision.Stop, decide(live, experience, now = at(200)))
    }

    @Test
    fun nothingLiveStops() {
        assertEquals(RefreshDecision.Stop, decide(null, experience))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RefreshDecisionTest*' -q`
Expected: compilation error "Unresolved reference 'decideRefresh'".

- [ ] **Step 3: Implement `RefreshDecision.kt`** (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel

sealed interface RefreshDecision {
    data object Stop : RefreshDecision
    data class Post(
        val experienceId: Int,
        val content: LiveNotificationContent,
        val model: AllTimelinesModel,
    ) : RefreshDecision
}

// stops for a deleted or replaced experience, blocked notifications and effects that have ended
suspend fun decideRefresh(
    live: LiveExperience?,
    experience: Experience?,
    canPost: Boolean,
    build: suspend (Experience) -> RefreshDecision.Post?,
): RefreshDecision {
    if (live == null || experience == null || !live.isFor(experience) || !canPost) return RefreshDecision.Stop
    val post = build(experience) ?: return RefreshDecision.Stop
    return if (post.content.isFinished) RefreshDecision.Stop else post
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*RefreshDecisionTest*' -q`
Expected: 6 tests pass.

- [ ] **Step 5: Implement `LiveNotificationIntents.kt`** (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.isaakhanimann.journal.MainActivity

sealed interface NotificationTarget {
    data class Experience(val experienceId: Int, val openQuickLog: Boolean) : NotificationTarget
    data object AddIngestion : NotificationTarget
}

object LiveNotificationIntents {
    const val ACTION_REFRESH = "com.isaakhanimann.journal.live.REFRESH"
    const val ACTION_STOP = "com.isaakhanimann.journal.live.STOP"
    const val ACTION_DISMISSED = "com.isaakhanimann.journal.live.DISMISSED"

    private const val EXTRA_OPEN_EXPERIENCE_ID = "live_open_experience_id"
    private const val EXTRA_OPEN_QUICK_LOG = "live_open_quick_log"
    private const val EXTRA_ADD_INGESTION = "live_add_ingestion"

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun openExperience(context: Context, experienceId: Int, openQuickLog: Boolean): PendingIntent {
        val intent = mainActivityIntent(context)
            .putExtra(EXTRA_OPEN_EXPERIENCE_ID, experienceId)
            .putExtra(EXTRA_OPEN_QUICK_LOG, openQuickLog)
        return PendingIntent.getActivity(context, if (openQuickLog) 2 else 1, intent, FLAGS)
    }

    fun addIngestion(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 3, mainActivityIntent(context).putExtra(EXTRA_ADD_INGESTION, true), FLAGS)

    fun broadcast(context: Context, action: String): PendingIntent {
        val intent = Intent(context, LiveNotificationReceiver::class.java).setAction(action)
        val requestCode = when (action) {
            ACTION_REFRESH -> 10
            ACTION_STOP -> 11
            else -> 12
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, FLAGS)
    }

    private fun mainActivityIntent(context: Context) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun target(intent: Intent?): NotificationTarget? {
        if (intent == null) return null
        if (intent.getBooleanExtra(EXTRA_ADD_INGESTION, false)) return NotificationTarget.AddIngestion
        val experienceId = intent.getIntExtra(EXTRA_OPEN_EXPERIENCE_ID, -1)
        if (experienceId < 0) return null
        return NotificationTarget.Experience(experienceId, intent.getBooleanExtra(EXTRA_OPEN_QUICK_LOG, false))
    }
}

fun Intent.notificationTarget(): NotificationTarget? = LiveNotificationIntents.target(this)
```

- [ ] **Step 6: Implement `LiveNotificationChart.kt`** (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import java.time.Duration
import java.time.Instant

// the timeline as the experience screen draws it, with a line at the current time; null if drawing fails
fun drawLiveChart(
    model: AllTimelinesModel,
    now: Instant,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    isDarkTheme: Boolean,
): Bitmap? = try {
    val image = ImageBitmap(widthPx, heightPx)
    val size = Size(widthPx.toFloat(), heightPx.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), size) {
        val pixelsPerSec = size.width / model.widthInSeconds
        // keeps the thick line inside the image at its highest point
        val verticalInset = 4 * density.density
        inset(left = 0f, top = verticalInset, right = 0f, bottom = verticalInset) {
            model.groupDrawables.forEach { group ->
                group.drawTimeLine(
                    drawScope = this,
                    canvasHeight = this.size.height,
                    pixelsPerSec = pixelsPerSec,
                    color = group.color.getComposeColor(isDarkTheme),
                    density = density
                )
            }
        }
        val nowX = Duration.between(model.startTime, now).seconds * pixelsPerSec
        drawLine(
            color = if (isDarkTheme) Color.White else Color.Black,
            start = Offset(nowX, 0f),
            end = Offset(nowX, size.height),
            strokeWidth = 2 * density.density
        )
    }
    image.asAndroidBitmap()
} catch (e: Exception) {
    null
}
```

- [ ] **Step 7: Create the resources**

`app/src/main/res/drawable/ic_live_timeline.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- a rising and falling curve, drawn for this app -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:pathData="M2,19 C6,19 7,6 11,6 C15,6 16,16 22,19" />
</vector>
```

`app/src/main/res/layout/live_notification_collapsed.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/live_lines"
    style="@style/TextAppearance.Compat.Notification"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:ellipsize="end"
    android:maxLines="4" />
```

`app/src/main/res/layout/live_notification_expanded.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">

    <ImageView
        android:id="@+id/live_chart"
        android:layout_width="match_parent"
        android:layout_height="80dp"
        android:contentDescription="Timeline"
        android:scaleType="fitXY" />

    <TextView
        android:id="@+id/live_lines"
        style="@style/TextAppearance.Compat.Notification"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp" />
</LinearLayout>
```

- [ ] **Step 8: Implement `LiveNotificationNotifier.kt`** (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isaakhanimann.journal.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveNotificationNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val isDarkTheme: Boolean
        get() = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun canPost(): Boolean {
        val isGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return isGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // canPost() checks the permission
    fun post(experienceId: Int, content: LiveNotificationContent, chart: Bitmap?, showDetailsOnLockScreen: Boolean) {
        if (!canPost()) return
        ensureChannel()
        val (shown, more) = content.collapsedLines()
        val collapsed = RemoteViews(context.packageName, R.layout.live_notification_collapsed).apply {
            setTextViewText(R.id.live_lines, linesText(shown, more, plannedLine = null))
        }
        val expanded = RemoteViews(context.packageName, R.layout.live_notification_expanded).apply {
            if (chart != null) setImageViewBitmap(R.id.live_chart, chart) else setViewVisibility(R.id.live_chart, View.GONE)
            setTextViewText(R.id.live_lines, linesText(content.lines, 0, content.plannedLine))
        }
        // the lock screen shows this version unless details are allowed there
        val publicVersion = baseBuilder()
            .setContentTitle("Experience in progress")
            .build()
        val builder = baseBuilder()
            .setContentTitle(content.title)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(LiveNotificationIntents.openExperience(context, experienceId, openQuickLog = false))
            .setDeleteIntent(LiveNotificationIntents.broadcast(context, LiveNotificationIntents.ACTION_DISMISSED))
            .addAction(0, "Add ingestion", LiveNotificationIntents.addIngestion(context))
            .setVisibility(if (showDetailsOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
        if (content.plannedLine != null) {
            builder.addAction(0, "Log planned", LiveNotificationIntents.openExperience(context, experienceId, openQuickLog = true))
        }
        builder.addAction(0, "Stop", LiveNotificationIntents.broadcast(context, LiveNotificationIntents.ACTION_STOP))
        // Android keeps this countdown running between refreshes
        content.endsAt?.let {
            builder.setWhen(it.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun baseBuilder() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_live_timeline)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    private fun linesText(lines: List<SubstanceLine>, more: Int, plannedLine: String?): CharSequence {
        val text = SpannableStringBuilder()
        lines.forEach { line ->
            if (text.isNotEmpty()) text.append("\n")
            val dotStart = text.length
            text.append("● ")
            text.setSpan(
                ForegroundColorSpan(line.color.getComposeColor(isDarkTheme).toArgb()),
                dotStart,
                dotStart + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.append(line.text)
        }
        if (more > 0) text.append("\n+$more more")
        if (plannedLine != null) text.append("\n").append(plannedLine)
        return text
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Live timeline", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Follows the estimated timeline of the current experience"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "live_timeline"
        const val NOTIFICATION_ID = 4711
    }
}
```

- [ ] **Step 9: Implement `LiveNotificationController.kt`** (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.Density
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.experience.myEffectLines
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.belongsTo
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveNotificationController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    private val userPreferences: UserPreferences,
    private val notifier: LiveNotificationNotifier,
) {
    // a failed refresh must never crash the app, the next refresh tries again
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w("LiveNotification", "Refresh failed", error)
        }
    )
    private val mutex = Mutex()

    val liveExperienceFlow: Flow<LiveExperience?> = userPreferences.liveExperienceFlow

    // keeps the notification in step with the journal while the app process runs
    fun observe() {
        scope.launch {
            userPreferences.liveExperienceFlow.collectLatest { live ->
                if (live == null) return@collectLatest
                experienceRepo.getIngestionsWithCompanionsFlow(live.experienceId).collect { refresh() }
            }
        }
    }

    suspend fun start(experienceId: Int) {
        val experience = experienceRepo.getExperience(experienceId) ?: return
        userPreferences.saveLiveExperience(LiveExperience(experience.id, experience.creationDate))
        refresh()
    }

    suspend fun stop() = mutex.withLock { stopLocked() }

    // starts the notification after logging an ingestion taken around now, if the user chose so
    fun onIngestionLogged(experienceId: Int, ingestionTime: Instant) {
        scope.launch {
            if (!isRecentForAutoStart(ingestionTime, Instant.now())) return@launch
            if (!userPreferences.isLiveAutoStartFlow.first()) return@launch
            if (!notifier.canPost()) return@launch
            start(experienceId)
        }
    }

    suspend fun refresh() = mutex.withLock {
        val live = userPreferences.liveExperienceFlow.first()
        val experience = live?.let { experienceRepo.getExperience(it.experienceId) }
        val now = Instant.now()
        when (val decision = decideRefresh(live, experience, notifier.canPost()) { build(it, now) }) {
            RefreshDecision.Stop -> stopLocked()
            is RefreshDecision.Post -> {
                val chart = drawLiveChart(
                    model = decision.model,
                    now = now,
                    widthPx = 600,
                    heightPx = 160,
                    density = Density(context),
                    isDarkTheme = notifier.isDarkTheme
                )
                notifier.post(
                    experienceId = decision.experienceId,
                    content = decision.content,
                    chart = chart,
                    showDetailsOnLockScreen = userPreferences.isLiveShownOnLockScreenFlow.first()
                )
                scheduleRefresh(decision.content.nextRefreshAt)
            }
        }
    }

    private suspend fun stopLocked() {
        if (userPreferences.liveExperienceFlow.first() != null) userPreferences.saveLiveExperience(null)
        notifier.cancel()
        alarmManager().cancel(LiveNotificationIntents.broadcast(context, LiveNotificationIntents.ACTION_REFRESH))
    }

    private suspend fun build(experience: Experience, now: Instant): RefreshDecision.Post? {
        val lines = myEffectLines(experienceRepo.getIngestionsWithCompanions(experience.id), substanceRepo)
        if (lines.isEmpty()) return null
        val model = AllTimelinesModel(
            dataForLines = lines,
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = userPreferences.areSubstanceHeightsIndependentFlow.first(),
            areRoutesMerged = userPreferences.areRoutesMergedFlow.first()
        )
        val planned = userPreferences.pinnedRedoseFlow.first()
            ?.takeIf { it.belongsTo(experience.id, experience.creationDate) }
            ?.let { model.resolvePinnedPlan(it, now) }
        val content = buildLiveNotificationContent(
            title = experience.title,
            model = model,
            lines = lines,
            plannedIngestion = planned,
            savedTimeDisplayOption = userPreferences.savedTimeDisplayOptionFlow.first(),
            now = now
        )
        return RefreshDecision.Post(experience.id, content, model)
    }

    // inexact, so no exact-alarm permission is needed; the countdown stays live if Android delays it
    private fun scheduleRefresh(at: Instant) {
        alarmManager().setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toEpochMilli(),
            LiveNotificationIntents.broadcast(context, LiveNotificationIntents.ACTION_REFRESH)
        )
    }

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)
}

// auto-start only for ingestions taken around now, not for entries about the past or plans for later
fun isRecentForAutoStart(ingestionTime: Instant, now: Instant): Boolean =
    ingestionTime >= now - Duration.ofMinutes(60) && ingestionTime <= now + Duration.ofMinutes(5)
```

- [ ] **Step 10: Implement `LiveNotificationReceiver.kt`** (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LiveNotificationReceiver : BroadcastReceiver() {
    @Inject
    lateinit var controller: LiveNotificationController

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Hilt injects here
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    LiveNotificationIntents.ACTION_REFRESH -> controller.refresh()
                    LiveNotificationIntents.ACTION_STOP, LiveNotificationIntents.ACTION_DISMISSED -> controller.stop()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

If `super.onReceive` doesn't compile (it's abstract in `BroadcastReceiver`, and Hilt's generated base adds it), Hilt's Gradle plugin isn't transforming the class. Check that `@AndroidEntryPoint` is imported from `dagger.hilt.android`, and ledger the fix.

- [ ] **Step 11: Register in the manifest and start observing**

In `app/src/main/AndroidManifest.xml`, after the `AD_ID` line:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

and inside `<application>`, after the `</activity>`:

```xml
        <receiver
            android:name=".ui.tabs.journal.experience.livenotification.LiveNotificationReceiver"
            android:exported="false" />
```

`di/JournalApplication.kt` becomes (the GPL header stays):

```kotlin
@HiltAndroidApp
class JournalApplication : Application() {
    @Inject
    lateinit var liveNotificationController: LiveNotificationController

    override fun onCreate() {
        super.onCreate()
        liveNotificationController.observe()
    }
}
```

with imports `javax.inject.Inject` and `com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.LiveNotificationController`.

- [ ] **Step 12: Build and run the suite**

Run: `./gradlew :app:assembleDebug -q && ./gradlew :app:testDebugUnitTest -q`
Expected: the build succeeds and the suite is green except the three known failures.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/layout/live_notification_collapsed.xml app/src/main/res/layout/live_notification_expanded.xml app/src/main/res/drawable/ic_live_timeline.xml app/src/main/java/com/isaakhanimann/journal/di/JournalApplication.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/ app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/RefreshDecisionTest.kt
git commit -m "Post and refresh the live timeline notification"
```

---

### Task 8: Open the app from the notification

**Files:**
- Modify:
  - `MainActivity.kt`
  - `ui/main/MainScreen.kt`
  - `ui/main/MainScreenViewModel.kt`
  - `ui/main/navigation/graphs/journalGraph.kt`
  - `ui/tabs/journal/experience/ExperienceViewModel.kt`
  - `ui/tabs/journal/experience/ExperienceScreen.kt`
  - `ui/tabs/journal/experience/timeline/PlannedIngestionUi.kt`

**Interfaces:**
- Consumes: `NotificationTarget`, `Intent.notificationTarget()` (Task 7).
- Produces:
  - `ExperienceRoute(experienceId: Int, openQuickLog: Boolean = false)`
  - `ExperienceViewModel.isQuickLogRequested: StateFlow<Boolean>`
  - `ExperienceViewModel.onQuickLogOpened()`
  - `PlannedIngestionSection(..., openLogOnStart: Boolean = false, onLogOpened: () -> Unit = {})`

Navigation glue has no JVM test; the gate is the build and the emulator checks in Task 11.

- [ ] **Step 1: Route flag.** In `journalGraph.kt`:

```kotlin
@Serializable
data class ExperienceRoute(val experienceId: Int, val openQuickLog: Boolean = false)
```

Keep the existing annotation line above it.

- [ ] **Step 2: View model.** In `ExperienceViewModel`:
  - Add `private val quickLogRequest = MutableStateFlow(false)` and `val isQuickLogRequested: StateFlow<Boolean> = quickLogRequest`.
  - In `init`, after `experienceId = expId`, add `quickLogRequest.value = experienceRoute.openQuickLog`.
  - Add the function below.

```kotlin
    // the notification's Log planned opens the quick-log dialog once
    fun onQuickLogOpened() {
        quickLogRequest.value = false
    }
```

- [ ] **Step 3: Dialog.** In `PlannedIngestionUi.kt`, add two parameters to `PlannedIngestionSection`, after `onChangeDetails`:

```kotlin
    // opens the quick-log dialog for the pinned plan once, when coming from the notification
    openLogOnStart: Boolean = false,
    onLogOpened: () -> Unit = {},
```

Then, right after the `var logSnapshot by remember { ... }` line:

```kotlin
    LaunchedEffect(openLogOnStart, state.isPinned, state.planned) {
        val planned = state.planned
        if (openLogOnStart && state.isPinned && planned != null) {
            logSnapshot = planned to (state.plannedLine?.color ?: AdaptiveColor.entries.first())
            onLogOpened()
        }
    }
```

Add the import `androidx.compose.runtime.LaunchedEffect` if it's missing.

- [ ] **Step 4: Screen plumbing.** In `ExperienceScreen.kt`:
  - Add the parameters `openQuickLogOnStart: Boolean = false, onQuickLogOpened: () -> Unit = {}` to the stateless `ExperienceScreen` and to `MyTimelineSection`.
  - Pass them on: `ExperienceScreen` → `MyTimelineSection` → `PlannedIngestionSection(openLogOnStart = openQuickLogOnStart, onLogOpened = onQuickLogOpened, ...)`.
  - In the stateful `ExperienceScreen(viewModel)`, pass `openQuickLogOnStart = viewModel.isQuickLogRequested.collectAsState().value` and `onQuickLogOpened = viewModel::onQuickLogOpened`.

- [ ] **Step 5: MainScreenViewModel.** Add `private val experienceRepo: ExperienceRepository` to the constructor, and:

```kotlin
    suspend fun experienceExists(experienceId: Int): Boolean = experienceRepo.getExperience(experienceId) != null
```

- [ ] **Step 6: MainScreen.** Change the signature to:

```kotlin
fun MainScreen(
    notificationTarget: NotificationTarget? = null,
    onNotificationTargetHandled: () -> Unit = {},
    viewModel: MainScreenViewModel = hiltViewModel()
)
```

In the accepted branch, right after the `NavHost(...) { ... }` call inside `NavigationSuiteScaffold`'s content, add:

```kotlin
            LaunchedEffect(notificationTarget) {
                when (val target = notificationTarget) {
                    null -> return@LaunchedEffect
                    is NotificationTarget.Experience -> if (viewModel.experienceExists(target.experienceId)) {
                        navController.navigate(ExperienceRoute(target.experienceId, target.openQuickLog))
                    }
                    NotificationTarget.AddIngestion -> navController.navigate(AddIngestionRoute)
                }
                onNotificationTargetHandled()
            }
```

In the `else` branch, before `AcceptConditionsScreen(...)`, add the code below. Until the conditions are accepted, the notification only opens the app.

```kotlin
        LaunchedEffect(notificationTarget) {
            if (notificationTarget != null) onNotificationTargetHandled()
        }
```

Import `LaunchedEffect`, `NotificationTarget`, `ExperienceRoute` and `AddIngestionRoute`.

- [ ] **Step 7: MainActivity.**

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // where a tap on the live notification should lead, handled once by MainScreen
    private val notificationTarget = mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) notificationTarget.value = intent.notificationTarget()
        setContent {
            JournalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        notificationTarget = notificationTarget.value,
                        onNotificationTargetHandled = { notificationTarget.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        notificationTarget.value = intent.notificationTarget()
    }
}
```

Import `android.content.Intent`, `androidx.compose.runtime.mutableStateOf`, and the `NotificationTarget` / `notificationTarget` from `livenotification`.

- [ ] **Step 8: Build and run the suite**

Run: `./gradlew :app:assembleDebug -q && ./gradlew :app:testDebugUnitTest -q`
Expected: the build succeeds and the suite is green except the three known failures.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/MainActivity.kt app/src/main/java/com/isaakhanimann/journal/ui/main/MainScreen.kt app/src/main/java/com/isaakhanimann/journal/ui/main/MainScreenViewModel.kt app/src/main/java/com/isaakhanimann/journal/ui/main/navigation/graphs/journalGraph.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceViewModel.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceScreen.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestionUi.kt
git commit -m "Open the experience, the add flow or the quick log from the notification"
```

---

### Task 9: The switch on the experience screen

**Files:**
- Create: `ui/tabs/journal/experience/livenotification/LiveNotificationRow.kt`
- Modify:
  - `ui/tabs/journal/experience/ExperienceViewModel.kt`
  - `ui/tabs/journal/experience/ExperienceScreen.kt`

**Interfaces:**
- Consumes:
  - `LiveNotificationController.start/stop/liveExperienceFlow` (Task 7);
  - `hasActiveEffects` (Task 4);
  - `LiveExperience.isFor` (Task 6).
- Produces:
  - `ExperienceViewModel`:
    - `val isLiveFlow: StateFlow<Boolean>`
    - `val isLiveNotificationAvailableFlow: StateFlow<Boolean>`
    - `fun setLive(isLive: Boolean)`
  - `@Composable fun LiveNotificationRow(isLive: Boolean, setLive: (Boolean) -> Unit)`

UI glue has no JVM test; its logic is `hasActiveEffects`, tested in Task 4. The gate is the build and the emulator checks in Task 11.

- [ ] **Step 1: View model.** Add `private val liveNotificationController: LiveNotificationController` to `ExperienceViewModel`'s constructor, and below `dataForEffectTimelinesFlow`:

```kotlin
    val isLiveFlow = combine(liveNotificationController.liveExperienceFlow, experienceFlow) { live, experience ->
        experience != null && live?.isFor(experience) == true
    }.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    private val hasActiveEffectsFlow = combine(dataForEffectTimelinesFlow, chartSettingsFlow, currentTimeFlow) { lines, (areSubstanceHeightsIndependent, areRoutesMerged), now ->
        hasActiveEffects(lines, areSubstanceHeightsIndependent, areRoutesMerged, now)
    }.flowOn(Dispatchers.Default)

    // shown while something can be followed, and always while it's live so it can be turned off
    val isLiveNotificationAvailableFlow = combine(hasActiveEffectsFlow, isLiveFlow) { hasActiveEffects, isLive ->
        hasActiveEffects || isLive
    }.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun setLive(isLive: Boolean) {
        viewModelScope.launch {
            if (isLive) liveNotificationController.start(experienceId) else liveNotificationController.stop()
        }
    }
```

`currentTimeFlow` is declared before `isCurrentExperienceFlow`. If it's declared after `dataForEffectTimelinesFlow`, move these declarations below it, because properties initialise in order.

- [ ] **Step 2: Row composable.** `livenotification/LiveNotificationRow.kt` (after the GPL header):

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

@Composable
fun LiveNotificationRow(isLive: Boolean, setLive: (Boolean) -> Unit) {
    val context = LocalContext.current
    var isBlocked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) setLive(true) else isBlocked = true
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Live notification", modifier = Modifier.weight(1f))
            Switch(
                checked = isLive,
                onCheckedChange = { isChecked ->
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    when {
                        !isChecked -> setLive(false)
                        needsPermission -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        !NotificationManagerCompat.from(context).areNotificationsEnabled() -> isBlocked = true
                        else -> {
                            isBlocked = false
                            setLive(true)
                        }
                    }
                }
            )
        }
        if (isBlocked) {
            Text(
                text = "Notifications are blocked for this app, so the live notification can't be shown.",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) {
                Text("Open notification settings")
            }
        }
    }
}
```

- [ ] **Step 3: Screen.** In `ExperienceScreen.kt`:
  - Add `isLive: Boolean = false, isLiveNotificationAvailable: Boolean = false, setLive: (Boolean) -> Unit = {}` to the stateless `ExperienceScreen` and to `MyTimelineSection`, and pass them through.
  - In the stateful `ExperienceScreen(viewModel)`, pass `isLive = viewModel.isLiveFlow.collectAsState().value`, `isLiveNotificationAvailable = viewModel.isLiveNotificationAvailableFlow.collectAsState().value` and `setLive = viewModel::setLive`.
  - In `MyTimelineSection`, directly after `TimelineQuantityDetails(model = timelineModel, selectedTime = selectedTime)`, add:

```kotlin
                    if (isLiveNotificationAvailable) {
                        LiveNotificationRow(isLive = isLive, setLive = setLive)
                    }
```

- [ ] **Step 4: Build and run the suite**

Run: `./gradlew :app:assembleDebug -q && ./gradlew :app:testDebugUnitTest -q`
Expected: the build succeeds and the suite is green except the three known failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/LiveNotificationRow.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceViewModel.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceScreen.kt
git commit -m "Turn the live notification on and off from the experience"
```

---

### Task 10: Settings and auto-start

**Files:**
- Modify:
  - `ui/tabs/settings/SettingsViewModel.kt`
  - `ui/tabs/settings/SettingsScreen.kt`
  - `ui/tabs/journal/addingestion/time/FinishIngestionScreenViewModel.kt`
  - `ui/tabs/journal/experience/timeline/PlannedIngestionActions.kt`
- Test: `ui/tabs/journal/experience/livenotification/AutoStartTest.kt`

**Interfaces:**
- Consumes:
  - `isRecentForAutoStart`, `LiveNotificationController.onIngestionLogged`, `refresh` (Task 7);
  - the settings flows (Task 6).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AutoStartTest {
    private val now = Instant.parse("2026-01-01T20:00:00Z")

    @Test
    fun ingestionsAroundNowStartIt() {
        assertTrue(isRecentForAutoStart(now, now))
        assertTrue(isRecentForAutoStart(now.minusSeconds(60 * 60), now))
        assertTrue(isRecentForAutoStart(now.plusSeconds(5 * 60), now))
    }

    @Test
    fun olderOrLaterIngestionsDont() {
        assertFalse(isRecentForAutoStart(now.minusSeconds(60 * 60 + 1), now))
        assertFalse(isRecentForAutoStart(now.plusSeconds(5 * 60 + 1), now))
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests '*AutoStartTest*' -q`
Expected: PASS, because `isRecentForAutoStart` was written in Task 7, step 9. This test pins its boundaries. To confirm the test can fail, temporarily change `60` to `59` in `isRecentForAutoStart`, run it, watch `ingestionsAroundNowStartIt` fail, then restore `60`. Ledger that the red was made this way.

- [ ] **Step 3: Settings view model.** Add `private val liveNotificationController: LiveNotificationController` to the constructor of `SettingsViewModel`, and:

```kotlin
    val isLiveAutoStartFlow = userPreferences.isLiveAutoStartFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveIsLiveAutoStart(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsLiveAutoStart(value)
    }

    val isLiveShownOnLockScreenFlow = userPreferences.isLiveShownOnLockScreenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    // a live notification takes the new lock-screen choice right away
    fun saveIsLiveShownOnLockScreen(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsLiveShownOnLockScreen(value)
        liveNotificationController.refresh()
    }
```

- [ ] **Step 4: Settings screen.**
  - Add the parameters `isLiveAutoStart: Boolean`, `saveIsLiveAutoStart: (Boolean) -> Unit`, `isLiveShownOnLockScreen: Boolean` and `saveIsLiveShownOnLockScreen: (Boolean) -> Unit` to the stateless `SettingsScreen`, after `saveAreRoutesMerged`.
  - Pass them from the stateful one (`viewModel.isLiveAutoStartFlow.collectAsState().value`, `viewModel::saveIsLiveAutoStart`, and so on).
  - Pass `false` / `{}` in the preview call (around line 107).
  - After the "Merge routes of same substance" `SwitchWithInfoRow`, add:

```kotlin
                HorizontalDivider()
                SwitchWithInfoRow(
                    title = "Start live notification automatically",
                    info = "When you log an ingestion taken in the last hour, the live notification starts for its experience. It follows the estimated timeline outside the app until the effects end. Notifications must be allowed; turn the live notification on once from an experience to allow them.",
                    checked = isLiveAutoStart,
                    onCheckedChange = saveIsLiveAutoStart
                )
                HorizontalDivider()
                SwitchWithInfoRow(
                    title = "Live notification details on lock screen",
                    info = "When disabled, the lock screen only shows that an experience is in progress, without substance names or the chart. Details are shown after unlocking.",
                    checked = isLiveShownOnLockScreen,
                    onCheckedChange = saveIsLiveShownOnLockScreen
                )
```

- [ ] **Step 5: Auto-start after adding an ingestion.**
  - Add `private val liveNotificationController: LiveNotificationController` to `FinishIngestionScreenViewModel`'s constructor.
  - In `createAndSaveIngestion()`, after `experienceRepo.insertIngestionExperienceAndCompanion(...)`, add `liveNotificationController.onIngestionLogged(newExperience.id, newIngestion.time)`.
  - After `experienceRepo.insertIngestionAndCompanion(...)`, add `liveNotificationController.onIngestionLogged(oldIdToUse, newIngestion.time)`.

  `onIngestionLogged` launches on the controller's own scope and never throws into the save, so a failure can't make the save guard retry and duplicate the ingestion.

- [ ] **Step 6: Auto-start after a quick log.**
  - Add `private val liveNotificationController: LiveNotificationController` to `PlannedIngestionHelper`'s constructor.
  - In `log(...)`, capture `val now = Instant.now()` before `logGuard.save(...)` and pass `now = now` to `logPlannedIngestion`.
  - In `onSaved`, before `onLogged()`, add `liveNotificationController.onIngestionLogged(experienceId, now)`.

- [ ] **Step 7: Build and run the suite**

Run: `./gradlew :app:assembleDebug -q && ./gradlew :app:testDebugUnitTest -q`
Expected: the build succeeds and the suite is green except the three known failures. `AutoStartTest` passes.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/SettingsViewModel.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/SettingsScreen.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/addingestion/time/FinishIngestionScreenViewModel.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestionActions.kt app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/AutoStartTest.kt
git commit -m "Add the live notification settings and start it after logging"
```

---

### Task 11: Check on the emulator

**Files:** none. Fixes found here get their own failing test where the code is pure, and their own commit.

The user may be using the emulator (`emulator-5554`) at the same time:
- Use only a test experience you create, and delete it at the end.
- Never change the user's own settings without restoring them.
- adb lives at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`; prefix commands with `MSYS_NO_PATHCONV=1` in Git Bash.
- The UI driver is `python -I .superpowers/ui.py dump|tap|switches`.

- [ ] **Step 1:** Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2:** Log caffeine "now" in a **new** experience titled `LiveTest`. Open it, check that the "Live notification" switch is shown, and turn it on. If Android asks, allow notifications. Expected: the notification appears.
- [ ] **Step 3:** Pull down the shade (`adb shell cmd statusbar expand-notifications`) and take a screenshot. Check the collapsed line text, then expand the notification and check the chart with its "now" line and the three actions. "Log planned" only appears with a pinned plan.
- [ ] **Step 4:** Pin a redose plan on `LiveTest`. Expected: the notification gains the planned line and "Log planned". Tap "Log planned". Expected: the app opens on `LiveTest` with the "Log now?" dialog. Cancel it.
- [ ] **Step 5:** Tap "Add ingestion". Expected: the add flow opens. Go back.
- [ ] **Step 6:** Lock screen: run `adb shell input keyevent 26` twice and take a screenshot. Expected: only "Experience in progress". Turn the lock-screen setting on, lock again, and check that the details show. Restore the setting to off.
- [ ] **Step 7:** Tap "Stop". Expected: the notification disappears and the switch turns off. Turn it on again, swipe the notification away, and expect the switch to turn off.
- [ ] **Step 8:** Turn it on again, then delete `LiveTest`. Expected: the notification disappears.
- [ ] **Step 9:** Note in the ledger anything that didn't match. Fix each item with a test where the code is pure. Re-run the suite.

---
