# Planned-ingestion chip with substance picker and quick log

Date: 2026-10-08
Status: Design approved in chat, awaiting spec review

## Goal

Replace the per-route redose chips with **one chip** that holds a *planned ingestion*: any substance, route and dose. It defaults to the latest ingestion, and is changed in an editor with a searchable substance picker. The chip is used both to preview a redose and to preview a **different substance**, e.g. to see when effects overlap.

A **quick log** button records the planned ingestion as a real ingestion taken now, after a confirmation that shows interaction warnings.

## Background

This builds on the redose preview (hold, pin, solid/dashed overlay, "With redose" and "Peak after redose" lines) and on route merging.

Today the preview can only copy one of the user's logged ingestions and change its dose (`AllTimelinesModel.withPlannedIngestion(option, dose, time)`). Previewing a substance that hasn't been taken requires building a curve from the substance database.

The app already has what this needs:
- substance search: `SearchRepository.getMatchingSubstances(searchText, filterCategories, recentlyUsedSubstanceNamesSorted)`, which matches names and aliases and orders recently used substances first;
- recently used names: `experienceRepo.getSortedLastUsedSubstanceNamesFlow(limit)`;
- interaction lookups: `InteractionChecker.getInteractionBetween(aName, bName)`;
- saving: `ExperienceRepository.insertIngestionAndCompanion(ingestion, substanceCompanion)`.

## Approach

The view model builds the planned line, and the chart model stays pure (approach A).

- A plain Kotlin function builds a `DataForOneEffectLine` for the planned ingestion from the substance database.
- `AllTimelinesModel.withPlannedIngestion` takes that ready-made line.
- Everything built on "a planned line" keeps working unchanged: the overlay, the peak, the "With redose" line, pinning and merging.

These alternatives were rejected:
- **B, giving the chart model access to the substance database:** it would stop the chart model being plain, testable maths.
- **C, limiting the chip to substances in this experience:** it doesn't meet the goal of previewing a different substance.

## Design

### 1. The chip and its editor

- **One chip**, under "Hold to preview taking now:". For example: `● Ketamine · 40 mg insufflated` followed by 📌 (pin), ✎ (edit) and ✓ (took it). The route is always shown.
- **Default:** the user's most recent own ingestion in this experience that can be previewed (see Section 3). Its dose is used if it was logged in the database unit of that route; otherwise the lower end of the common range is used.
- **If nothing in the experience can be previewed:** the chip reads `Choose a substance to preview` with ✎ only. Holding it does nothing, and 📌 and ✓ are hidden.
- **Holding and pinning** behave as today.
- **The editor** is a full-height bottom sheet, opened with ✎:
  1. **Substance:** a search field and a list (Section 2). Choosing a substance shows steps 2 and 3, with a "change" link back to the list.
  2. **Route:** one button per route of the substance.
     - A route that can't be previewed is disabled, with the reason "no full duration data" or "no dose ranges".
     - The pre-selected route is the first previewable route the user took in this experience, otherwise the first previewable route.
  3. **Dose:** a number field in the database unit of the route.
     - Below it, the dose ranges of that route, e.g. `light 5–30 · common 30–75 · strong 75–175 · heavy 175+ mg`. Ranges missing from the database are left out.
     - Pre-filled with the user's last dose of that substance and route in this experience, if it was logged in the database unit; otherwise with `commonMin`.
     - Commas are accepted as decimal separators. Doses of 0 or less, or that aren't numbers, disable **Done**.
  4. **Done** puts the selection on the chip; **Cancel** leaves the chip unchanged. If the chip is pinned, **Done** updates the pin.

### 2. The substance picker

- **Two groups:**
  1. **"In this experience":** substances from the user's own ingestions in this experience, most recently taken first.
  2. **"All substances":** the results of `getMatchingSubstances` with the recently used names, which puts recently used substances first. Substances already in group 1 are left out.
- **Search** filters both groups with `getMatchingSubstances` (names and aliases). An empty search lists everything, in a lazy list.
- **Previewable:** a substance can be previewed if at least one of its routes is previewable (Section 3).
  - A substance that can't be previewed is shown greyed out and can't be tapped, with the reason `Can't be previewed: no full duration data for any route` or `Can't be previewed: no dose ranges for any route`. When routes fail for different reasons, the duration reason is shown.
- A coloured dot is shown when the substance already has a colour (substance companion).
- **Custom substances are not listed.** They have no timing or dose data.
- **Where the logic lives:**
  - Building the list (grouping, removing duplicates, deciding what is previewable and why) is a plain function: `buildPickerEntries(matchingSubstances, experienceSubstanceNamesByRecency)`.
  - The data comes from one helper shared by `ExperienceViewModel` and `TimelineScreenViewModel`.

### 3. Building the planned ingestion

- **`PlannedIngestion(substanceName: String, route: AdministrationRoute, dose: Double, units: String)`** replaces `RedoseOption` as the chip's data.
- **A route is previewable** if:
  - `roaDuration` has full onset, come-up, peak and offset (`toFullDurations() != null`);
  - `roaDose?.averageCommonDose != null`.
- **`buildPlannedLine(planned, substance, color, time): DataForOneEffectLine?`** is a pure function. It returns `null` if:
  - the route is missing or not previewable;
  - or `planned.units != roaDose.units`.

  Otherwise the line has:
  - `height = dose / averageCommonDose`;
  - `horizontalWeight` from the dose's number of dots, using the same mapping as real ingestions. That mapping (`numDots` → weight) moves into a shared function used by both `ExperienceViewModel.getDataForEffectTimelines` and the planned line;
  - `referenceDose = averageCommonDose`, `doseUnits = units`, `isReferenceDoseFromDoseRanges = true`;
  - `startTime = time`, `endTime = null`, and the route's `roaDuration`.
- **`AllTimelinesModel.withPlannedIngestion(line: DataForOneEffectLine)`** adds the line and keeps it as the planned line. Everything else reads the planned substance and route from that line:
  - `plannedPeak`, `plannedIngestionPoint`, `plannedIngestionColor` and `plannedFractionOfPeakAt`;
  - the overlay baseline;
  - the peak's units, which are those of the line.
- **Colour of the planned line:** the substance's companion colour if it has one; otherwise the first `AdaptiveColor` not used by any line on the chart (or the first colour if all are used).
- **Effect on the chart:**
  - the same substance and route joins that line;
  - the same substance through another route joins the merged line when merging applies, otherwise it gets its own line;
  - a different substance gets its own line, on the shared strength scale (unless independent heights is on).
- **The chip default** comes from the real lines: the latest line whose substance and route are previewable and whose dose is known. Its dose is `height × referenceDose` when `doseUnits` equals the database unit; otherwise `commonMin`.

### 4. Pinning, storage and cleanup

- **`PinnedRedose`** keeps its fields and its stored format, so existing saved pins still load. It is built from a `PlannedIngestion` plus the experience and the pin time. `pinnedAt` is kept when only the dose of the same substance and route changes.
- **The pin is removed when:**
  1. the substance is logged through the same route after `pinnedAt` (unchanged);
  2. "now" is past the end of the experience's timeline (unchanged);
  3. the planned line can no longer be built (`buildPlannedLine` returns `null`). This replaces "its chip no longer exists". Deleting an ingestion no longer removes the pin.

  Rules 1 and 2 stay a pure, tested function: `resolvePinnedPlan(pin, realLines, timelineEnd, now): PlannedIngestion?`. Rule 3 is applied where the line is built.
- **The chip's choice when not pinned** is kept while the screen is open, and resets to the default when the screen is left.
- **Removed:** the per-route chips and their per-chip dose editing, `RedoseOption`, and `redoseOptions`. `chipText` is adapted.
- **Both screens** (the experience and the full-screen timeline, for your own ingestions only) use the single chip, the editor and the quick log.
- **Unchanged:**
  - the solid/dashed overlay;
  - the "With redose" and "Peak after redose" lines, with their space reserved;
  - the details under the chart, which describe real ingestions only;
  - route merging;
  - one pin at a time, across experiences.

### 5. Quick log ("Took it")

- **✓ on the chip** opens a confirmation dialog, titled "Log ingestion now?". It shows:
  - `● <substance> · <dose> <units> <route>`;
  - `now (HH:MM) · this experience · you`;
  - interaction warnings between the planned substance and each other substance in the user's own ingestions of this experience, from `InteractionChecker.getInteractionBetween`, in the interaction type's colour. Nothing is shown when there are none;
  - **Cancel** and **Log**.
- **Log** saves `Ingestion` with:
  - `substanceName`, `administrationRoute`, `dose` and `units` from the chip;
  - `time = now`, `endTime = null`, `creationDate = now`;
  - `isDoseAnEstimate = false`, `estimatedDoseStandardDeviation = null`;
  - `experienceId` = the current experience, `consumerName = null` (you);
  - `notes = null`, `stomachFullness = null`, `customUnitId = null`.

  It is saved with `insertIngestionAndCompanion`. The companion is the existing one, or a new `SubstanceCompanion` with the planned line's colour if the substance has none.
- **Afterwards:**
  - the chart shows the real ingestion;
  - a pin of that substance and route removes itself (rule 1);
  - the chip default becomes the new ingestion.
- **Pure, tested parts:** building the `Ingestion` from `PlannedIngestion`, the experience id and now; whether a new companion is needed; and the list of interactions.

## Testing

Tests are written first (TDD) as JVM unit tests:

1. **`buildPlannedLine`:**
   - height, timing, colour and horizontal weight;
   - `null` for incomplete timing, missing dose ranges and mismatched units.
2. **The chart model with a planned line:**
   - a different substance becomes its own group with the correct peak;
   - the same substance and route joins its group;
   - the existing redose, pin and overlay tests are adapted to `PlannedIngestion` and lines, and keep their expected values.
3. **Chip default:**
   - the latest previewable line;
   - the unit fallback to `commonMin`;
   - nothing when no line is previewable.
4. **Picker list:**
   - grouping and order;
   - no duplicates;
   - previewable flags and reasons.
5. **Pin:**
   - rules 1 and 2 with `PlannedIngestion`;
   - saved-format compatibility (a pin string from the current format still loads).
6. **Quick log:**
   - the `Ingestion` fields;
   - a companion is needed only when missing;
   - interactions only with interacting substances of this experience.
7. **Manual check on the emulator:**
   - the default chip;
   - editing to another substance and holding to see the overlap;
   - pinning;
   - the quick log with and without an interaction;
   - the full-screen timeline.

## Out of scope

- Custom substances and custom units in the picker.
- Quick logging for other consumers, or at a time other than now.
- Persisting an unpinned chip selection.
