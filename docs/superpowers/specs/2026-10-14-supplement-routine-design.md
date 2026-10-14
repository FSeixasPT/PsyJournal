# Supplement routine reminders

Date: 2026-10-14
Status: Design approved in chat, awaiting spec review

## Goal

Someone planning to take MDMA can follow RollSafe's supplement routine without keeping track of the time themselves. PsyJournal reminds them each hour what to take and roughly how much water they should have had by then. It never pushes supplements as a substitute for safer use: the evidence is presented honestly, and the water reminders guard against drinking too much as well as too little.

## Decisions taken in chat

- **Started from a planned dose time** on the MDMA page. After logging MDMA without a routine running, the app offers to start the after-dose part.
- **Two routines:** RollSafe's regular routine, or its minimal routine for people who can't take pills at the event.
- **Each reminder shows** that hour's supplements and a running water total ("By now: about 1 L"). When starting, the app asks whether the user will be dancing or somewhere hot (500 ml an hour) or not (250 ml).
- **Reminders only:** nothing is logged and there's no "taken" button.
- **Scheduling** uses a chain of `setAndAllowWhileIdle` alarms, like the live notification. Reminders can be a few minutes late, so no exact-alarm permission is needed. A boot receiver re-arms the chain after a restart, which adds the `RECEIVE_BOOT_COMPLETED` permission.
- **MDMA only** in v1.

## The routines

Source: RollSafe, https://rollsafe.org/mdma-supplements/ (checked 2026-10-14). The amounts and times are RollSafe's; all wording in the app is our own. Offsets are relative to the planned MDMA dose.

**Regular**

| Offset | Take | Optional |
|---|---|---|
| −4 h | Ginger 2 g | |
| −3 h | ALCAR 500 mg, vitamin C 500 mg | |
| −1 h | Ginger 2 g | Magnesium 100 mg |
| 0 (with the dose) | ALA 300 mg, vitamin C 500 mg | |
| +1 h | ALA 300 mg, ALCAR 500 mg | |
| +2 h | ALA 300 mg | Magnesium 100 mg |
| +3 h | ALA 300 mg, vitamin C 500 mg | |
| +4 h | ALA 300 mg | |
| +5 h | ALA 300 mg, ALCAR 500 mg | |
| +6 h | ALA 300 mg, vitamin C 500 mg | |
| +7 h | ALA 300 mg | |

**Minimal**

| Offset | Take |
|---|---|
| −13 h | Vitamin E 800 IU |
| −6 h 30 min | CoQ10 200–600 mg |
| −3 h | ALCAR 1000 mg |
| 0 to +7 h | water only (one reminder per hour, see below) |

The minimal routine's water reminders run hourly from 0 to +7 h, so both routines end at +7 h.

## Water

- **Rate:** 250 ml an hour, or 500 ml if dancing or somewhere hot. The user chooses when starting and can change it later.
- **Running total in reminders from 0 to +7 h:** hours since the dose × rate, rounded to the nearest 0.25 L. At the dose reminder (0 h) the line is "Start drinking about 250 ml an hour" instead of a total.
- **Every water line** says not to drink much more than that, because too much water is dangerous, and suggests an electrolyte drink.
- **Before the dose** there's no water line.

## Reminders

- **Channel:** "Supplement reminders", default importance (sound/vibration), separate from the silent live-notification channel.
- **Title:** "MDMA routine · +2 h (01:00)". Before the dose: "MDMA routine · 3 h before (20:00)". At the dose: "MDMA routine · with your dose (23:00)".
- **Text:** "Take: ALA 300 mg · optional: magnesium 100 mg", then the water line. Before the dose, also "Planned MDMA at 23:00."
- **The last reminder (+7 h)** adds: "No 5-HTP until 24 h after your last MDMA dose (from Sat 07:00)."
- **Tap** opens the MDMA page. A **Stop routine** action ends the routine and cancels its alarm.
- **Lock screen:** follows the existing "Live notification details on lock screen" setting (`isLiveShownOnLockScreenFlow`). Off means a public version reading "PsyJournal reminder".
- **Notification permission:** requested when starting, using the same flow as the live notification. Without it, the routine can't start, and the sheet explains why.

## Screens

**MDMA page, "Supplement routine" section:**
- When nothing is running: one line on what the routine is, and a **Plan…** button.
- While running:
  - "Next: +3 h at 02:00 · ALA 300 mg, vitamin C 500 mg";
  - the full schedule with past steps greyed;
  - the water rate;
  - **Change** (dose time, water rate) and **Stop**.

**Plan sheet:**
- **Planned dose time:** defaults to 4 h from now, rounded up to the next quarter hour.
- **Regular / Minimal:** one line each. When the dose is too soon for the first steps, the sheet says which steps are skipped.
- **"Dancing or somewhere hot?"** switch.
- **A note:**
  - the evidence is from animal studies;
  - RollSafe earns from some supplement links;
  - supplements don't make MDMA safe;
  - a link to RollSafe.
- **Start:** if another routine is running, starting replaces it after a confirmation.

**Experience screen offer:**
- Shown when the experience has an MDMA ingestion of the user's own in the last 2 hours and no routine is running.
- A small dismissible card: "Start RollSafe's after-dose reminders?" with **Start**.
- Start runs the regular routine, timed from that ingestion, at 250 ml an hour, skipping steps already past. The rate can be changed on the MDMA page.
- Dismissing hides the offer for that experience.

## Architecture

- **`data/supplements/SupplementRoutine.kt`** (pure Kotlin, unit-tested):
  - the two routines as data (`RoutineStep(offset: Duration, take: List<Supplement>, optional: List<Supplement>)`, `Supplement(name, amount)`);
  - `remainingSteps(routine, doseTime, now)`;
  - `waterTotalMl(doseTime, at, mlPerHour)`;
  - `fiveHtpFrom(lastStep)`;
  - `isFinished(routine, doseTime, now)`;
  - reminder text builders.
- **`ActiveRoutine` in `UserPreferences` (DataStore):**
  - `kind` (REGULAR/MINIMAL), `doseTime`, `mlPerHour`;
  - plus the experience ids whose offer was dismissed.
- **`SupplementReminderScheduler`:**
  - arms one `setAndAllowWhileIdle` alarm for the next step;
  - cancels it on stop;
  - re-arms it from the stored routine.
- **`SupplementReminderReceiver`:** posts the due step's notification, arms the next step or clears the routine when finished, and handles the Stop action.
- **`BootReceiver`:** listens for `BOOT_COMPLETED` and re-arms if a routine is active and not finished. Steps missed while the phone was off are skipped; only the most recent missed step is posted, once.
- **UI:**
  - the MDMA page section and plan sheet (`SubstanceScreen`/`SubstanceViewModel`, only when the substance is MDMA);
  - the offer card on `ExperienceScreen`.

## Testing

- **Unit tests:**
  - step lists match the tables above;
  - `remainingSteps` for start times before, between and after steps;
  - water totals at 250 and 500 ml, rounding, and no water line before the dose;
  - the 5-HTP date is 24 h after the last step's dose time;
  - `isFinished` after +7 h;
  - reminder texts for a pre-dose, dose, after-dose and last step.
- **On the emulator:**
  - plan a routine with the dose 4 h minus a few minutes away, and check the first reminder;
  - check the Stop action;
  - restart the emulator mid-routine and check the re-arm;
  - check the offer after logging MDMA;
  - check the lock-screen setting.

## Out of scope

- Routines for other substances.
- Marking steps as taken or logging supplements.
- Custom supplement lists or amounts.
- Translations.
