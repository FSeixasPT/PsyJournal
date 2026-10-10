# Home-screen timeline widgets

Date: 2026-10-10
Status: Design approved in chat, awaiting spec review

## Goal

The user can follow the current experience from the home screen, without opening the app or turning on the live notification. There are three widgets in the launcher's widget picker. Each one shows a different amount of the same information. It's written independently from the closed-source official app; nothing is copied from it.

## Decisions taken in chat

- **Which experience:** picked automatically. It's the user's most recent experience with an ingestion of their own whose estimated effect hasn't ended. It doesn't depend on the live notification.
- **Three widgets instead of one with a setup screen:** "Timeline" (chart and lines), "Timeline chart" (chart only) and "Timeline status" (lines only). To change the content, the user removes one widget and adds another.
- **Substance names:** always shown. Putting a widget on the home screen is the user's choice. There's no setting to hide them.
- **Taps:** the widget opens the experience, or the journal when nothing is active. "Timeline" and "Timeline status" also have a + button that opens the add-ingestion flow.
- **Implementation:** classic `RemoteViews` widgets that reuse the live notification's content builder and chart drawing. No Jetpack Glance and no new library.

## Scope

**In:**

- the three widgets, resizable, in light and dark theme;
- refreshing while the app runs, by alarm during an active experience, and on Android's widget triggers;
- only the user's own ingestions (consumer `null`), like the main timeline and the live notification.

**Out:**

- choosing a particular experience for a widget;
- a "Log planned" button or the planned-ingestion line;
- hiding substance names;
- widgets on the lock screen (no `keyguard` category).

## What each widget shows

| Widget | Picker name | Content | Default size | Minimum size |
|---|---|---|---|---|
| Full | Timeline | title row with +, chart with now line, one line per substance | 4×2 cells | 3×2 |
| Chart | Timeline chart | title row, chart with now line | 4×2 cells | 2×1 |
| Status | Timeline status | one line per substance, + in the corner | 3×1 cells | 2×1 |

- **Title row:** the experience title, as in the journal.
- **Substance lines:** a color dot, then `SubstanceLine.text` from `buildLiveNotificationContent`. For example, "2C-B · coming down · 45% · ends in 40 min". The times use the user's saved time display option, mapped the same way as the notification (`liveTimeDisplayOption`). If there are more lines than fit, the list is cut at the bottom. Lines are in the order they're drawn on the chart.
- **Chart:** `drawLiveChart`, drawn at the widget's current size in pixels. The size is read from `AppWidgetManager.getAppWidgetOptions`, and if it's missing the default size is used. The chart is drawn again when the widget is resized.
- **Theme:** the background, text and chart colors follow the system's dark mode. The chart already takes an `isDarkTheme` flag, and the layouts use `values-night` colors.
- **No active experience:** the text "No active experience". "Timeline" and "Timeline status" keep the + button. "Timeline chart" shows only the text.
- **Before the first update:** the layout itself shows "Loading…", so a just-placed widget is never blank.

## Choosing the experience

`chooseWidgetExperience` (pure, unit-tested):

- **Input:** the experiences whose sort date is within the last 3 days, with their ingestions, newest first. These come from `getSortedExperiencesWithIngestionsWithSortDateBetween(now − 3 days, now + 1 day)`. The extra day covers an experience whose sort date is slightly in the future.
- **Rule:** the first experience, newest first, for which `hasActiveEffects` is true. It's computed on `myEffectLines` of its ingestions with the user's merge-routes and independent-heights settings, at `now`.
- **Output:** that experience, or none.

3 days is longer than any substance in the data stays active. An experience older than that, with nothing logged since, can't still be active.

## Components

All new code goes in the package `ui/widget/`. Each file has one job.

### 1. `WidgetContent.kt` (pure Kotlin, unit-tested)

```kotlin
sealed interface WidgetContent {
    data class Active(
        val experienceId: Int,
        val title: String,
        val lines: List<SubstanceLine>,
        val model: AllTimelinesModel,     // for the chart
        val nextRefreshAt: Instant,
    ) : WidgetContent
    data object NothingActive : WidgetContent
}
```

- `chooseWidgetExperience(...)`, as described above.
- `buildWidgetContent(...)`:
  - for the chosen experience, it calls `buildLiveNotificationContent` with no planned ingestion and wraps the result in `Active`;
  - with no chosen experience, it returns `NothingActive`.
- `nextRefreshAt` is the notification's (1 to 5 minutes, at the next phase change). `NothingActive` schedules no alarm.

### 2. `TimelineWidgetRenderer.kt`

- `render(context, kind: WidgetKind, content: WidgetContent, sizePx): RemoteViews`, where `enum class WidgetKind { FULL, CHART, STATUS }`.
- **Substance lines:** one `TextView` with a colored "●" span before each line, joined by line breaks, the way the notification's expanded view does it. `maxLines` cuts a long list at the bottom, so no collection adapter is needed.
- **Chart:** `setImageViewBitmap`. If `drawLiveChart` returns null, the chart view is hidden and the text stays.
- **Taps:** they reuse `LiveNotificationIntents`:
  - the root opens the experience (`NotificationTarget.Experience(id, openQuickLog = false)`), or the main activity when nothing is active;
  - + uses `NotificationTarget.AddIngestion`.

  So the existing `MainScreen` handling, including the conditions screen, applies unchanged. The widget pending intents use their own request codes, so they never replace the notification's.

### 3. `TimelineWidgetProvider.kt`

- An abstract `TimelineWidgetProvider(kind)` extending `AppWidgetProvider`, and three subclasses: `FullTimelineWidget`, `ChartTimelineWidget` and `StatusTimelineWidget`.
- `onUpdate`, `onAppWidgetOptionsChanged` (a resize), `onEnabled` and `onDisabled` all go to `WidgetUpdater`.
- It's a manifest-declared receiver, so it also handles `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED` by refreshing.
- Each subclass has `res/xml/widget_<kind>_info.xml`, with:
  - the min and target cell sizes from the table;
  - `resizeMode="horizontal|vertical"`;
  - `updatePeriodMillis="1800000"`, the 30-minute fallback;
  - `previewLayout` (Android 12+) and a `previewImage` fallback;
  - a description string.

### 4. `WidgetUpdater.kt` (Hilt `@Singleton`)

- `refreshAll()`:
  - builds the content once and renders every placed widget of every kind (`AppWidgetManager.getAppWidgetIds` per provider);
  - schedules the next refresh for `Active`, and cancels it for `NothingActive` or when no widgets are placed.
- **Concurrency and failures:** a `Mutex` keeps refreshes from overlapping. It uses `runSafely` from the live notification. If a refresh fails, the widgets keep their last content and a retry is scheduled 5 minutes later. A failure never crashes the app.
- `observe()`:
  - started from `JournalApplication.onCreate`, next to the live notification's `observe()`;
  - while the app process runs, it refreshes when any of these change: `getSortedExperiencesWithIngestionsFlow()`, which Room re-emits on any experience or ingestion change, the saved time display option, merge routes, or independent heights;
  - it's debounced by 500 ms, so a burst of changes refreshes once.
- **Alarm:** an inexact `setAndAllowWhileIdle` alarm to a new `ACTION_WIDGET_REFRESH` handled by a small receiver, like the notification's. No exact-alarm permission is needed.
- `onDisabled` (the last widget of a kind removed): `refreshAll` runs. It finds no widgets left and cancels the alarm.

## Refreshing

- **Journal or setting change while the app runs:** `observe()`, as described above. Logging from the app, editing, deleting and the planned-chip "I took it now" all go through the database, so they're covered.
- **During an active experience:** the alarm at `nextRefreshAt`, every 1 to 5 minutes. This keeps the now line, the percent and the "in 40 min" text current.
- **No active experience:** no alarm. The 30-minute `updatePeriodMillis` heartbeat and the triggers below pick up the next experience.
- **Android triggers:** a widget is placed, resized or restored, or the time or timezone changes.

## Error handling

- Drawing the chart fails: the chart is hidden and the text is shown.
- Building the content fails: the last content stays, and a retry is scheduled 5 minutes later.
- A substance has no timeline data: its line shows "no timeline". It never makes the experience count as active on its own, which is the same rule as the notification.

## Testing

**Unit tests:**

- `chooseWidgetExperience`:
  - an active experience is chosen;
  - an experience whose effects ended is skipped in favour of an older one that's still active;
  - an experience with only friends' ingestions isn't active;
  - no experiences gives none.
- `buildWidgetContent`: `Active` carries the notification's lines and `nextRefreshAt`, and nothing active gives `NothingActive`.

**Emulator check:**

- place all three widgets, with and without an active experience;
- light and dark theme;
- resize "Timeline" so the chart redraws;
- tap the widget and the + button;
- log an ingestion and see every widget update;
- wait for the alarm refresh and see the now line move.

**Changelog:** "Home-screen widgets" under Added.
