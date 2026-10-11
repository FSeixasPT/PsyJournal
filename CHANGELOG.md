# Changelog

All notable changes to PsyJournal are listed here, newest first.

PsyJournal is a fork of [PsychonautWiki Journal](https://github.com/isaakhanimann/psychonautwiki-journal-android) 11.11, the last open-source version. For changes up to 11.11, see the original project.

## Unreleased

### Added

- **Duration range on the timeline.** The shading now spans the shortest to the longest durations known for the route, instead of filling the area below the curve, so it shows how much the timing can vary. It appears on the experience, fullscreen and substance timelines, and can be turned off in Settings with "Show duration range".

### Changed

- **Thinner lines in the live notification's chart**, so the small chart is easier to read.

### Fixed

- Deleting the experience that the live notification follows now removes the notification. Deleting everything or importing a journal does too.

## 12.0 — 2026-10-10

### Added

- **Live timeline notification.** Follow the current experience from the notification shade without opening the app.
  - Each substance shows its phase (waiting for onset, coming up, peaking, steady, coming down) and its percentage of peak. It also shows when the next phase starts, in your chosen time display.
  - The phase follows the combined line on the chart. A small top-up during the come-down doesn't show as "coming up".
  - The expanded view adds a small chart with a "now" line, and the pinned redose plan.
  - Buttons:
    - **Add ingestion** opens the add flow.
    - **Log planned** opens the usual confirmation.
    - **Stop** removes the notification.
  - It stops by itself when all effects have ended. Swiping it away also stops it.
  - Turn it on with "Live notification" above an experience's timeline, next to "Info". In Settings you can also have it start automatically after logging a recent ingestion.
  - Substance details are hidden on the lock screen unless you allow them in Settings.
- **Home-screen widgets.** Follow the current experience from your home screen:
  - **Timeline:** the chart with a "now" line, and each substance's phase.
  - **Timeline status:** only the phases, in a small widget.
  - The widgets show your most recent experience whose effects haven't ended. They update when you log, and every few minutes while the experience lasts.
  - Tapping a widget opens the experience. The + button opens the add-ingestion flow.
  - Times are clock times, like "peaks at 15:42", so they stay correct between updates. If you chose times relative to the start, those are kept.
- **Planned-ingestion chip under the timeline.**
  - Show it with "Preview next ingestion" below the chart, and hide it again with ×. Your choice is remembered.
  - Hold the chip to preview the effect of taking a dose now, drawn as a dashed line on the chart, with the resulting intensity and peak.
  - Edit the planned substance, route and dose, including custom units.
  - Pin the plan to keep it visible.
  - Log it with "I took it now", after a confirmation, or change its details in the full add flow.
- **Intensity details below the timeline.** Drag across the chart to see each substance's percentage of peak and the equivalent dose at that moment.
- **Quick recent times when logging.** Pick "Now", "5 min ago", "15 min ago", "30 min ago" or "1 hr ago" instead of opening the clock. This also works when editing an ingestion.
- **Line breaks in ingestion notes and timed notes.** Enter now starts a new line.
- **Change a substance's journal color from its page**, once you have logged it.
- **Hide routes of administration you never use.** Choose them in Settings, and show or hide routes for a single substance on its page. Hidden routes stay available under "Show hidden routes".
- **Merge the routes of a substance into one line.** For example, oral and insufflated ketamine can show as one line on the timeline (setting).
- **Copy support info** in Settings → Feedback: the app version, Android version and device, to paste into a bug report.
- **Drug checking services** in Lisbon (Kosmicare) and Toronto.

### Changed

- **Targets Android 16.** The app is built for the latest Android version, as Google Play requires.
- **Reproducible builds.** Building a release from source gives the same APK as the published one, so app stores like F-Droid can verify it and publish the same signed APK.
- **Exported backups are named "PsyJournal" and the date**, so they're easy to tell apart from the original app's.
- **New app icon.** The same eye on a warm sunset background, so PsyJournal is easy to tell apart from the original app on your home screen. The launch screen matches it.
- **Interaction warnings are more complete.**
  - Group names now match their members, which they mostly didn't before: DOx, 25x-NBOMe, 25x-NBOH, 2C-x, 2C-T-x and 5-MeO-xxT.
  - MAOIs (including Ayahuasca), SSRIs, SNRIs and tricyclic antidepressants list their members.
  - Custom substances named with a common name (e.g. "Molly") or in different letter case (e.g. "mdma") are checked as that substance.
- **After saving an ingestion, its experience opens**, also when you started from the journal list or the notification.
- **Rebranded as PsyJournal**, with its own app id, so it can be installed next to the original app.

### Fixed

- Mirtazapine was wrongly flagged with every depressant, because "Antidepressants" matched the word "depressant".
- Tapping Done twice could save an ingestion, rating, timed note, custom unit or custom substance twice. If saving fails, your entries now stay on screen so you can try again.
