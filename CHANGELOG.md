# Changelog

All notable changes to PsyJournal are listed here, newest first.

PsyJournal is a fork of [PsychonautWiki Journal](https://github.com/isaakhanimann/psychonautwiki-journal-android) 11.11, the last open-source version. For changes up to 11.11, see the original project.

## Unreleased

### Added

- **Chart settings next to the chart.** A gear on the effect timeline, and on the full-screen chart, opens a sheet with the chart's switches: independent substance heights, merged routes and duration range. The chart changes behind the sheet as you tap them. They apply to all experiences and are still in Settings too. The sheet links to the explanation of how to read the chart, which replaces the Info button.
- **Keep chart in view.** A new chart setting, in the gear sheet and in Settings, keeps the effect timeline and its current effects at the top of an experience while the ingestions, notes and ratings scroll below it. It's off by default, and in landscape or on short windows such as split screen the chart scrolls as usual.
- **What's new.** The first time you open PsyJournal after an update, a popup lists what changed, and Details shows the full notes. Settings → App → What's new has the changes of every PsyJournal release. New installs don't get the popup. Copies installed from Google Play leave out the notes about the Ko-fi page.
- **Substance colors in Drugs.** Substances you've given a color show it as a bar on the left in the Drugs list, like the experiences in the Journal.
- **Mushroom weight to psilocybin.** When logging Psilocybin mushrooms, the dose screen lets you choose between entering the Psilocybin dose or the Mushroom weight. With Mushroom weight, pick the kind (P. cubensis, Penis Envy, liberty cap, wavy cap, P. azurescens or truffles), enter the grams, dried or fresh, and it works out about how much psilocybin that is as the dose, in place of the pure dose field, and shows the range lab tests commonly find. Switching between the two keeps what was entered in each, with the cursor at the end ready to carry on typing, and the dose logged is the one from the way chosen. To always log mushrooms by weight, create a custom unit from there: the custom unit screen opens filled in for that kind, and any grams entered carry over.

### Changed

- **Clearer note on the mushroom dose.** The note above the Psilocybin mushrooms dose listed grams per kind of mushroom, which the Mushroom weight choice now works out, and ended on a garbled sentence. It now says the dose is in mg of psilocybin, points to Mushroom weight, and says how much potency varies.
- **New app icon: an open journal with a red spiral**, on the same sunset background. It replaces the eye, which was the original app's logo, on the home screen, the launch screen and the welcome screen, and in its themed version.
- **Shorter dates on the time pickers.** The date buttons when logging or editing an ingestion or rating leave out the year when it's this year, such as "Sun 18 Oct", so the time next to them stays on one line.
- **Settings labels a little smaller**, so every switch setting fits on one line next to its switch on common phones. "Live notification details on lock screen" is now called "Lock screen details".

### Fixed

- **Settings switches no longer cut off.** On narrower phones, the switches of longer settings such as "Start live notification automatically" were pushed partly off the card; the label now wraps instead.
- **Drug testing services with long city lists show their name.** Ai Laket in Spain showed as "Ai …"; long city lists now wrap, and it now lists Vitoria-Gasteiz, Pamplona, Bilbao and Donostia.
- **The last interactions and safer-use tips scroll clear of the Next button** when logging an ingestion.

## 12.1.1 — 2026-10-18

### Changed

- **A little more space under the timeline chart**, between its hour labels and the list of current effects.

### Fixed

- **Timeline header on one line again.** In 12.1, the timeline's Live notification, Info and expand buttons moved to a line of their own on many phones, even at the normal font size. The buttons are now a little more compact, so "Effect timeline" and the buttons fit on one line on common phones; when space is tighter the title wraps first, and the buttons only move down when they really don't fit, such as with a very large font. Live notification shows it's on by its color, without the check mark.

## 12.1 — 2026-10-17

### Added

- **Journal filter.** The funnel in the Journal shows only experiences where you took certain substances (all of the ones you pick), with certain numbers of substances (such as 1 or 2), or that lasted between two durations you set on a slider. Only your own doses count, and it works together with favorites and search.
- **What comes next, under the chart.** On an ongoing experience, each substance under the chart now also shows its phase and what comes next, such as "Coming up · peaks in 20 min", like the live notification.
- **Supplement routine reminders.** On the MDMA page, plan when you'll take it and PsyJournal reminds you of each step of RollSafe's supplement routine, from 4 hours before to 7 hours after, or of its minimal version, three supplements before the event. After the dose, each reminder also says roughly how much water you should have had by then (more if you're dancing), with a reminder that too much water is dangerous, and the last one says when 5-HTP is OK again. Mark steps done from the reminder or by ticking them, so steps you missed stand out. Between steps, the reminder stays up and quietly shows the next step and when to take it. The routine also shows under the chart of its experience, with the next step and the water so far. If you log MDMA without a routine, the experience offers to start the after-dose part (turn this off with "Suggest MDMA supplement routine" in Settings); deleting that dose, or its experience, stops those reminders. Tapping a reminder opens the experience, and the X on its card stops the routine. To start it again later, or after turning the suggestion off, use "Supplement routine" in the experience's + menu; it picks up where it left off, with your ticks.
- **Frequency warnings.** When you log MDMA or MDA less than 3 months after your last session, a psychedelic before your tolerance from the last one (of any psychedelic) has reset, or ketamine for the third time in a week, PsyJournal tells you how long ago the last session was and why spacing them out matters, with a source. Redoses within the same experience don't count, and neither do ingestions you log for someone else. The substance page shows when you last took it and the recommended gap, and the redose preview mentions it too.
- **Donate.** Settings → App has a Donate button that opens PsyJournal's Ko-fi page. It replaces the original developer's donation links, which were no longer reachable from the app. Copies installed from Google Play don't show it.

### Fixed

- **Importing the wrong file can no longer wipe your journal.** A file that isn't a PsyJournal export is now rejected without changing anything; before, an unrelated JSON file was accepted and replaced the journal with an empty one. A valid export is read first, and the confirmation says what it contains (how many experiences and ingestions, and from when to when) and how many experiences your current journal has, before you replace it. An export that lacks the journal color of a substance it uses, such as one made by hand or by another tool, gets a built-in color instead of crashing the journal.
- **The lock-screen setting explains what Android decides.** With "Live notification details on lock screen" off, PsyJournal asks Android for a generic lock-screen message, but Android still shows the details when the phone is set to show sensitive notification content. The setting now says so, and that it covers the supplement reminders too.
- **Timeline buttons stay visible with a large font size.** The timeline's Live notification, Info and expand buttons wrap onto another line instead of disappearing, and the preview chip's pin, change and "I took it" buttons keep their space while its text wraps. The experience's title in the top bar stays on one line, ending in "…" when it doesn't fit.
- **The "Notifications are blocked" message goes away once you allow them.** After allowing notifications in Android's settings from that message, it disappears when you return, instead of staying until you tap Live notification again.
- **The Vancouver drug checking link** points to Vancouver Coastal Health's current page.

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
