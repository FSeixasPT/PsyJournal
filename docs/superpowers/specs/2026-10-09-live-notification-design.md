# Live timeline notification

Date: 2026-10-09
Status: Design approved in chat, awaiting spec review

## Goal

During an experience, the user can follow the estimated timeline from the notification shade, without opening the app. The notification shows:

- where each substance is on its curve;
- a small chart of the timeline;
- the pinned redose plan, if there is one.

It also offers shortcuts to add an ingestion, log the pinned plan, or stop. It's written independently from the closed-source official app; nothing is copied from it.

## Decisions taken in chat

- **Content:** text plus a mini chart. The collapsed view has one line per substance. The expanded view adds the chart with a "now" line.
- **Starting:** a toggle on the experience screen, plus a setting (off by default) to start the notification automatically when logging a recent ingestion.
- **Stopping:**
  - automatically when every estimated effect has ended;
  - with the Stop action, or by swiping the notification away.
- **Lock screen:** a setting, off by default, decides whether details are shown on the lock screen. When off, the lock screen shows a version without substance names.
- **Pinned redose plan:** shown as one line, with a "Log planned" action. The action opens the app's existing confirmation dialog; it never logs silently.
- **Refreshing:** an ordinary ongoing notification refreshed by inexact alarms (approach 1), not a foreground service.

## Scope

**In:**

- one live experience at a time;
- only ingestions of the user themself, i.e. consumer `null`, like the experience screen's main timeline;
- PsychonautWiki substances with duration data. Ingestions without duration data appear as a text line only (see "Content").

**Out:**

- restoring the notification after the phone restarts;
- several experiences at once;
- other consumers;
- a home-screen widget, which is a separate later feature.

## Components

All new code goes in the package `ui/tabs/journal/experience/livenotification/`. Each file has one job.

### 1. `LiveNotificationContent` (pure Kotlin, unit-tested)

```kotlin
data class LiveNotificationContent(
    val title: String,                 // the experience title
    val lines: List<SubstanceLine>,    // sorted by ingestion time of the first ingestion of the substance
    val plannedLine: String?,          // "Planned: 2C-B 10 mg insufflated in 25 min", null without a plan
    val endsAt: Instant?,              // when the last estimated effect ends, null if no line has a known end
    val nextRefreshAt: Instant?,       // see "Refreshing"
)

data class SubstanceLine(
    val substanceName: String,
    val color: AdaptiveColor,
    val phase: Phase,
    val percentOfPeak: Int?,           // 0..100, null if the substance has no full timeline
    val hint: TimeHint?,               // what happens next and when, null if unknown
)

enum class Phase { WAITING_FOR_ONSET, COMING_UP, PEAKING, STEADY, COMING_DOWN, ENDED, NO_TIMELINE }

data class TimeHint(val kind: HintKind, val time: Instant)
enum class HintKind { STARTS, PEAKS, COMES_DOWN, RISES_AGAIN, ENDS }
```

`buildLiveNotificationContent(...)` takes these inputs:

- the experience title;
- the experience's `DataForOneEffectLine`s, built with the existing `getDataForEffectTimelines`;
- the `AllTimelinesModel` built from them, for its `getQuantityDetails(now)`;
- the pinned plan, if it `belongsTo` this experience;
- the time display option (see "Times");
- `now`.

It returns the content above.

**Phase of a substance, from its summed line:**

The phase comes from the substance's line as drawn on the chart: the sum of all its ingestions, or of its merged routes. It doesn't come from a single ingestion. So a small top-up during the come-down doesn't make the notification say "coming up" while the chart goes down.

Let *h(t)* be the height of the substance's line, from the existing `heightAt` of its full timelines, and *M* its highest point over the whole line.

- **Direction:** *d* = *h*(now + 5 min) − *h*(now − 5 min).
  - *h*(now) = 0 and the line rises later: `WAITING_FOR_ONSET`.
  - *h*(now) = 0 and the line stays at 0: `ENDED`.
  - *d* > 0.05·*M*: `COMING_UP`.
  - *d* < −0.05·*M*: `COMING_DOWN`.
  - Otherwise, flat: `PEAKING` if *h*(now) ≥ 0.5·*M*, else `STEADY`. `STEADY` is the rare case where one dose's come-down balances another's come-up at a low level.
- **Time hint:** found by stepping the line forward in 1-minute steps from now, up to its end:
  - `WAITING_FOR_ONSET`: `STARTS`, when *h* first becomes > 0.
  - `COMING_UP`: `PEAKS`, when the line stops rising, i.e. the first step where it's no longer `COMING_UP`.
  - `PEAKING` / `STEADY`: `COMES_DOWN`, when it starts falling. If it rises first, `PEAKS` at the end of that rise.
  - `COMING_DOWN`: `RISES_AGAIN` if a later dose makes it rise before it reaches 0, otherwise `ENDS`, when *h* reaches 0.
  - `ENDED`: no hint.

  These moments are also the "phase changes" the refresh scheduling uses.

**Fallback without a summed line:** a substance whose duration data is too incomplete for a full timeline (no peak or no offset) has no summed line. Only for those, the phase comes from its most recent ingestion that hasn't ended:

- The boundaries are the midpoints of its onset, come-up, peak and offset ranges, the same 0.5 weights the chart uses.
- If a duration is missing, that phase and every later one are unknown. The line shows the last phase it can work out, with no hint.
- A time-range ingestion (one with an `endTime`) counts as starting at its start time, and its peak lasts until its end time plus the peak duration.
- A substance with no duration data for any route gets `NO_TIMELINE`.

**Percent of peak:** `fractionOfPeak` from `getQuantityDetails(now)` for that substance, rounded. When routes are merged, the merged group counts as one substance.

**End of the experience:** `endsAt` is the latest time at which a substance line reaches 0, using the fallback offset end for substances without a summed line. When every line is `ENDED` or `NO_TIMELINE`, the notification is removed.

**Text of a line:**

- `"<substance> · <phase> · <percent>% · <hint>"`.
- The phase words are: `waiting for onset`, `coming up`, `peaking`, `steady`, `coming down`, `ended`, `no timeline`.
- The hint words are `starts`, `peaks`, `comes down`, `rises again` and `ends`, followed by the time (see "Times"). The hint is left out when unknown.
- Examples, with "Auto" or "Time relative to now":
  - `2C-B · peaking · 90% · comes down in 40 min`
  - `Caffeine · coming up · 40% · peaks in 15 min`
  - `2C-B · coming down · 55% · rises again in 20 min`
- The same examples with "Regular time": `… · comes down at 22:40`.

**Times:** every time in the notification follows the app's time display setting, which is the stopwatch option on the experience screen. This covers the hints and the planned line. The notification uses the existing `plannedTimeText` together with `chipTimeDisplayOption`, as the redose chip does, so the notification always matches the chip:

- **Auto** and **Time relative to now:** relative to now, e.g. "in 25 min", or "10 min ago" for a planned time that's past. The notification is about the ongoing experience, so Auto always means relative to now.
- **Time relative to start:** e.g. "2.5 hr in".
- **Regular time** and **Time between:** the clock time in the device's 12/24-hour format, e.g. "at 22:40".

Relative times are only as current as the last refresh, which is at most about 5 minutes old, or longer if Android delays an alarm in doze. The countdown described in the notifier section stays live between refreshes.

### 2. `LiveNotificationChart`

Draws the existing timeline into a bitmap of about 600×200 px for the expanded view:

- It uses `CanvasDrawScope` on an `ImageBitmap` and reuses the existing drawing code of `AllTimelinesModel`'s group drawables.
- It adds a vertical "now" line.
- It uses dark-theme colours on a transparent background.
- It has no axis labels; the text lines carry the times.

If drawing fails, the notification is posted without the chart.

### 3. `LiveNotificationNotifier`

- **Channel:** builds and posts the notification on its own channel, `live_timeline`, named "Live timeline", with low importance (no sound, no vibration).
- **Behaviour:** the notification is ongoing, only alerts once, and uses category `CATEGORY_PROGRESS`.
- **Layout:** `DecoratedCustomViewStyle` with two small XML layouts:
  - collapsed: up to 3 substance lines plus "+N more";
  - expanded: the chart, then all lines and the planned line.
  
  Each line has a coloured dot in the substance's colour.
- **Countdown:** when `endsAt` is known, the notification uses `setWhen(endsAt)` with a count-down chronometer, so Android itself shows a live "ends in" time between refreshes.
- **Lock screen:**
  - When "Show details on lock screen" is off, visibility is `VISIBILITY_PRIVATE`, with a public version that only says "Experience in progress" with no substance names or chart.
  - When it's on, visibility is `VISIBILITY_PUBLIC`.
- **Actions:**
  - **Tap:** opens the experience.
  - **Add ingestion:** opens the add-ingestion flow. The existing nearest-experience logic of the finish screen assigns a current ingestion to this experience.
  - **Log planned:** only shown with a pinned plan. Opens the experience and shows the existing quick-log confirmation dialog for the plan.
  - **Stop:** a broadcast that stops the live notification.
  - **Swipe away (delete intent):** same as Stop.

### 4. `LiveNotificationState` (DataStore, in `UserPreferences`)

- `KEY_LIVE_EXPERIENCE`: `"<experienceId>|<experienceCreationDate ISO>"`, or absent. It is stored with the creation date because experience ids are reused after deletion, the same reason `PinnedRedose.belongsTo` exists. A stored value whose experience no longer exists, or has a different creation date, counts as absent.
- `KEY_LIVE_AUTO_START`: Boolean, default false.
- `KEY_LIVE_SHOW_ON_LOCK_SCREEN`: Boolean, default false.

### 5. `LiveNotificationController` (@Singleton, Hilt)

The single entry point for everything else:

- `start(experienceId)`: saves the state, then refreshes. Starting another experience replaces the current one.
- `stop()`: clears the state, cancels the notification and the alarm.
- `refresh()`:
  - loads the live experience, its ingestions, companions, substances and the pinned plan from the repositories;
  - builds the content, then posts it, or stops if the experience is gone or everything has ended;
  - schedules the next alarm.
  
  It runs on `Dispatchers.Default`.
- `isLiveFlow(experienceId): Flow<Boolean>`: for the experience screen toggle.
- **Updates while the app runs:** the controller collects the live experience's ingestions flow. Any change triggers `refresh()`: a new ingestion, an edit, a deletion, or the deletion of the experience, which leads to stop. Collection starts with the app process and when a live experience is started.

### 6. `LiveNotificationReceiver` (BroadcastReceiver, `@AndroidEntryPoint`)

- Handles the `REFRESH`, `STOP` and `DISMISSED` actions, using `goAsync()` for the suspend work.
- Registered in the manifest with `exported="false"`.

### 7. Refreshing

- **Next alarm:** `nextRefreshAt` is the earlier of `now + 5 min` and the earliest hint time of any line (`hint.time`), but not before `now + 1 min`.
- **Scheduling:** `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)` with a `PendingIntent` to the receiver. These are inexact alarms, so no exact-alarm permission is needed. Android may delay them in doze; that's accepted, and the countdown chronometer stays live in the meantime.

## UI changes

**Experience screen:**

- A "Live notification" switch row near the timeline. It's only shown when the experience has an ingestion of the user whose estimated effect hasn't ended yet, or when it's already live.
- Turning it on:
  - On Android 13+, asks for `POST_NOTIFICATIONS` if it isn't granted.
  - If the request is denied, the switch stays off and a short text explains that notifications are blocked, with a button to the app's notification settings.

**Settings, in the existing timeline/journal options group of `SettingsScreen`:**

- "Start live notification when logging a recent ingestion"
- "Show live notification details on lock screen"

**Auto-start:**

- Trigger: after an ingestion is saved via add ingestion or quick log, with a time within the last 60 minutes or in the next 5, and the auto-start setting on.
- The controller starts that ingestion's experience. If notification permission isn't granted, nothing happens.
- Auto-start never asks for the permission. That only happens from the switch.

## Navigation from the notification

- **Mechanism:** each `PendingIntent` targets `MainActivity` with extras:
  - `EXTRA_OPEN_EXPERIENCE_ID`;
  - `EXTRA_OPEN_QUICK_LOG` (Boolean);
  - `EXTRA_ADD_INGESTION` (Boolean).
- **Delivery:** `MainActivity` hands the intent to `MainScreen` in `onCreate` and in `onNewIntent`. `MainScreen` navigates to the matching route:
  - `ExperienceRoute(experienceId)`;
  - `ExperienceRoute(experienceId, openQuickLog = true)`;
  - `AddIngestionRoute`.
- **`ExperienceRoute`:** gets `openQuickLog: Boolean = false`. When true and a pinned plan belongs to the experience, the screen opens the existing quick-log dialog once.
- **Edge cases:**
  - If the experience no longer exists, the journal is shown.
  - If the user hasn't accepted the app's initial disclaimer yet, the extras are ignored.

## Error handling

- **Missing data:** if the experience or its ingestions can't be loaded, the notification stops quietly.
- **Notifications blocked:** if they're blocked after starting, the state is cleared on the next refresh. `NotificationManagerCompat.areNotificationsEnabled()` is false then.
- **Chart failure:** the notification is posted without the chart (see the chart section).
- **Swipe-away:** on Android 14+, users can dismiss ongoing notifications. The delete intent treats that as Stop.

## Testing

**JVM unit tests (`LiveNotificationContentTest`):**

- the phases of a single ingestion's summed line: waiting for onset, coming up, peaking, coming down, ended, with the right hints;
- a small top-up during the come-down that doesn't lift the summed line: `COMING_DOWN`, with `ENDS`;
- a top-up that does lift the line: `COMING_DOWN` with `RISES_AGAIN`, then `COMING_UP` once it rises;
- a flat line below half of *M*: `STEADY`;
- the fallback for a duration missing the peak or offset: the known phase is shown, with no hint;
- a substance with no duration: `NO_TIMELINE`, and it doesn't keep the notification alive;
- a time-range ingestion;
- `endsAt` and the "everything ended" signal;
- `nextRefreshAt`: never more than 5 min ahead, never earlier than 1 min, and it snaps to a phase change;
- the planned line, and no planned line when the pin belongs to another experience;
- the text of a line for each time display option: Auto (relative to now), relative to start, and regular time.

**`LiveNotificationState` parsing tests:** a valid value, a malformed value, and a reused id with a different creation date.

**On the emulator:**

- start from the switch and grant the permission;
- check the collapsed and expanded views and the lock screen in both settings;
- Add ingestion, Log planned and Stop;
- swipe away;
- check that it ends by itself with a short test substance;
- check that deleting the experience stops it.

## Files touched

- new package `ui/tabs/journal/experience/livenotification/` with the files above, and two layouts in `res/layout/`;
- `AndroidManifest.xml`: `POST_NOTIFICATIONS` permission and the receiver;
- `UserPreferences`: the three keys;
- `ExperienceScreen` / `ExperienceViewModel`: the switch row and the `openQuickLog` handling;
- `journalGraph.kt`: `ExperienceRoute.openQuickLog`;
- `MainActivity`, `MainScreen`: intent extras;
- `SettingsScreen` / `SettingsViewModel`: the two settings;
- `FinishIngestionScreenViewModel`, `PlannedIngestionActions`: auto-start after saving.
