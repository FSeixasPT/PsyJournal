# Changelog

All notable changes to PsyJournal are listed here, newest first.

PsyJournal is a fork of [PsychonautWiki Journal](https://github.com/isaakhanimann/psychonautwiki-journal-android) 11.11, the last open-source version. For changes up to 11.11, see the original project.

## Unreleased

### Added

- **Supplement routine reminders.** On the MDMA page, plan when you'll take it and PsyJournal reminds you of each step of RollSafe's supplement routine, from 4 hours before to 7 hours after, or of its minimal version, three supplements before the event. After the dose, each reminder also says roughly how much water you should have had by then (more if you're dancing), with a reminder that too much water is dangerous, and the last one says when 5-HTP is OK again. Mark steps done from the reminder or by ticking them, so steps you missed stand out. Between steps, the reminder stays up and quietly shows the next step and when to take it. The routine also shows under the chart of its experience, with the next step and the water so far. If you log MDMA without a routine, the experience offers to start the after-dose part (turn this off with "Suggest MDMA supplement routine" in Settings); deleting that dose, or its experience, stops those reminders. Tapping a reminder opens the experience, and the X on its card stops the routine. To start it again later, or after turning the suggestion off, use "Supplement routine" in the experience's + menu.
- **Frequency warnings.** When you log MDMA or MDA less than 3 months after your last session, a psychedelic before your tolerance from the last one (of any psychedelic) has reset, or ketamine for the third time in a week, PsyJournal tells you how long ago the last session was and why spacing them out matters, with a source. Redoses within the same experience don't count, and neither do ingestions you log for someone else. The substance page shows when you last took it and the recommended gap, and the redose preview mentions it too.

## 12.0 — 2026-10-14

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
- **Custom colors.** The color picker is shorter, offering 20 built-in colors instead of 51 (colors already in use keep working), and has a "Custom color…" button: choose any color with hue, saturation and brightness sliders or a hex code, with a preview on light and dark backgrounds. Like the built-in colors, a custom color is made a bit lighter in dark mode or darker in light mode when it would otherwise be hard to see. Backups keep custom colors; a backup that contains one can't be imported into apps without this feature.
- **Change the substance and route of an ingestion.** The ingestion editor has a Substance and route card: pick another substance from a searchable list, or another route (the ones PsychonautWiki documents for the substance come first). If the ingestion used a custom unit, such as sprays, its dose is converted to what the unit stood for, since the unit only belongs to its own substance and route.
- **Hide things from the timeline.** Turn off "Show in timeline" when editing an ingestion or a timed rating to leave it out of the timeline without deleting it; its row then says "(Not in timeline)". To hide a substance from the timelines of all experiences, turn it off on its substance page, next to its journal color, or in Settings → Substance colors and visibility, where the + button also lets you set the color and visibility of substances you haven't logged yet; its search lists the exact match first. A substance you haven't logged can be removed from that list again with the Remove button in its color picker. Hidden ingestions still count everywhere else, such as stats and the ingestion list.
- **Duration, notes and ingestion counts in the journal.** Each experience row shows how many ingestions it has, how many notes if it has any (its written notes count as one, plus its timed notes) and roughly how long it lasts: the hours from your first ingestion until the effects are estimated to end.
- **Duration range on the timeline.** Turn on "Show duration range" in Settings to shade the area between the shortest and the longest durations known for the route, instead of the area below the curve, so you can see how much the timing can vary. It applies to the experience, fullscreen and substance timelines.

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
- **Simpler ingestion list.** The list below an experience's timeline no longer has a date header, since each ingestion already shows its day and time.
- **Timeline hours follow your phone's clock format.** With the 12-hour format, the hours below timelines read "1 PM" or "2 PM" instead of "13" or "14", and fewer hours are labeled on narrow charts so the labels don't overlap.

### Fixed

- Mirtazapine was wrongly flagged with every depressant, because "Antidepressants" matched the word "depressant".
- Tapping Done twice could save an ingestion, rating, timed note, custom unit or custom substance twice. If saving fails, your entries now stay on screen so you can try again.
