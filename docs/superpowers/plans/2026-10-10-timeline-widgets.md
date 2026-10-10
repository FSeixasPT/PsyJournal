# Home-Screen Timeline Widgets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three home-screen widgets ("Timeline", "Timeline chart", "Timeline status") that show the user's current experience, built from the live notification's content and chart.

**Architecture:**
- **Pure logic:** a pure Kotlin layer (`WidgetContent.kt`) chooses the experience and builds the content by calling `buildLiveNotificationContent`.
- **Rendering:** a renderer fills `RemoteViews` for the three kinds, and three thin `AppWidgetProvider` subclasses forward to a Hilt singleton `WidgetUpdater`.
- **Refreshing:** `WidgetUpdater` refreshes every placed widget. It observes the journal while the app runs and schedules inexact alarms during an active experience, the same way as `LiveNotificationController`.

**Tech Stack:** Kotlin, Android `AppWidgetProvider` / `RemoteViews`, Hilt (entry points for receivers), Room flows, Jetpack DataStore (`UserPreferences`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-10-timeline-widgets-design.md`

## Global Constraints

- No new library dependencies. No Jetpack Glance.
- `minSdk` 26. `previewLayout` is used from Android 12 (API 31); below that, `previewImage` is the fallback.
- Only the user's own ingestions (consumer `null`). `myEffectLines` already filters this.
- Experiences are considered only if their sort date is in [now − 3 days, now + 1 day].
- Refresh during an active experience at `LiveNotificationContent.nextRefreshAt` (1 to 5 minutes). There's no alarm when nothing is active. The `updatePeriodMillis` fallback is `1800000`.
- Alarms are inexact (`setAndAllowWhileIdle`). No exact-alarm permission.
- A failed refresh keeps the last content and retries after 5 minutes. A failure must never crash the app (use `runSafely`).
- Substance names are always shown. There's no "Log planned" on widgets and no lock-screen category.
- Widget pending intents must not reuse the notification's request codes (1, 2, 3, 10, 11, 12).
- Every new Kotlin file starts with the GPL header used by `livenotification/*.kt` ("Copyright (c) 2026. Francisco Seixas. This file is part of PsyJournal…").
- Build with `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"`. 3 inherited unit tests fail (`TestDates.dateDifferences`, `TestParse.testExtractSubstancesString`, `TestParse.noCrash`); ignore them.
- Every user-facing change gets a `CHANGELOG.md` entry under "## Unreleased" in the commit that finishes it.

## Review Focus

- **No experience at all** (fresh install, or a journal with only old experiences). Every widget shows "No active experience", the chart widget shows no chart, and no alarm is scheduled. Pinned by `WidgetContentTest.noExperiencesGiveNothingActive` and `WidgetRefreshTest.nothingActiveSchedulesNoAlarm` (Tasks 1 and 3).
- **The newest experience has ended but an older one is still running.** The older one is shown. Pinned by `WidgetContentTest.anEndedNewerExperienceIsSkipped` (Task 1).
- **A tiny or unknown widget size** (options missing, or a 1-cell height). The chart bitmap is never 0 px; drawing with `ImageBitmap(0, …)` throws. Pinned by `WidgetSizeTest` (Task 2).
- **All widgets removed.** The alarm is cancelled and nothing keeps waking the phone. Pinned by `WidgetRefreshTest.noWidgetsSchedulesNoAlarm` (Task 3).
- **Switching the system theme.** RemoteViews colors follow `values-night` straight away. The chart bitmap is redrawn at the next refresh, within 5 minutes during an active experience. This is accepted and checked on the emulator in Task 4.

---

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetContent.kt` | Pure: `WidgetCandidate`, `WidgetSettings`, `WidgetContent`, `chooseWidgetExperience`, `buildWidgetContent` |
| `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetSize.kt` | Pure: `WidgetKind`, `chartSizePx` |
| `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetRefresh.kt` | Pure: `widgetRefreshAt` |
| `app/src/main/java/com/isaakhanimann/journal/ui/widget/TimelineWidgetRenderer.kt` | Fills `RemoteViews` for one widget |
| `app/src/main/java/com/isaakhanimann/journal/ui/widget/TimelineWidgetProvider.kt` | The base provider, the three subclasses, and the entry point |
| `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetUpdater.kt` | Singleton: loads data, renders all widgets, schedules the alarm, observes changes |
| `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetRefreshReceiver.kt` | Alarm target |
| `app/src/main/res/layout/widget_full.xml`, `widget_chart.xml`, `widget_status.xml` | Widget layouts |
| `app/src/main/res/layout/widget_full_preview.xml`, `widget_chart_preview.xml`, `widget_status_preview.xml` | Static picker previews |
| `app/src/main/res/drawable/widget_background.xml`, `widget_preview_chart.xml` | Rounded background, sample curve for previews |
| `app/src/main/res/values/widget_colors.xml`, `app/src/main/res/values-night/widget_colors.xml` | Light and dark widget colors |
| `app/src/main/res/xml/widget_full_info.xml`, `widget_chart_info.xml`, `widget_status_info.xml` | `appwidget-provider` metadata |
| `app/src/main/res/values/strings.xml` | Widget names and descriptions |
| `app/src/main/AndroidManifest.xml` | Three widget receivers, the refresh receiver |
| `app/src/main/java/com/isaakhanimann/journal/di/JournalApplication.kt` | Starts `WidgetUpdater.observe()` |
| Tests: `app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetContentTest.kt`, `WidgetSizeTest.kt`, `WidgetRefreshTest.kt` | Unit tests |

---

### Task 1: Choosing the experience and building the content

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetContent.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetContentTest.kt`

**Interfaces:**
- Consumes:
  - `buildLiveNotificationContent(title: String, model: AllTimelinesModel, lines: List<DataForOneEffectLine>, plannedIngestion: PlannedIngestion?, savedTimeDisplayOption: SavedTimeDisplayOption, now: Instant): LiveNotificationContent` and `hasActiveEffects(lines, areSubstanceHeightsIndependent, areRoutesMerged, now): Boolean`, both in `ui.tabs.journal.experience.livenotification`.
  - `AllTimelinesModel(dataForLines, dataForRatings, timedNotes, areSubstanceHeightsIndependent, areRoutesMerged)`.
- Produces:
  - `data class WidgetCandidate(val experience: Experience, val lines: List<DataForOneEffectLine>)`
  - `data class WidgetSettings(val areSubstanceHeightsIndependent: Boolean, val areRoutesMerged: Boolean, val savedTimeDisplayOption: SavedTimeDisplayOption)`
  - `sealed interface WidgetContent` with `data class Active(experienceId: Int, title: String, lines: List<SubstanceLine>, model: AllTimelinesModel, nextRefreshAt: Instant)` and `data object NothingActive`
  - `fun chooseWidgetExperience(candidatesNewestFirst: List<WidgetCandidate>, settings: WidgetSettings, now: Instant): WidgetCandidate?`
  - `fun buildWidgetContent(chosen: WidgetCandidate?, settings: WidgetSettings, now: Instant): WidgetContent`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetContentTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.widget

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.data.substances.classes.roa.DurationUnits
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class WidgetContentTest {
    private val start = Instant.parse("2026-01-01T20:00:00Z")
    private fun at(minutes: Long) = start.plus(minutes, ChronoUnit.MINUTES)
    private fun minutes(value: Float) = DurationRange(value, value, DurationUnits.MINUTES)
    // 30 min onset, 30 come-up, 60 peak, 60 offset: active from 0 to 180 minutes
    private val duration = RoaDuration(minutes(30f), minutes(30f), minutes(60f), minutes(60f), null, null)
    private val settings = WidgetSettings(
        areSubstanceHeightsIndependent = false,
        areRoutesMerged = false,
        savedTimeDisplayOption = SavedTimeDisplayOption.AUTO
    )

    private fun line(minutesAfterStart: Long) = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = duration,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = at(minutesAfterStart),
        endTime = null,
    )

    private fun candidate(id: Int, vararg lineStarts: Long) = WidgetCandidate(
        experience = Experience(id = id, title = "Experience $id", text = "", sortDate = at(lineStarts.minOrNull() ?: 0), location = null),
        lines = lineStarts.map { line(it) }
    )

    @Test
    fun anActiveExperienceIsChosen() {
        val chosen = chooseWidgetExperience(listOf(candidate(1, 0)), settings, at(90))
        assertEquals(1, chosen?.experience?.id)
    }

    @Test
    fun anEndedNewerExperienceIsSkipped() {
        // newest first: experience 2 started at -300 and ended at -120, experience 1 started at 0 and runs until 180
        val chosen = chooseWidgetExperience(listOf(candidate(2, -300), candidate(1, 0)), settings, at(90))
        assertEquals(1, chosen?.experience?.id)
    }

    @Test
    fun anExperienceWithoutLinesOfTheUserIsNotActive() {
        // myEffectLines drops friends' ingestions, so such an experience arrives with no lines
        assertNull(chooseWidgetExperience(listOf(candidate(1)), settings, at(90)))
    }

    @Test
    fun noExperiencesGiveNothingActive() {
        assertNull(chooseWidgetExperience(emptyList(), settings, at(90)))
        assertEquals(WidgetContent.NothingActive, buildWidgetContent(null, settings, at(90)))
    }

    @Test
    fun activeContentCarriesTheNotificationLines() {
        val content = buildWidgetContent(candidate(1, 0), settings, at(90)) as WidgetContent.Active
        assertEquals(1, content.experienceId)
        assertEquals("Experience 1", content.title)
        assertEquals("TestSubstance · peaking · 100% · comes down in 30 min", content.lines.single().text)
        assertTrue(content.nextRefreshAt > at(90))
    }

    @Test
    fun anExperienceThatEndedGivesNothingActive() {
        assertEquals(WidgetContent.NothingActive, buildWidgetContent(candidate(1, 0), settings, at(200)))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest --tests "*WidgetContentTest" -q`
Expected: compile failure, "Unresolved reference 'WidgetSettings'".

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetContent.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.widget

import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.SubstanceLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.buildLiveNotificationContent
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.hasActiveEffects
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import java.time.Instant

// an experience and the user's own effect lines in it, from myEffectLines
data class WidgetCandidate(val experience: Experience, val lines: List<DataForOneEffectLine>)

data class WidgetSettings(
    val areSubstanceHeightsIndependent: Boolean,
    val areRoutesMerged: Boolean,
    val savedTimeDisplayOption: SavedTimeDisplayOption,
)

sealed interface WidgetContent {
    data class Active(
        val experienceId: Int,
        val title: String,
        val lines: List<SubstanceLine>,
        val model: AllTimelinesModel,
        val nextRefreshAt: Instant,
    ) : WidgetContent

    data object NothingActive : WidgetContent
}

// the most recent experience whose estimated effects haven't ended, so an ended newer one doesn't hide a running older one
fun chooseWidgetExperience(candidatesNewestFirst: List<WidgetCandidate>, settings: WidgetSettings, now: Instant): WidgetCandidate? =
    candidatesNewestFirst.firstOrNull {
        hasActiveEffects(it.lines, settings.areSubstanceHeightsIndependent, settings.areRoutesMerged, now)
    }

fun buildWidgetContent(chosen: WidgetCandidate?, settings: WidgetSettings, now: Instant): WidgetContent {
    if (chosen == null || chosen.lines.isEmpty()) return WidgetContent.NothingActive
    val model = AllTimelinesModel(
        dataForLines = chosen.lines,
        dataForRatings = emptyList(),
        timedNotes = emptyList(),
        areSubstanceHeightsIndependent = settings.areSubstanceHeightsIndependent,
        areRoutesMerged = settings.areRoutesMerged
    )
    // the widgets show no plan, see the spec
    val content = buildLiveNotificationContent(
        title = chosen.experience.title,
        model = model,
        lines = chosen.lines,
        plannedIngestion = null,
        savedTimeDisplayOption = settings.savedTimeDisplayOption,
        now = now
    )
    if (content.isFinished) return WidgetContent.NothingActive
    return WidgetContent.Active(chosen.experience.id, content.title, content.lines, model, content.nextRefreshAt)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest --tests "*WidgetContentTest" -q`
Expected: PASS, with no output from `-q`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetContent.kt app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetContentTest.kt
git commit -m "Choose the widget experience and build its content"
```

---

### Task 2: Widget layouts, renderer and providers

At the end of this task, the three widgets can be placed and show the current content when Android asks them to update (placing, resizing, the 30-minute heartbeat). Refreshing on journal changes and by alarm comes in Task 3.

**Files:**
- Create:
  - `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetSize.kt`
  - `app/src/main/java/com/isaakhanimann/journal/ui/widget/TimelineWidgetRenderer.kt`
  - `app/src/main/java/com/isaakhanimann/journal/ui/widget/TimelineWidgetProvider.kt`
  - `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetUpdater.kt`
- Create the resources: the layouts `widget_full.xml`, `widget_chart.xml` and `widget_status.xml`; `drawable/widget_background.xml`; `values/widget_colors.xml` and `values-night/widget_colors.xml`; and `xml/widget_full_info.xml`, `xml/widget_chart_info.xml` and `xml/widget_status_info.xml`.
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetSizeTest.kt`

**Interfaces:**
- Consumes:
  - from Task 1: `WidgetContent`, `WidgetCandidate`, `WidgetSettings`, `chooseWidgetExperience` and `buildWidgetContent`;
  - `drawLiveChart(model, now, widthPx, heightPx, density: androidx.compose.ui.unit.Density, isDarkTheme): Bitmap?`;
  - `LiveNotificationIntents.mainActivityIntent` is private, so this task adds a public `openFromWidget(context, requestCode, experienceId: Int?)` and `addIngestionFromWidget(context, requestCode)` next to it;
  - `myEffectLines(ingestionsWithCompanions, substanceRepo)`;
  - `ExperienceRepository.getSortedExperiencesWithIngestionsWithSortDateBetween(from, to)` and `getIngestionsWithCompanions(experienceId)`;
  - `UserPreferences.areSubstanceHeightsIndependentFlow`, `areRoutesMergedFlow` and `savedTimeDisplayOptionFlow`.
- Produces:
  - `enum class WidgetKind { FULL, CHART, STATUS }`
  - `fun chartSizePx(kind: WidgetKind, widthDp: Int?, heightDp: Int?, density: Float): Pair<Int, Int>`
  - `class WidgetUpdater` with `suspend fun refreshAll()`
  - `abstract class TimelineWidgetProvider(val kind: WidgetKind)` and the subclasses `FullTimelineWidget`, `ChartTimelineWidget` and `StatusTimelineWidget`
  - `interface WidgetEntryPoint { fun widgetUpdater(): WidgetUpdater }`

- [ ] **Step 1: Write the failing size test**

`app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetSizeTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun theChartFillsTheWidthAndTheSpaceLeftByTheText() {
        // 300 x 180 dp at density 2; the full widget keeps 92 dp for the title and lines and 24 dp of padding
        assertEquals(Pair(552, 128), chartSizePx(WidgetKind.FULL, 300, 180, 2f))
        // the chart widget only keeps 28 dp for the title and 24 dp of padding
        assertEquals(Pair(552, 256), chartSizePx(WidgetKind.CHART, 300, 180, 2f))
    }

    @Test
    fun anUnknownSizeUsesTheDefaultSize() {
        // the default is 4 x 2 cells, about 250 x 110 dp
        assertEquals(chartSizePx(WidgetKind.FULL, 250, 110, 2f), chartSizePx(WidgetKind.FULL, null, null, 2f))
    }

    @Test
    fun theChartIsNeverEmpty() {
        val (width, height) = chartSizePx(WidgetKind.FULL, 40, 40, 1f)
        assertTrue(width >= 48 && height >= 24)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest --tests "*WidgetSizeTest" -q`
Expected: compile failure, "Unresolved reference 'chartSizePx'".

- [ ] **Step 3: Write `WidgetSize.kt`**

```kotlin
package com.isaakhanimann.journal.ui.widget

import kotlin.math.roundToInt

enum class WidgetKind { FULL, CHART, STATUS }

private const val defaultWidthDp = 250
private const val defaultHeightDp = 110
private const val paddingDp = 24
private const val minimumWidthPx = 48
private const val minimumHeightPx = 24

// the chart bitmap size for a widget of this size in dp; drawing a 0 px bitmap would throw
fun chartSizePx(kind: WidgetKind, widthDp: Int?, heightDp: Int?, density: Float): Pair<Int, Int> {
    val width = widthDp ?: defaultWidthDp
    val height = heightDp ?: defaultHeightDp
    // the title row, and for the full widget the substance lines below the chart
    val textDp = if (kind == WidgetKind.FULL) 92 else 28
    val widthPx = ((width - paddingDp) * density).roundToInt().coerceAtLeast(minimumWidthPx)
    val heightPx = ((height - paddingDp - textDp) * density).roundToInt().coerceAtLeast(minimumHeightPx)
    return Pair(widthPx, heightPx)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest --tests "*WidgetSizeTest" -q`
Expected: PASS.

- [ ] **Step 5: Add the colors, background and strings**

`app/src/main/res/values/widget_colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="widget_background">#F2FFFFFF</color>
    <color name="widget_text">#1B1B1F</color>
    <color name="widget_text_secondary">#5F5E66</color>
    <color name="widget_accent">#4F5BD5</color>
</resources>
```

`app/src/main/res/values-night/widget_colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="widget_background">#F21B1B1F</color>
    <color name="widget_text">#E4E1E6</color>
    <color name="widget_text_secondary">#C7C5D0</color>
    <color name="widget_accent">#BAC3FF</color>
</resources>
```

`app/src/main/res/drawable/widget_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/widget_background" />
    <corners android:radius="16dp" />
</shape>
```

Add to `app/src/main/res/values/strings.xml`, inside `<resources>`:

```xml
    <string name="widget_full_name">Timeline</string>
    <string name="widget_full_description">Chart and phases of the current experience</string>
    <string name="widget_chart_name">Timeline chart</string>
    <string name="widget_chart_description">Chart of the current experience</string>
    <string name="widget_status_name">Timeline status</string>
    <string name="widget_status_description">Phase of each substance in the current experience</string>
    <string name="widget_loading">Loading…</string>
    <string name="widget_nothing_active">No active experience</string>
```

- [ ] **Step 6: Add the three layouts**

`app/src/main/res/layout/widget_full.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:orientation="vertical"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="28dp"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/widget_title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:maxLines="1"
            android:text="@string/widget_loading"
            android:textColor="@color/widget_text"
            android:textSize="15sp"
            android:textStyle="bold" />

        <ImageView
            android:id="@+id/widget_add"
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:contentDescription="Add ingestion"
            android:padding="4dp"
            android:src="@drawable/ic_widget_add" />
    </LinearLayout>

    <ImageView
        android:id="@+id/widget_chart"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:contentDescription="Timeline"
        android:scaleType="fitXY" />

    <TextView
        android:id="@+id/widget_lines"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:ellipsize="end"
        android:maxLines="4"
        android:textColor="@color/widget_text_secondary"
        android:textSize="13sp" />
</LinearLayout>
```

`app/src/main/res/layout/widget_chart.xml`: the same as `widget_full.xml` but **without** `widget_add` and **without** `widget_lines`. Write it out in full:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:id="@+id/widget_title"
        android:layout_width="match_parent"
        android:layout_height="28dp"
        android:ellipsize="end"
        android:gravity="center_vertical"
        android:maxLines="1"
        android:text="@string/widget_loading"
        android:textColor="@color/widget_text"
        android:textSize="15sp"
        android:textStyle="bold" />

    <ImageView
        android:id="@+id/widget_chart"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:contentDescription="Timeline"
        android:scaleType="fitXY" />
</LinearLayout>
```

`app/src/main/res/layout/widget_status.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:padding="12dp">

    <TextView
        android:id="@+id/widget_lines"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:ellipsize="end"
        android:maxLines="6"
        android:text="@string/widget_loading"
        android:textColor="@color/widget_text"
        android:textSize="13sp" />

    <ImageView
        android:id="@+id/widget_add"
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:layout_gravity="top"
        android:contentDescription="Add ingestion"
        android:padding="4dp"
        android:src="@drawable/ic_widget_add" />
</LinearLayout>
```

`app/src/main/res/drawable/ic_widget_add.xml` (a plus icon in the accent color):

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/widget_accent"
        android:pathData="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
</vector>
```

- [ ] **Step 7: Add the provider metadata**

`app/src/main/res/xml/widget_full_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_full_description"
    android:initialLayout="@layout/widget_full"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:minResizeWidth="180dp"
    android:minResizeHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen" />
```

`app/src/main/res/xml/widget_chart_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_chart_description"
    android:initialLayout="@layout/widget_chart"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="40dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen" />
```

`app/src/main/res/xml/widget_status_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_status_description"
    android:initialLayout="@layout/widget_status"
    android:minWidth="180dp"
    android:minHeight="40dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="40dp"
    android:targetCellWidth="3"
    android:targetCellHeight="1"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen" />
```

(The `previewLayout` and `previewImage` attributes are added in Task 4.)

- [ ] **Step 8: Add the widget intents to `LiveNotificationIntents`**

In `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/LiveNotificationIntents.kt`, add after `addIngestion`:

```kotlin
    // widgets reuse the notification's targets with their own request codes, so neither replaces the other's intent
    fun openFromWidget(context: Context, requestCode: Int, experienceId: Int?): PendingIntent {
        val intent = mainActivityIntent(context)
        if (experienceId != null) {
            intent.putExtra(EXTRA_OPEN_EXPERIENCE_ID, experienceId).putExtra(EXTRA_OPEN_QUICK_LOG, false)
        }
        return PendingIntent.getActivity(context, requestCode, intent, FLAGS)
    }

    fun addIngestionFromWidget(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, mainActivityIntent(context).putExtra(EXTRA_ADD_INGESTION, true), FLAGS)
```

- [ ] **Step 9: Write the renderer**

`app/src/main/java/com/isaakhanimann/journal/ui/widget/TimelineWidgetRenderer.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.isaakhanimann.journal.R
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.LiveNotificationIntents
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.SubstanceLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.drawLiveChart
import java.time.Instant

// request codes 20+ so they never collide with the notification's 1-12
private const val REQUEST_OPEN = 20
private const val REQUEST_ADD = 21

fun renderTimelineWidget(
    context: Context,
    kind: WidgetKind,
    content: WidgetContent,
    widthDp: Int?,
    heightDp: Int?,
    now: Instant,
): RemoteViews {
    val layout = when (kind) {
        WidgetKind.FULL -> R.layout.widget_full
        WidgetKind.CHART -> R.layout.widget_chart
        WidgetKind.STATUS -> R.layout.widget_status
    }
    val isDarkTheme = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val views = RemoteViews(context.packageName, layout)
    val experienceId = (content as? WidgetContent.Active)?.experienceId
    views.setOnClickPendingIntent(R.id.widget_root, LiveNotificationIntents.openFromWidget(context, REQUEST_OPEN, experienceId))
    if (kind != WidgetKind.CHART) {
        views.setOnClickPendingIntent(R.id.widget_add, LiveNotificationIntents.addIngestionFromWidget(context, REQUEST_ADD))
    }
    when (content) {
        WidgetContent.NothingActive -> {
            val text = context.getString(R.string.widget_nothing_active)
            if (kind == WidgetKind.STATUS) {
                views.setTextViewText(R.id.widget_lines, text)
            } else {
                views.setTextViewText(R.id.widget_title, text)
                views.setViewVisibility(R.id.widget_chart, View.GONE)
                if (kind == WidgetKind.FULL) views.setTextViewText(R.id.widget_lines, "")
            }
        }

        is WidgetContent.Active -> {
            if (kind != WidgetKind.STATUS) {
                views.setTextViewText(R.id.widget_title, content.title)
                val density = context.resources.displayMetrics.density
                val (widthPx, heightPx) = chartSizePx(kind, widthDp, heightDp, density)
                val chart = drawLiveChart(content.model, now, widthPx, heightPx, Density(context), isDarkTheme)
                if (chart != null) {
                    views.setViewVisibility(R.id.widget_chart, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_chart, chart)
                } else {
                    views.setViewVisibility(R.id.widget_chart, View.GONE)
                }
            }
            if (kind != WidgetKind.CHART) {
                views.setTextViewText(R.id.widget_lines, linesText(content.lines, isDarkTheme))
            }
        }
    }
    return views
}

// a colored dot before each substance, like the notification's expanded view
private fun linesText(lines: List<SubstanceLine>, isDarkTheme: Boolean): CharSequence {
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
    return text
}
```

- [ ] **Step 10: Write `WidgetUpdater` (rendering only for now)**

`app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetUpdater.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.runSafely
import com.isaakhanimann.journal.ui.tabs.journal.experience.myEffectLines
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    private val userPreferences: UserPreferences,
) {
    private val mutex = Mutex()

    // renders every placed widget of every kind from one content
    suspend fun refreshAll() = runSafely(onFailure = { Log.w("Widgets", "Widget refresh failed", it) }) {
        mutex.withLock { refreshLocked() }
    }

    private suspend fun refreshLocked() {
        val manager = AppWidgetManager.getInstance(context)
        val idsByKind = WidgetKind.entries.associateWith { manager.getAppWidgetIds(ComponentName(context, providerClass(it))) }
        if (idsByKind.values.all { it.isEmpty() }) return
        val now = Instant.now()
        val content = loadContent(now)
        idsByKind.forEach { (kind, ids) ->
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).takeIf { it > 0 }
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 }
                manager.updateAppWidget(id, renderTimelineWidget(context, kind, content, widthDp, heightDp, now))
            }
        }
    }

    private suspend fun loadContent(now: Instant): WidgetContent {
        val settings = WidgetSettings(
            areSubstanceHeightsIndependent = userPreferences.areSubstanceHeightsIndependentFlow.first(),
            areRoutesMerged = userPreferences.areRoutesMergedFlow.first(),
            savedTimeDisplayOption = userPreferences.savedTimeDisplayOptionFlow.first(),
        )
        val experiences = experienceRepo.getSortedExperiencesWithIngestionsWithSortDateBetween(
            fromInstant = now - Duration.ofDays(3),
            toInstant = now + Duration.ofDays(1)
        ).sortedByDescending { it.experience.sortDate }
        // loaded one at a time, newest first, and stops at the first active one
        var chosen: WidgetCandidate? = null
        for (experience in experiences) {
            val candidate = WidgetCandidate(
                experience.experience,
                myEffectLines(experienceRepo.getIngestionsWithCompanions(experience.experience.id), substanceRepo)
            )
            if (chooseWidgetExperience(listOf(candidate), settings, now) != null) {
                chosen = candidate
                break
            }
        }
        return buildWidgetContent(chosen, settings, now)
    }
}

fun providerClass(kind: WidgetKind): Class<out TimelineWidgetProvider> = when (kind) {
    WidgetKind.FULL -> FullTimelineWidget::class.java
    WidgetKind.CHART -> ChartTimelineWidget::class.java
    WidgetKind.STATUS -> StatusTimelineWidget::class.java
}
```

- [ ] **Step 11: Write the providers**

`app/src/main/java/com/isaakhanimann/journal/ui/widget/TimelineWidgetProvider.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
}

// every trigger refreshes all widgets, so the three kinds always show the same experience
abstract class TimelineWidgetProvider(val kind: WidgetKind) : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            refresh(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        refresh(context)

    override fun onDisabled(context: Context) = refresh(context)

    private fun refresh(context: Context) {
        // an entry point instead of field injection, like the live notification's receiver
        val updater = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).widgetUpdater()
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updater.refreshAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class FullTimelineWidget : TimelineWidgetProvider(WidgetKind.FULL)
class ChartTimelineWidget : TimelineWidgetProvider(WidgetKind.CHART)
class StatusTimelineWidget : TimelineWidgetProvider(WidgetKind.STATUS)
```

- [ ] **Step 12: Register them in the manifest**

In `app/src/main/AndroidManifest.xml`, inside `<application>` after the `LiveNotificationReceiver`:

```xml
        <receiver
            android:name=".ui.widget.FullTimelineWidget"
            android:exported="false"
            android:label="@string/widget_full_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_full_info" />
        </receiver>
        <receiver
            android:name=".ui.widget.ChartTimelineWidget"
            android:exported="false"
            android:label="@string/widget_chart_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_chart_info" />
        </receiver>
        <receiver
            android:name=".ui.widget.StatusTimelineWidget"
            android:exported="false"
            android:label="@string/widget_status_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_status_info" />
        </receiver>
```

`Intent.ACTION_TIME_CHANGED` is the constant for `"android.intent.action.TIME_SET"`.

- [ ] **Step 13: Build and run all unit tests**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest assembleDebug --continue -q`
Expected: no `e:` lines. The only failures are the 3 inherited tests.

- [ ] **Step 14: Check on the emulator**

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
```

Long-press the home screen, open Widgets, find PsyJournal and place "Timeline", "Timeline chart" and "Timeline status". Each one shows either the current experience or "No active experience", never a blank or "Loading…" for more than a moment. Tapping a widget opens the experience (or the journal), and + opens the add flow.

- [ ] **Step 15: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/widget app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetSizeTest.kt app/src/main/res app/src/main/AndroidManifest.xml app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/LiveNotificationIntents.kt
git commit -m "Add the three timeline widgets"
```

---

### Task 3: Refreshing on journal changes and by alarm

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetRefresh.kt`, `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetRefreshReceiver.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetUpdater.kt`, `app/src/main/java/com/isaakhanimann/journal/di/JournalApplication.kt`, `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetRefreshTest.kt`

**Interfaces:**
- Consumes: from Task 2, `WidgetUpdater.refreshAll()`, `WidgetEntryPoint` and `providerClass(kind)`; from Task 1, `WidgetContent`.
- Produces:
  - `fun widgetRefreshAt(content: WidgetContent, hasWidgets: Boolean): Instant?`
  - `WidgetUpdater.observe()`
  - `WidgetRefreshReceiver` handling `WidgetRefreshReceiver.ACTION_REFRESH = "com.isaakhanimann.journal.widget.REFRESH"`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetRefreshTest.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.widget

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WidgetRefreshTest {
    private val next = Instant.parse("2026-01-01T20:05:00Z")
    // the model's content doesn't matter here, only the content's kind and refresh time
    private val line = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = null,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = Instant.parse("2026-01-01T20:00:00Z"),
        endTime = null,
    )
    private val active = WidgetContent.Active(
        experienceId = 1,
        title = "Experience",
        lines = emptyList(),
        model = AllTimelinesModel(listOf(line), emptyList(), emptyList(), false, false),
        nextRefreshAt = next
    )

    @Test
    fun anActiveExperienceRefreshesAtTheNextChange() {
        assertEquals(next, widgetRefreshAt(active, hasWidgets = true))
    }

    @Test
    fun nothingActiveSchedulesNoAlarm() {
        assertNull(widgetRefreshAt(WidgetContent.NothingActive, hasWidgets = true))
    }

    @Test
    fun noWidgetsSchedulesNoAlarm() {
        assertNull(widgetRefreshAt(active, hasWidgets = false))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest --tests "*WidgetRefreshTest" -q`
Expected: compile failure, "Unresolved reference 'widgetRefreshAt'".

- [ ] **Step 3: Write `WidgetRefresh.kt`**

```kotlin
package com.isaakhanimann.journal.ui.widget

import java.time.Instant

// alarms only while a widget shows an active experience, so nothing wakes the phone otherwise
fun widgetRefreshAt(content: WidgetContent, hasWidgets: Boolean): Instant? =
    if (hasWidgets && content is WidgetContent.Active) content.nextRefreshAt else null
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest --tests "*WidgetRefreshTest" -q`
Expected: PASS.

- [ ] **Step 5: Write the alarm receiver**

`app/src/main/java/com/isaakhanimann/journal/ui/widget/WidgetRefreshReceiver.kt` (GPL header first):

```kotlin
package com.isaakhanimann.journal.ui.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return
        val updater = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).widgetUpdater()
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updater.refreshAll()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.isaakhanimann.journal.widget.REFRESH"

        // request code 22, after the widget's open and add intents
        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            22,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
```

Manifest, after the widget receivers:

```xml
        <receiver
            android:name=".ui.widget.WidgetRefreshReceiver"
            android:exported="false" />
```

- [ ] **Step 6: Schedule the alarm and observe changes in `WidgetUpdater`**

Add these imports to `WidgetUpdater.kt`: `android.app.AlarmManager`, `kotlinx.coroutines.CoroutineExceptionHandler`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.FlowPreview`, `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.flow.combine`, `kotlinx.coroutines.flow.debounce`, `kotlinx.coroutines.launch`.

Replace `refreshAll` and `refreshLocked` with:

```kotlin
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w("Widgets", "Widget update failed", error)
        }
    )

    // a failed refresh is tried again later, so the widgets never stay frozen
    suspend fun refreshAll() = runSafely(
        onFailure = { error ->
            Log.w("Widgets", "Widget refresh failed", error)
            scheduleRefresh(Instant.now() + retryAfterFailure)
        }
    ) {
        mutex.withLock { refreshLocked() }
    }

    // keeps the widgets in step with the journal while the app process runs
    @OptIn(FlowPreview::class)
    fun observe() {
        scope.launch {
            combine(
                experienceRepo.getSortedExperiencesWithIngestionsFlow(),
                userPreferences.savedTimeDisplayOptionFlow,
                userPreferences.areRoutesMergedFlow,
                userPreferences.areSubstanceHeightsIndependentFlow,
            ) { _, _, _, _ -> }
                .debounce(500)
                .collect { refreshAll() }
        }
    }

    private suspend fun refreshLocked() {
        val manager = AppWidgetManager.getInstance(context)
        val idsByKind = WidgetKind.entries.associateWith { manager.getAppWidgetIds(ComponentName(context, providerClass(it))) }
        val hasWidgets = idsByKind.values.any { it.isNotEmpty() }
        if (!hasWidgets) {
            cancelRefresh()
            return
        }
        val now = Instant.now()
        val content = loadContent(now)
        idsByKind.forEach { (kind, ids) ->
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).takeIf { it > 0 }
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 }
                manager.updateAppWidget(id, renderTimelineWidget(context, kind, content, widthDp, heightDp, now))
            }
        }
        val at = widgetRefreshAt(content, hasWidgets)
        if (at != null) scheduleRefresh(at) else cancelRefresh()
    }

    // inexact, so no exact-alarm permission is needed
    private fun scheduleRefresh(at: Instant) {
        alarmManager().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WidgetRefreshReceiver.pendingIntent(context))
    }

    private fun cancelRefresh() = alarmManager().cancel(WidgetRefreshReceiver.pendingIntent(context))

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)
```

Add at file level, after the class:

```kotlin
private val retryAfterFailure: Duration = Duration.ofMinutes(5)
```

- [ ] **Step 7: Start observing in the application**

In `app/src/main/java/com/isaakhanimann/journal/di/JournalApplication.kt`:

```kotlin
@HiltAndroidApp
class JournalApplication : Application() {
    @Inject
    lateinit var liveNotificationController: LiveNotificationController

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    override fun onCreate() {
        super.onCreate()
        liveNotificationController.observe()
        widgetUpdater.observe()
    }
}
```

Add `import com.isaakhanimann.journal.ui.widget.WidgetUpdater`.

- [ ] **Step 8: Build and run all unit tests**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest assembleDebug --continue -q`
Expected: no `e:` lines. The only failures are the 3 inherited tests.

- [ ] **Step 9: Check on the emulator**

Install, keep the three widgets placed, and log an ingestion of a substance with duration data, taken now, from the app. Go home: every widget shows the new experience within about a second. Then check the alarm:

```bash
"$ADB" shell dumpsys alarm | grep -A3 "com.isaakhanimann.journal.widget.REFRESH"
```

Expected: one pending alarm while the experience is active. Delete the test ingestion afterwards. When nothing is active, the same command shows no alarm.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/ui/widget app/src/test/java/com/isaakhanimann/journal/ui/widget/WidgetRefreshTest.kt app/src/main/java/com/isaakhanimann/journal/di/JournalApplication.kt app/src/main/AndroidManifest.xml
git commit -m "Refresh the widgets on journal changes and by alarm"
```

---

### Task 4: Picker previews, changelog and final check

**Files:**
- Create: `app/src/main/res/layout/widget_full_preview.xml`, `widget_chart_preview.xml`, `widget_status_preview.xml`, and `app/src/main/res/drawable/widget_preview_chart.xml`
- Modify: `app/src/main/res/xml/widget_full_info.xml`, `widget_chart_info.xml`, `widget_status_info.xml`, `CHANGELOG.md`

**Interfaces:**
- Consumes: the layouts, colors and strings from Task 2.
- Produces: nothing used by code.

- [ ] **Step 1: Add the sample chart drawable**

`app/src/main/res/drawable/widget_preview_chart.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="240dp"
    android:height="80dp"
    android:viewportWidth="240"
    android:viewportHeight="80">
    <path
        android:fillColor="#335E5CE6"
        android:pathData="M4,76 L30,10 L90,10 L200,76 Z" />
    <path
        android:strokeColor="#5E5CE6"
        android:strokeWidth="3"
        android:pathData="M4,76 L30,10 L90,10 L200,76" />
    <path
        android:strokeColor="@color/widget_text"
        android:strokeWidth="2"
        android:pathData="M110,0 L110,80" />
</vector>
```

- [ ] **Step 2: Add the three preview layouts**

`app/src/main/res/layout/widget_full_preview.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="28dp"
        android:gravity="center_vertical"
        android:text="Saturday night"
        android:textColor="@color/widget_text"
        android:textSize="15sp"
        android:textStyle="bold" />

    <ImageView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:importantForAccessibility="no"
        android:scaleType="fitXY"
        android:src="@drawable/widget_preview_chart" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:text="● Substance · coming down · 60% · ends in 1.5 hr"
        android:textColor="@color/widget_text_secondary"
        android:textSize="13sp" />
</LinearLayout>
```

`app/src/main/res/layout/widget_chart_preview.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="28dp"
        android:gravity="center_vertical"
        android:text="Saturday night"
        android:textColor="@color/widget_text"
        android:textSize="15sp"
        android:textStyle="bold" />

    <ImageView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:importantForAccessibility="no"
        android:scaleType="fitXY"
        android:src="@drawable/widget_preview_chart" />
</LinearLayout>
```

`app/src/main/res/layout/widget_status_preview.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:gravity="center_vertical"
    android:padding="12dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="● Substance · coming down · 60% · ends in 1.5 hr"
        android:textColor="@color/widget_text"
        android:textSize="13sp" />
</LinearLayout>
```

The sample uses the word "Substance" on purpose, so the picker never shows a real substance name to someone browsing widgets.

- [ ] **Step 3: Point the metadata at the previews**

Add to `widget_full_info.xml`:

```xml
    android:previewLayout="@layout/widget_full_preview"
    android:previewImage="@drawable/widget_preview_chart"
```

Add to `widget_chart_info.xml`:

```xml
    android:previewLayout="@layout/widget_chart_preview"
    android:previewImage="@drawable/widget_preview_chart"
```

Add to `widget_status_info.xml`:

```xml
    android:previewLayout="@layout/widget_status_preview"
    android:previewImage="@drawable/widget_preview_chart"
```

- [ ] **Step 4: Add the changelog entry**

In `CHANGELOG.md`, under "## Unreleased" → "### Added", directly after the "Live timeline notification" entry and its sub-bullets:

```markdown
- **Home-screen widgets.** Follow the current experience from your home screen:
  - **Timeline:** the chart with a "now" line, and each substance's phase.
  - **Timeline chart:** only the chart.
  - **Timeline status:** only the phases, in a small widget.
  - The widgets show your most recent experience whose effects haven't ended. They update when you log, and every few minutes while the experience lasts.
  - Tapping a widget opens the experience. The + button opens the add-ingestion flow.
```

- [ ] **Step 5: Build, test and do the full emulator check**

Run: `JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest assembleDebug --continue -q`
Expected: no `e:` lines. The only failures are the 3 inherited tests.

On the emulator, check each of these:
- the widget picker shows the three previews;
- all three widgets work with and without an active experience;
- light and dark theme (`"$ADB" shell cmd uimode night yes` and `night no`). Text colors switch straight away, and the chart switches by the next refresh;
- resizing "Timeline" makes the chart redraw at the new size;
- the widget tap and the + button;
- the now line moves after an alarm refresh (wait about 5 minutes, or trigger it with `"$ADB" shell cmd alarm` / by logging).

Delete any test ingestions afterwards.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res CHANGELOG.md
git commit -m "Add widget previews and the changelog entry"
```
