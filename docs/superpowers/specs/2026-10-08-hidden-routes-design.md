# Hide routes of administration

Date: 2026-10-08
Status: Design approved in chat, awaiting spec review

## Goal

Let the user hide routes of administration they never use, so these routes are not offered when choosing a route. The user picks the hidden routes globally in the app settings, and can override this per substance on the substance's page, in both directions:

- show a route that is globally hidden, e.g. insufflated for ketamine only;
- hide a route that is globally shown.

Nothing is hidden by default, so the app behaves as today until the user changes the setting.

## Scope

**Hidden routes are left out of:**

1. The add-ingestion route choice for PsychonautWiki substances (`ChooseRouteScreen` / `ChooseRouteViewModel`). This covers both the substance's routes and the "other routes" list.
2. The add-ingestion route choice for custom substances (`CustomSubstanceChooseRouteScreen`). Custom substances use the global setting only.
3. The planned-ingestion chip editor:
   - its route buttons;
   - its route pre-selection;
   - the previewability check of its substance picker.

**Never hidden:**

- logged ingestions everywhere: the journal, the charts and the details under them, and route merging;
- the substance page's dosage and duration sections;
- the Safer route explanations;
- the add-ingestion suggestions based on past ingestions.

## Storage

The settings are stored in `UserPreferences` (DataStore), like the app's other settings, as approved approach A. They are not part of the journal export, like all the other settings.

- `KEY_HIDDEN_ROUTES`: a string set of `AdministrationRoute.name` values, e.g. `{"INSUFFLATED", "SMOKED"}`. It is empty by default.
- `KEY_ROUTE_OVERRIDES`: a string set of entries `"<substanceName>\u001F<ROUTE>\u001F<SHOW|HIDE>"`. Entries that can't be parsed (an unknown route or a malformed entry) are ignored.

## Visibility rules (pure, unit-tested)

- `data class RouteOverride(substanceName: String, route: AdministrationRoute, isShown: Boolean)`
- `data class RouteVisibility(hiddenRoutes: Set<AdministrationRoute>, overrides: Set<RouteOverride>)`, with:
  - **`isShown(substanceName: String?, route)`:** the override for that substance and route if there is one, otherwise `route !in hiddenRoutes`. A `null` substance name (custom substances) uses the global setting only.
  - **`withSubstanceRoute(substanceName, route, isShown)`:**
    - stores an override only if `isShown` differs from the global setting;
    - removes the override when it matches the global setting.
  - **`withoutOverridesOf(substanceName)`:** removes all overrides of the substance ("Reset to app settings").
  - **`withGlobalRoute(route, isShown)`:** changes the global set, and leaves the overrides untouched.
- **Serialization:** `RouteOverride.toPreferenceString()` and `RouteOverride.fromPreferenceString(value): RouteOverride?` (`null` for malformed values).
- **`splitRoutes(substanceName, routes, visibility)`:** returns `(shown, hidden)`, keeping the order of `routes`. Used by the choosers.

## Settings screen

- In the "UI" card, a new entry **"Routes of administration"** opens a new screen, `RouteVisibilityScreen`.
- At the top, the explanation: *"Hidden routes are not offered when adding an ingestion or in the redose chip. Ingestions you already logged are always shown. You can override this for a single substance on its page."*
- Then one row per route in `AdministrationRoute.entries` order: the route's display text and a switch labelled "Shown".

## Substance page

- A new card near the bottom, **"Routes shown in the app"**, lists the substance's PsychonautWiki routes (`substance.roas`), each with a switch.
- A switch that differs from the global setting shows a small note, "hidden in app settings" or "shown in app settings", depending on the global value.
- If the substance has at least one override, a **"Reset to app settings"** button is shown.
- The card isn't shown for substances without routes.

## Route choosers

- The shown routes are listed as today.
- If any are hidden, a **"Show hidden routes (n)"** text button at the bottom reveals them, so a hidden route can still be logged. Choosing one behaves exactly like choosing a shown route.
- **PsychonautWiki substance chooser:**
  - the substance's routes are split with `splitRoutes(substanceName, …)`;
  - the "other routes" list is split the same way;
  - the revealed hidden routes are listed below, under the heading "Hidden routes".
- **Custom substance chooser:** routes are split with a `null` substance name, so the global setting only.

## Planned-ingestion chip editor

- **Route buttons** list the shown routes of the substance, plus the route of the plan being edited if it is hidden, so a selection never disappears.
- **`preselectedRoute`** only considers shown routes.
- **Picker:**
  - a substance is previewable only if at least one of its **shown** routes is previewable;
  - if its previewable routes are all hidden, the reason is `Can't be previewed: its previewable routes are hidden`;
  - otherwise the existing reasons apply.
- **The chip default** (from the first ingestion) is not affected, because it reflects what was actually taken.

## Testing

Tests are written first (TDD) as JVM unit tests.

1. **`RouteVisibility.isShown`:**
   - the global setting only;
   - an override that shows a globally hidden route;
   - an override that hides a globally shown route;
   - a `null` substance uses the global setting only.
2. **`withSubstanceRoute`, `withoutOverridesOf`, `withGlobalRoute`:**
   - only values that differ are stored;
   - setting a route back to the global value removes the override;
   - reset removes only that substance's overrides;
   - changing the global set keeps the overrides.
3. **Serialization:** round trip, and malformed entries are ignored.
4. **`splitRoutes`:** order is kept; overrides are applied.
5. **Picker:**
   - the new "routes are hidden" reason;
   - previewability ignores hidden routes;
   - `preselectedRoute` skips hidden routes;
   - the editor's route list keeps a hidden planned route.
6. **Manual check on the emulator:**
   - the settings screen;
   - the substance page card, including overrides and reset;
   - the add-ingestion route chooser with "Show hidden routes";
   - the chip editor.

## Out of scope

- Hiding routes on the substance page, the Safer screens or in logged data.
- Per-substance overrides for custom substances.
- Including these settings in the journal export.
