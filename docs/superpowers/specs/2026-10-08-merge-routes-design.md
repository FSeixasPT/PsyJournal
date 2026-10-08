# Merge routes of the same substance on the timeline

Date: 2026-10-08
Status: Design approved in chat, awaiting spec review

## Goal

Today the effect timeline draws one line per substance **and route**: insufflated and oral ketamine taken in the same experience are two separate curves. The user wants **one line per substance** that shows the combined *effect*, not the combined amount. Each route keeps its own timing (onset, come-up, peak, offset), and doses are made comparable across routes.

Merging is controlled by a setting that is **on by default** and can be switched off.

## Background and data

- The substance database (`app/src/main/assets/Substances.json`) has 289 substances. 81 of them list more than one route.
- Bioavailability is given for only 12 of 401 routes, which is too sparse to use. It is **not** used.
- Every route has its own dose ranges (light, common, strong, heavy). Those ranges come from reported effects, so they already reflect how effective each route is, including things bioavailability misses such as first-pass metabolism and active metabolites.
  - Example (ketamine): the insufflated reference dose is 52.5 mg and the oral reference dose is 200 mg, a factor of about 3.8. Bioavailability alone (45% vs 17%) would suggest about 2.6.
- The chart already measures effect per route. Each ingestion's height is `dose / referenceDose(route)`, where `referenceDose = (commonMin + strongMin) / 2` (`RoaDose.averageCommonDose`). Heights are therefore already comparable across routes.
- Coverage:
  - 78 substances have dose ranges for at least two routes.
  - 39 of those also have full timing data for at least two routes.
- Limitation: the dose ranges are community estimates, not lab data. Merging stacks two such estimates.

## Approach

Each ingestion's line gets its **own timing**, taken from its route. The existing curve summation in `FullTimelines` (rise, plateau and fall per ingestion, added up at every point) then merges routes without further changes. Ingestions of different routes are simply lines in the same group.

These alternatives were rejected:

- **A wrapper that adds up several per-route curves.** It would introduce a second curve type, which drawing, the intensity details, the peak calculation and the redose preview would all have to handle.
- **Converting all doses into one main route's dose.** This would draw them with the wrong timing, e.g. oral ketamine kicking in within minutes.

## Design

### 1. Curve maths

- `WeightedLine` gains a timing field: the full onset, come-up, peak and offset of that ingestion's route.
- `FullTimelines` takes its timings from each line instead of from a single group-wide set. This covers:
  - point ingestions;
  - ingestions taken over a time range (`getLineSegments` / `getSamplePoints`);
  - the fallback end-of-line value.
- In `GroupDrawable`, `TimeRangeDrawable` also takes its timings from each line.
- Without merging, every line in a group shares a route, so all the per-line timings are identical and the output equals today's.

### 2. Grouping and the setting

- **New preference:**
  - `UserPreferences.areRoutesMergedFlow` and `saveAreRoutesMerged`, default `true`.
  - It is wired through `SettingsViewModel`, `ExperienceViewModel` and `TimelineScreenViewModel`.
  - `SubstanceViewModel` (the substance info page) always passes `false`.
- **New chart-model parameter:** `AllTimelinesModel` takes `areRoutesMerged: Boolean`.
- **New effect-line field:** `DataForOneEffectLine` gains `isReferenceDoseFromDoseRanges: Boolean`. It is true when the reference dose came from `RoaDose.averageCommonDose`, and false when it fell back to the average of the logged doses. The flag describes the route, not the single ingestion: it is set from `averageCommonDose != null` even when this ingestion's dose is unknown.
- **Grouping in `AllTimelinesModel` when merging is on:**
  1. Group the lines by substance.
  2. A route is *mergeable* when all of its lines have full timings and `isReferenceDoseFromDoseRanges == true`.
  3. If a substance has two or more mergeable routes, all lines of those routes form **one group**, using the substance's colour.
  4. Non-mergeable routes, and substances with only one mergeable route, stay separate groups (one per route), as today.
- **Merging off:** one group per substance and route, exactly as today.
- **Unknown doses** (drawn at height 1, i.e. one reference dose) still merge. Such a group shows no mg equivalents (see Section 3).
- **`RoaGroup` changes:** it holds a list of routes, and for each route the reference dose and units.

### 3. Intensity details, redose preview and peak

- **`TimelineQuantityDetail`:**
  - Its single `doseEquivalent` / `doseUnits` are replaced by `doseEquivalents: List<RouteDoseEquivalent(route, dose, units)>`.
  - Each route's equivalent is the curve height × that route's reference dose.
  - The list is empty if any dose in the group is unknown, or if the units within a route differ.
- **Display** (`TimelineQuantityDetails`):
  - A single route keeps today's text: `● MDMA · 85% of peak · ≈ 85 mg equivalent`.
  - Several routes show the percentage on the first line and the equivalents on a second line, in the order the routes were first taken:
    ```
    ● Ketamine · 74% of peak
       ≈ 39 mg insufflated / ≈ 148 mg oral equivalent
    ```
- **Redose preview:**
  - There is still one chip per substance **and route**, pre-filled with that route's last dose.
  - `redoseOptions` is computed per route rather than per group. A route is eligible when it has full timings, all of its doses are known, and its units are consistent.
  - `withPlannedIngestion` copies the last line of the planned route, so it keeps that route's timings and reference dose. Grouping then merges it automatically.
  - The hollow dot and `plannedPeak` use the group that contains the planned route.
- **Peak after redose:**
  - It is expressed in the previewed route.
  - The route is named only when the group is merged: "Peak after redose: ≈ 52 mg insufflated equivalent at 00:45".

### 4. Settings screen

- A new switch, "Merge routes of same substance", goes in the same card as "independent substance heights", with an ⓘ explanation sheet:
  > When enabled, ingestions of the same substance through different routes (e.g. insufflated and oral ketamine) are shown as one combined line. Each route keeps its own onset and duration, and doses are made comparable using each route's common dose range, so the line shows the combined effect rather than the combined amount. Routes without dose ranges in the database are always shown separately.
- **Interaction with "independent heights":** with both settings on, a merged group fills the full height like any other group. No special handling is needed.

## Testing

Tests are written first (TDD) as JVM unit tests next to the existing `TimelineQuantityDetailsTest` and `RedosePreviewTest`.

1. **Curve maths:**
   - a line with its own timings gives the expected heights;
   - two routes with different timings (fast and slow) add up correctly at chosen times.
2. **Grouping:**
   - merging on with two mergeable routes gives one group;
   - merging off gives two groups;
   - a route whose reference dose is not from dose ranges stays separate;
   - an unknown dose still merges, but the group has no equivalents.
3. **Intensity details:** a merged group lists each route's equivalent. With reference doses of 52.5 mg insufflated and 200 mg oral, at a height of 1 they are 52.5 mg and 200 mg.
4. **Redose and peak:**
   - planning one route merges into the substance's group;
   - the peak is expressed in the planned route.
5. **Regression:** the existing 17 timeline tests pass. Only their field access changes (`doseEquivalent` becomes `doseEquivalents`), and the expected numbers are unchanged.
6. **Manual check:** log insufflated and oral ketamine on the emulator, then check the experience screen and the full-screen timeline with the setting on and off.

## Out of scope

- Using the bioavailability data.
- Changing the substance info page's per-route chart.
- Migrating or changing stored ingestions. This is display only.
