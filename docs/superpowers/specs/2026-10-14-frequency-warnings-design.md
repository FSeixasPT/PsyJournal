# Frequency warnings

Date: 2026-10-14
Status: Design approved in chat, awaiting spec review

## Goal

PsyJournal warns people when they use a substance more often than harm-reduction advice suggests, for example MDMA again less than three months after the last time. The warnings come from a small, hand-picked list of rules, each with a reason and a source. They inform and never block.

## Decisions taken in chat

- **Curated rules, not tolerance data for everything.** PsychonautWiki's tolerance times are generic for many substances ("3–7 days / 1–2 weeks" for alcohol, caffeine, cocaine, heroin…), so v1 only covers substances where the advice is specific.
- **Sessions, not doses.** A redose in the same experience never triggers a warning; redosing MDMA once during the roll is common and not the problem. Only earlier experiences count.
- **Three places:** the add-ingestion flow (interactions step), the substance page, and the redose preview on the timeline.
- **MDA shares MDMA's rule**, and the two count against each other.
- **Psychedelics use PsychonautWiki's reset time per substance**, with cross-tolerance between them. 2C-B, DMT and 5-MeO-DMT never trigger.
- **Out of v1:** warnings about *lower* tolerance after a break (opioids, benzodiazepines, GHB, alcohol). They need their own rules (they only make sense after regular use) and get a separate design.

## The rules

| Rule | Substances | Warns when | Shown reason |
|---|---|---|---|
| Long gap | MDMA, MDA (one group) | a previous session of either ended less than **90 days** ago | Serotonin needs time to recover, and frequent use is linked to lasting low mood and memory problems. Waiting at least 3 months between sessions is recommended. |
| Psychedelic tolerance | Every substance in PsychonautWiki's "psychedelic" category whose tolerance "full" text contains "immediately" and whose "zero" time parses to at least 1 day (70 substances today) | a previous session of **any** substance in the group ended less than the **reset time of the substance being logged** ago | Tolerance (and cross-tolerance between psychedelics) means the effects will likely be weaker. Taking more to make up for it raises the risk of a difficult trip. |
| Too often | Ketamine | there were **2 or more** other sessions in the past **7 days** | Using ketamine several times a week can damage the bladder. Early damage often heals if you cut down. |

Sources shown with the warnings:

- MDMA/MDA: https://tripsitter.substack.com/p/rolling-safe-mdma
- Psychedelics: the logged substance's own PsychonautWiki page (its `url` in `Substances.json`), which has a Tolerance section with cross-tolerance notes
- Ketamine: https://knowyourstuff.nz/ketamine-bladder-0-stars-do-not-recommend/

Reset times come from the bundled `Substances.json` (`tolerance.zero`). Examples: LSD 5 days, psilocybin mushrooms and mescaline 7 days, 1P-LSD and the NBOMes 14 days, 2C-I "7-10 days" (upper end: 10 days). Excluded by the rule itself: 2C-B, 2C-C, 2C-E, DMT, ayahuasca (no tolerance data), 5-MeO-DMT and bufotenin (reset within 2 hours), and entactogens such as MDA, 6-APB and methylone (tolerance only "with prolonged and repeated use").

## How sessions are counted

- A **session** is an experience. All ingestions of rule substances in one experience form one session, which ends at its last such ingestion.
- Only the user's **own** ingestions count (consumer `null`), like the timeline, widgets and live notification. Ingestions hidden from the timeline still count, since they were taken.
- The **gap** is the time from the end of a previous session to the time being checked.
- **Current session:**
  - when logging: the experience the new dose would join isn't chosen yet (that's the last step), so every experience with an ingestion in the last 12 hours (`hourLimitToSeparateIngestions`) counts as current and is ignored;
  - in the redose preview: the open experience is the current session;
  - on the substance page: nothing is current; it shows the most recent session.

## What the user sees

**Add-ingestion flow, interactions step.** A warning card above the interaction list, for example:

> **Last MDMA session was 5 weeks ago.** Waiting at least 3 months between sessions is recommended: serotonin needs time to recover, and frequent use is linked to lasting low mood and memory problems. *Source*

For the psychedelic rule the title names the substance that caused it ("Last psychedelic session: mushrooms, 3 days ago") and uses the softer tolerance reason. For ketamine: "3rd ketamine session in 7 days." The card doesn't stop the flow; "Next" works as before.

**Substance page.** A "Frequency" card below "Tolerance", only for substances covered by a rule:

> Last session: 5 weeks ago · Recommended gap: 3 months · OK again from 14 Jan

For ketamine: "Sessions in the last 7 days: 2 · More than 2 a week risks bladder damage". With no previous session, the card shows just the recommended gap and reason.

**Redose preview.** When the planned substance would trigger a warning against an earlier experience, one extra line appears under the preview text, e.g. "Tolerance from mushrooms 3 days ago: effects will be weaker."

## Architecture

All new logic is plain Kotlin without Android dependencies, in `data/frequency/`:

- `ToleranceDuration.kt`: `parseToleranceDuration(text: String): Duration?` turns PsychonautWiki texts such as "7 days", "7-10 days", "7 - 14 days", "2 weeks", "1 month", "2.5 months", "2 hours" into a duration (upper end of ranges; month = 30 days). Returns null for anything it can't read, which means no warning.
- `FrequencyRules.kt`: the three rules as data (`MinimumGapRule`, `ToleranceRule`, `MaxSessionsRule`), each with its substance set, threshold, reason text and source URL. The psychedelic group is built from the `Substance` list at startup.
- `FrequencyChecker.kt`: `check(substanceName, at: Instant, currentExperienceIds: Set<Int>, pastIngestions: List<PastIngestion>): List<FrequencyWarning>`, where `PastIngestion` has substance name, time and experience id. Groups into sessions, applies the rule for the substance, and returns warnings with the rule, the triggering substance, last session end, recommended gap and "OK again from" time. A second function gives the substance-page summary (last session, sessions in window).

Database: one new DAO query, own ingestions (`consumerName IS NULL`) of a set of substance names since a date, returning name, time and experience id. The existing `getLatestIngestionOfEverySubstanceSinceDate` only returns one ingestion per substance, which isn't enough for counting sessions.

UI hooks:

- `CheckInteractionsViewModel`: loads past ingestions for the rule's group since the longest window it needs (90 days for MDMA/MDA), runs the checker with `Instant.now()` and the 12-hour current-session rule, exposes the warnings; `CheckInteractionsScreen` shows the card.
- `SubstanceViewModel` / `SubstanceScreen`: the summary card under "Tolerance".
- `ExperienceViewModel` / `PlannedIngestionSection`: runs the checker for the planned substance with the open experience as current; one extra line in the preview.

Also: a CHANGELOG entry under Unreleased (Added) and a line in the README's "Stay safer" list.

## Testing

Unit tests (written first, per the project's TDD practice):

- Parser: "5 days", "7-10 days" → 10 days, "7 - 14 days", "2 weeks", "1 month", "2.5 months" → 75 days, "2 hours", and unreadable text → null.
- Checker:
  - MDMA redose in the same experience → no warning;
  - MDMA session ended 89 days ago → warning; 91 days → none;
  - an MDA session counts for MDMA and the other way around;
  - LSD 3 days after a mushroom session → warning using LSD's 5 days; LSD 6 days after → none;
  - mushrooms 6 days after LSD → warning (mushrooms' 7 days);
  - 2C-B, DMT, 5-MeO-DMT → never;
  - ketamine with 2 other sessions in 7 days → warning; with 1 → none; ketamine twice in one experience counts once;
  - an experience with an ingestion in the last 12 hours is treated as current;
  - someone else's ingestion (consumer set) doesn't count.
- The psychedelic group built from the real `Substances.json` includes LSD, psilocybin mushrooms and 1P-LSD, and excludes 2C-B, DMT, 5-MeO-DMT and MDA.

On the emulator, with a test journal: the card in the add flow, the substance-page card, and the redose-preview line.

## Out of scope

- "Lower tolerance after a break" warnings (separate design).
- Rules for other substances; the rule list is easy to extend later.
- Settings to turn warnings off or change thresholds.
- Notifications or reminders outside the three places above.
