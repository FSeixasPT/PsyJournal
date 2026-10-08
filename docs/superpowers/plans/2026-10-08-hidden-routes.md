# Hidden Routes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users hide routes of administration globally in the app settings, with per-substance overrides in both directions on each substance's page. Hidden routes are left out of the add-ingestion route choice and the planned-ingestion chip editor.

**Architecture:**
- A pure `RouteVisibility` value (the globally hidden routes plus per-substance overrides) decides visibility. It is unit-tested.
- `UserPreferences` stores it in DataStore as two string sets, with an atomic `updateRouteVisibility`.
- A new settings screen, a substance-page card, the two route choosers and the chip editor read it through their view models.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, DataStore Preferences, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-08-hidden-routes-design.md`

## Global Constraints

- Gradle runs with JDK 21. In Git Bash: `export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"`, then `./gradlew ...` from `D:/Documentos/Projetos/Psychonautwiki`.
- Three tests already fail and are unrelated: `TestDates.dateDifferences`, `TestParse.testExtractSubstancesString` and `TestParse.noCrash`. "All tests pass" means everything except these.
- New files start with the PsyJournal GPL header, copied from lines 1-17 of `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/TimelineQuantityDetail.kt`.
- Nothing is hidden by default (both sets are empty).
- Storage keys: `KEY_HIDDEN_ROUTES` (`AdministrationRoute.name` values) and `KEY_ROUTE_OVERRIDES` (entries `"<substanceName>\u001F<ROUTE>\u001F<SHOW|HIDE>"`). Entries that can't be parsed are ignored.
- Texts, verbatim:
  - settings entry: `Routes of administration`;
  - settings explanation: `Hidden routes are not offered when adding an ingestion or in the redose chip. Ingestions you already logged are always shown. You can override this for a single substance on its page.`;
  - switch label: `Shown`;
  - substance card title: `Routes shown in the app`;
  - notes: `hidden in app settings` / `shown in app settings`;
  - reset button: `Reset to app settings`;
  - chooser link: `Show hidden routes (n)`;
  - chooser heading: `Hidden routes`;
  - picker reason: `Can't be previewed: its previewable routes are hidden`.
- Never hide logged ingestions, the substance page's dosage and duration sections, the Safer screens, or the add-ingestion suggestions.
- `ChooseRouteDuringAddCustomUnitScreen` (adding a custom unit) is unchanged. It passes no hidden routes.

## Review Focus

1. **Every route of a substance is hidden.** The add-ingestion chooser must still offer a way to log one: the "other routes" grid opens automatically and the hidden link is there. Test: Task 1 `everyRouteHiddenStillListsThemAsHidden`.
2. **An override for a route the substance doesn't list in PsychonautWiki** (e.g. set before a data update). It must not crash, and it still applies to that route in the "other routes" list. Test: Task 1 `overrideAppliesToAnyRouteOfTheSubstance`.
3. **The global setting is changed back after an override was set.** The override keeps its stored value, and `isShown` returns the override. Test: Task 1 `changingTheGlobalRouteKeepsOverrides`.
4. **The plan being edited uses a hidden route** (e.g. a pin made before hiding). The editor still lists that route as selected. Test: Task 5 `editorKeepsThePlannedRouteEvenIfHidden`.
5. **A substance name containing the separator character, or empty route sets from old installs.** Parsing must not crash: missing keys give an empty visibility. Test: Task 1 `malformedOverridesAreIgnored`.

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/routes/RouteVisibility.kt` | pure visibility rules, serialization, `splitRoutes` | new |
| `.../ui/tabs/settings/combinations/UserPreferences.kt` | storage | two keys, flow, `updateRouteVisibility` |
| `.../ui/tabs/settings/routes/RouteVisibilityScreen.kt` (+ ViewModel in the same file) | settings screen | new |
| `.../ui/main/navigation/graphs/settingsGraph.kt`, `.../ui/tabs/settings/SettingsScreen.kt` | navigation and entry | modify |
| `.../ui/tabs/search/substance/RoutesShownSection.kt` | substance-page card | new |
| `.../ui/tabs/search/substance/SubstanceViewModel.kt`, `SubstanceScreen.kt` | substance page | modify |
| `.../ui/tabs/journal/addingestion/route/ChooseRouteViewModel.kt`, `ChooseRouteScreen.kt`, `AdministrationRoutePicker.kt`, `CustomSubstanceChooseRouteScreen.kt` | route choosers | modify |
| `.../ui/tabs/journal/experience/timeline/SubstancePicker.kt`, `PlannedIngestion.kt`, `PlannedIngestionActions.kt`, `PlannedIngestionUi.kt` | chip editor | modify |
| test `app/src/test/java/com/isaakhanimann/journal/ui/tabs/settings/routes/RouteVisibilityTest.kt` | rules tests | new |
| test `.../timeline/SubstancePickerTest.kt` | picker tests | extend |

In this table, `...` stands for `app/src/main/java/com/isaakhanimann/journal` (or `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience` for tests).

---

### Task 1: Visibility rules

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/routes/RouteVisibility.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/settings/routes/RouteVisibilityTest.kt`

**Interfaces:**
- Produces:
  - `data class RouteOverride(substanceName: String, route: AdministrationRoute, isShown: Boolean)`, with `toPreferenceString(): String` and `companion fromPreferenceString(value: String): RouteOverride?`
  - `data class RouteVisibility(hiddenRoutes: Set<AdministrationRoute> = emptySet(), overrides: Set<RouteOverride> = emptySet())`, with:
    - `isShown(substanceName: String?, route: AdministrationRoute): Boolean`
    - `isShownGlobally(route): Boolean`
    - `hasOverrides(substanceName: String): Boolean`
    - `withSubstanceRoute(substanceName, route, isShown): RouteVisibility`
    - `withoutOverridesOf(substanceName): RouteVisibility`
    - `withGlobalRoute(route, isShown): RouteVisibility`
  - `fun splitRoutes(substanceName: String?, routes: List<AdministrationRoute>, visibility: RouteVisibility): Pair<List<AdministrationRoute>, List<AdministrationRoute>>` (shown, hidden)

- [ ] **Step 1: Write the failing tests** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.settings.routes

import com.isaakhanimann.journal.data.substances.AdministrationRoute.INSUFFLATED
import com.isaakhanimann.journal.data.substances.AdministrationRoute.ORAL
import com.isaakhanimann.journal.data.substances.AdministrationRoute.RECTAL
import com.isaakhanimann.journal.data.substances.AdministrationRoute.SMOKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteVisibilityTest {

    private val insufflatedHidden = RouteVisibility(hiddenRoutes = setOf(INSUFFLATED))

    @Test
    fun globalSettingDecidesWithoutOverride() {
        assertEquals(false, insufflatedHidden.isShown("Ketamine", INSUFFLATED))
        assertEquals(true, insufflatedHidden.isShown("Ketamine", ORAL))
    }

    @Test
    fun overrideCanShowAGloballyHiddenRoute() {
        val visibility = insufflatedHidden.withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
        assertEquals(true, visibility.isShown("Ketamine", INSUFFLATED))
        assertEquals(false, visibility.isShown("Cocaine", INSUFFLATED))
    }

    @Test
    fun overrideCanHideAGloballyShownRoute() {
        val visibility = RouteVisibility().withSubstanceRoute("Cannabis", SMOKED, isShown = false)
        assertEquals(false, visibility.isShown("Cannabis", SMOKED))
        assertEquals(true, visibility.isShown("Tobacco", SMOKED))
    }

    @Test
    fun customSubstancesUseTheGlobalSettingOnly() {
        val visibility = insufflatedHidden.withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
        assertEquals(false, visibility.isShown(null, INSUFFLATED))
    }

    @Test
    fun onlyDifferencesFromTheGlobalSettingAreStored() {
        assertEquals(emptySet<RouteOverride>(), RouteVisibility().withSubstanceRoute("Ketamine", ORAL, isShown = true).overrides)
        val shown = insufflatedHidden.withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
        assertEquals(emptySet<RouteOverride>(), shown.withSubstanceRoute("Ketamine", INSUFFLATED, isShown = false).overrides)
    }

    @Test
    fun resetRemovesOnlyThatSubstancesOverrides() {
        val visibility = insufflatedHidden
            .withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
            .withSubstanceRoute("Cocaine", INSUFFLATED, isShown = true)
            .withoutOverridesOf("Ketamine")
        assertEquals(false, visibility.hasOverrides("Ketamine"))
        assertEquals(true, visibility.hasOverrides("Cocaine"))
    }

    @Test
    fun changingTheGlobalRouteKeepsOverrides() {
        val visibility = insufflatedHidden
            .withSubstanceRoute("Ketamine", INSUFFLATED, isShown = true)
            .withGlobalRoute(INSUFFLATED, isShown = true)
        assertEquals(setOf(RouteOverride("Ketamine", INSUFFLATED, true)), visibility.overrides)
        assertEquals(true, visibility.isShown("Ketamine", INSUFFLATED))
        assertEquals(emptySet<Any>(), visibility.hiddenRoutes)
    }

    @Test
    fun overrideSurvivesSavingAndLoading() {
        val override = RouteOverride("2C-B", INSUFFLATED, isShown = false)
        assertEquals(override, RouteOverride.fromPreferenceString(override.toPreferenceString()))
    }

    @Test
    fun malformedOverridesAreIgnored() {
        assertNull(RouteOverride.fromPreferenceString(""))
        assertNull(RouteOverride.fromPreferenceString("Ketamine"))
        assertNull(RouteOverride.fromPreferenceString(listOf("Ketamine", "TELEPORTED", "SHOW").joinToString("\u001F")))
        assertNull(RouteOverride.fromPreferenceString(listOf("Ketamine", "ORAL", "MAYBE").joinToString("\u001F")))
        assertNull(RouteOverride.fromPreferenceString(listOf("a", "b", "ORAL", "SHOW").joinToString("\u001F")))
    }

    @Test
    fun splitKeepsTheOrderOfTheRoutes() {
        val visibility = insufflatedHidden.withSubstanceRoute("Ketamine", RECTAL, isShown = false)
        val (shown, hidden) = splitRoutes("Ketamine", listOf(INSUFFLATED, ORAL, RECTAL, SMOKED), visibility)
        assertEquals(listOf(ORAL, SMOKED), shown)
        assertEquals(listOf(INSUFFLATED, RECTAL), hidden)
    }

    @Test
    fun everyRouteHiddenStillListsThemAsHidden() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(ORAL, INSUFFLATED))
        val (shown, hidden) = splitRoutes("Ketamine", listOf(INSUFFLATED, ORAL), visibility)
        assertEquals(emptyList<Any>(), shown)
        assertEquals(listOf(INSUFFLATED, ORAL), hidden)
    }

    @Test
    fun overrideAppliesToAnyRouteOfTheSubstance() {
        // e.g. RECTAL is not a PsychonautWiki route of ketamine, but appears in the "other routes" list
        val visibility = RouteVisibility().withSubstanceRoute("Ketamine", RECTAL, isShown = false)
        assertEquals(listOf(SMOKED) to listOf(RECTAL), splitRoutes("Ketamine", listOf(RECTAL, SMOKED), visibility))
    }
}
```

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RouteVisibilityTest*' --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3`
Expected: `Unresolved reference 'RouteVisibility'`.

- [ ] **Step 3: Implement `RouteVisibility.kt`** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.settings.routes

import com.isaakhanimann.journal.data.substances.AdministrationRoute

// a per-substance exception to the globally hidden routes, in either direction
data class RouteOverride(
    val substanceName: String,
    val route: AdministrationRoute,
    val isShown: Boolean
) {
    fun toPreferenceString(): String =
        listOf(substanceName, route.name, if (isShown) SHOW else HIDE).joinToString(SEPARATOR)

    companion object {
        // unit separator, which can't be part of a substance name
        private const val SEPARATOR = "\u001F"
        private const val SHOW = "SHOW"
        private const val HIDE = "HIDE"

        fun fromPreferenceString(value: String): RouteOverride? {
            val parts = value.split(SEPARATOR)
            if (parts.size != 3) return null
            val route = AdministrationRoute.entries.firstOrNull { it.name == parts[1] } ?: return null
            val isShown = when (parts[2]) {
                SHOW -> true
                HIDE -> false
                else -> return null
            }
            return RouteOverride(substanceName = parts[0], route = route, isShown = isShown)
        }
    }
}

// which routes are offered when choosing a route; logged ingestions are always shown
data class RouteVisibility(
    val hiddenRoutes: Set<AdministrationRoute> = emptySet(),
    val overrides: Set<RouteOverride> = emptySet()
) {
    // custom substances pass null and only use the global setting
    fun isShown(substanceName: String?, route: AdministrationRoute): Boolean {
        if (substanceName != null) {
            overrides.firstOrNull { it.substanceName == substanceName && it.route == route }?.let { return it.isShown }
        }
        return isShownGlobally(route)
    }

    fun isShownGlobally(route: AdministrationRoute): Boolean = route !in hiddenRoutes

    fun hasOverrides(substanceName: String): Boolean = overrides.any { it.substanceName == substanceName }

    // only stores an override if it differs from the global setting
    fun withSubstanceRoute(substanceName: String, route: AdministrationRoute, isShown: Boolean): RouteVisibility {
        val others = overrides.filterNot { it.substanceName == substanceName && it.route == route }.toSet()
        return if (isShown == isShownGlobally(route)) {
            copy(overrides = others)
        } else {
            copy(overrides = others + RouteOverride(substanceName, route, isShown))
        }
    }

    fun withoutOverridesOf(substanceName: String): RouteVisibility =
        copy(overrides = overrides.filterNot { it.substanceName == substanceName }.toSet())

    fun withGlobalRoute(route: AdministrationRoute, isShown: Boolean): RouteVisibility =
        copy(hiddenRoutes = if (isShown) hiddenRoutes - route else hiddenRoutes + route)
}

// the shown and the hidden routes, each in the order of the given routes
fun splitRoutes(
    substanceName: String?,
    routes: List<AdministrationRoute>,
    visibility: RouteVisibility
): Pair<List<AdministrationRoute>, List<AdministrationRoute>> =
    routes.partition { visibility.isShown(substanceName, it) }
```

- [ ] **Step 4: Run the tests to verify that they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*RouteVisibilityTest*' --console=plain 2>&1 | grep -E "^e: |FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`, with 12 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Add the rules for hiding routes of administration

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Storage and the settings screen

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/combinations/UserPreferences.kt`
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/routes/RouteVisibilityScreen.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/main/navigation/graphs/settingsGraph.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes (Task 1): `RouteVisibility` and `RouteOverride`.
- Produces: `UserPreferences.routeVisibilityFlow: Flow<RouteVisibility>` and `suspend fun UserPreferences.updateRouteVisibility(change: (RouteVisibility) -> RouteVisibility)`.

This task is storage wiring and UI, checked by building and on the emulator.

- [ ] **Step 1: Add storage to `UserPreferences`**

Add to `PreferencesKeys`:

```kotlin
        val KEY_HIDDEN_ROUTES = stringSetPreferencesKey("KEY_HIDDEN_ROUTES")
        val KEY_ROUTE_OVERRIDES = stringSetPreferencesKey("KEY_ROUTE_OVERRIDES")
```

Add to the class:

```kotlin
    private fun routeVisibilityOf(preferences: Preferences) = RouteVisibility(
        hiddenRoutes = (preferences[PreferencesKeys.KEY_HIDDEN_ROUTES] ?: emptySet())
            .mapNotNull { name -> AdministrationRoute.entries.firstOrNull { it.name == name } }
            .toSet(),
        overrides = (preferences[PreferencesKeys.KEY_ROUTE_OVERRIDES] ?: emptySet())
            .mapNotNull { RouteOverride.fromPreferenceString(it) }
            .toSet()
    )

    val routeVisibilityFlow: Flow<RouteVisibility> = dataStore.data.map { routeVisibilityOf(it) }

    // reads and writes in one transaction, so concurrent changes are not lost
    suspend fun updateRouteVisibility(change: (RouteVisibility) -> RouteVisibility) {
        dataStore.edit { preferences ->
            val updated = change(routeVisibilityOf(preferences))
            preferences[PreferencesKeys.KEY_HIDDEN_ROUTES] = updated.hiddenRoutes.map { it.name }.toSet()
            preferences[PreferencesKeys.KEY_ROUTE_OVERRIDES] = updated.overrides.map { it.toPreferenceString() }.toSet()
        }
    }
```

Add these imports:
- `androidx.datastore.preferences.core.stringSetPreferencesKey`
- `com.isaakhanimann.journal.data.substances.AdministrationRoute`
- `com.isaakhanimann.journal.ui.tabs.settings.routes.RouteOverride`
- `com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility`

`Preferences` is already imported. `dataStore.edit` gives a `MutablePreferences`, which is a `Preferences`, so it can be passed to `routeVisibilityOf`.

- [ ] **Step 2: Create `RouteVisibilityScreen.kt`** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.settings.routes

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.theme.horizontalPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteVisibilityViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {
    val routeVisibilityFlow = userPreferences.routeVisibilityFlow.stateIn(
        initialValue = RouteVisibility(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun setShown(route: AdministrationRoute, isShown: Boolean) = viewModelScope.launch {
        userPreferences.updateRouteVisibility { it.withGlobalRoute(route, isShown) }
    }
}

@Composable
fun RouteVisibilityScreen(viewModel: RouteVisibilityViewModel = hiltViewModel()) {
    RouteVisibilityScreen(
        routeVisibility = viewModel.routeVisibilityFlow.collectAsState().value,
        setShown = viewModel::setShown
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteVisibilityScreen(
    routeVisibility: RouteVisibility,
    setShown: (AdministrationRoute, Boolean) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Routes of administration") }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Text(
                    text = "Hidden routes are not offered when adding an ingestion or in the redose chip. Ingestions you already logged are always shown. You can override this for a single substance on its page.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)
                )
            }
            items(AdministrationRoute.entries) { route ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 4.dp)
                ) {
                    Text(route.displayText, modifier = Modifier.weight(1f))
                    Text("Shown", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                    Switch(
                        checked = routeVisibility.isShownGlobally(route),
                        onCheckedChange = { setShown(route, it) }
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
```

- [ ] **Step 3: Add the route and the entry in Settings**

In `settingsGraph.kt`:
1. Add `composableWithTransitions<RouteVisibilityRoute> { RouteVisibilityScreen() }` next to `SubstanceColorsRoute`.
2. Add `navigateToRouteVisibility = { navController.navigate(RouteVisibilityRoute) },` to the `SettingsScreen(` call.
3. At the bottom, add:

```kotlin
@Serializable
object RouteVisibilityRoute
```

4. Add `import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibilityScreen`.

In `SettingsScreen.kt`:
1. Add the parameter `navigateToRouteVisibility: () -> Unit` to the public `SettingsScreen(viewModel, ...)` and pass it through.
2. Add `navigateToRouteVisibility: () -> Unit` to the inner `SettingsScreen(...)`.
3. Add `navigateToRouteVisibility = {},` to `SettingsPreview`.
4. In the "UI" card, directly after the "Substance colors" `SettingsButton` and its `HorizontalDivider()`, insert:

```kotlin
                SettingsButton(
                    imageVector = Icons.Outlined.Route,
                    text = "Routes of administration"
                ) {
                    navigateToRouteVisibility()
                }
                HorizontalDivider()
```

5. Add `import androidx.compose.material.icons.outlined.Route`, from the extended icons that are already a dependency.

- [ ] **Step 4: Build and run all tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --continue --console=plain 2>&1 | grep -E "^e: |FAILED|tests completed"`
Expected: no `e:` lines and only the 3 known failures. If `Icons.Outlined.Route` doesn't exist, use `Icons.Outlined.AltRoute` and record a ruling.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Add a settings screen to hide routes of administration

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Substance-page overrides

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/search/substance/RoutesShownSection.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/search/substance/SubstanceViewModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/search/substance/SubstanceScreen.kt`

**Interfaces:**
- Consumes (Tasks 1-2): `RouteVisibility`, `UserPreferences.routeVisibilityFlow` and `updateRouteVisibility`.
- Produces: `@Composable fun RoutesShownSection(substanceName: String, routes: List<AdministrationRoute>, routeVisibility: RouteVisibility, onRouteShownChange: (AdministrationRoute, Boolean) -> Unit, onReset: () -> Unit)`.

- [ ] **Step 1: Create `RoutesShownSection.kt`** (GPL header first)

```kotlin
package com.isaakhanimann.journal.ui.tabs.search.substance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility
import com.isaakhanimann.journal.ui.theme.horizontalPadding

// per-substance exceptions to the routes hidden in the app settings
@Composable
fun RoutesShownSection(
    substanceName: String,
    routes: List<AdministrationRoute>,
    routeVisibility: RouteVisibility,
    onRouteShownChange: (AdministrationRoute, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    if (routes.isEmpty()) return
    SectionWithTitle(title = "Routes shown in the app") {
        Column(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp)) {
            routes.forEach { route ->
                val isShown = routeVisibility.isShown(substanceName, route)
                val isShownGlobally = routeVisibility.isShownGlobally(route)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(route.displayText)
                        if (isShown != isShownGlobally) {
                            Text(
                                if (isShownGlobally) "shown in app settings" else "hidden in app settings",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Switch(checked = isShown, onCheckedChange = { onRouteShownChange(route, it) })
                }
            }
            if (routeVisibility.hasOverrides(substanceName)) {
                TextButton(onClick = onReset) { Text("Reset to app settings") }
            }
        }
    }
}
```

- [ ] **Step 2: Wire the view model**

In `SubstanceViewModel`:
1. Add the constructor parameter `private val userPreferences: UserPreferences,` before `state: SavedStateHandle`.
2. Add:

```kotlin
    val routeVisibilityFlow = userPreferences.routeVisibilityFlow.stateIn(
        initialValue = RouteVisibility(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun setRouteShown(route: AdministrationRoute, isShown: Boolean) = viewModelScope.launch {
        userPreferences.updateRouteVisibility { it.withSubstanceRoute(substanceName, route, isShown) }
    }

    fun resetRoutes() = viewModelScope.launch {
        userPreferences.updateRouteVisibility { it.withoutOverridesOf(substanceName) }
    }
```

3. Add the imports for `UserPreferences`, `RouteVisibility` and `AdministrationRoute` (if missing).

- [ ] **Step 3: Show the section on the substance page**

In `SubstanceScreen.kt`:
1. In the public `SubstanceScreen(...)` call, add:

```kotlin
        routeVisibility = viewModel.routeVisibilityFlow.collectAsState().value,
        onRouteShownChange = viewModel::setRouteShown,
        onResetRoutes = viewModel::resetRoutes,
```

2. Add parameters to the inner `SubstanceScreen(...)`. The defaults keep the preview compiling:

```kotlin
    customUnits: List<CustomUnit>,
    routeVisibility: RouteVisibility = RouteVisibility(),
    onRouteShownChange: (AdministrationRoute, Boolean) -> Unit = { _, _ -> },
    onResetRoutes: () -> Unit = {},
```

3. Directly before the final `Spacer(modifier = Modifier.height(70.dp))` of the content, add:

```kotlin
            RoutesShownSection(
                substanceName = substance.name,
                routes = substance.roas.map { it.route },
                routeVisibility = routeVisibility,
                onRouteShownChange = onRouteShownChange,
                onReset = onResetRoutes
            )
```

4. Add `import com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility`.

- [ ] **Step 4: Build and run all tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --continue --console=plain 2>&1 | grep -E "^e: |FAILED|tests completed"`
Expected: no `e:` lines and only the 3 known failures.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Let substances override the hidden routes on their page

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Route choosers

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/addingestion/route/AdministrationRoutePicker.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/addingestion/route/ChooseRouteViewModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/addingestion/route/ChooseRouteScreen.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/addingestion/route/CustomSubstanceChooseRouteScreen.kt`

**Interfaces:**
- Consumes (Tasks 1-2): `splitRoutes`, `RouteVisibility` and `UserPreferences.routeVisibilityFlow`.
- Produces: `AdministrationRoutePicker(..., hiddenRoutes: List<AdministrationRoute> = emptyList())`, and the same `hiddenRoutes` on `ChooseRouteScreen`'s inner composable.

The splitting logic is tested in Task 1. This task is UI, checked by building and on the emulator.

- [ ] **Step 1: Add hidden routes to `AdministrationRoutePicker`**

1. Add the parameter `hiddenRoutes: List<AdministrationRoute> = emptyList(),` after `onRouteTapped`.
2. Inside the `AnimatedVisibility(visible = isShowingOther, ...)` content, after the `otherRoutesChunked.forEach { ... }` loop (still inside that `Column`), add:

```kotlin
                if (hiddenRoutes.isNotEmpty()) {
                    var isShowingHidden by remember { mutableStateOf(false) }
                    if (!isShowingHidden) {
                        TextButton(onClick = { isShowingHidden = true }) {
                            Text("Show hidden routes (${hiddenRoutes.size})")
                        }
                    } else {
                        Text("Hidden routes", style = MaterialTheme.typography.titleSmall)
                        hiddenRoutes.chunked(2).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.dp)
                            ) {
                                chunk.forEach { route ->
                                    ElevatedCard(
                                        modifier = Modifier
                                            .clickable { onRouteTapped(route) }
                                            .weight(1f)
                                    ) {
                                        RouteBox(route = route, titleStyle = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                if (chunk.size == 1) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
```

3. Add the imports for `TextButton`, `remember`, `mutableStateOf`, `getValue` and `setValue` if missing.

- [ ] **Step 2: Split the routes in `ChooseRouteViewModel` and `ChooseRouteScreen`**

In `ChooseRouteViewModel`:
1. Add the constructor parameter `userPreferences: UserPreferences,`.
2. Replace the `pwRoutes`, `otherRoutes` and `otherRoutesChunked` declarations with:

```kotlin
    private val allPwRoutes = substance?.roas?.map { it.route } ?: emptyList()
    private val allOtherRoutes = AdministrationRoute.entries.filter { route -> !allPwRoutes.contains(route) }

    val routeVisibilityFlow = userPreferences.routeVisibilityFlow.stateIn(
        initialValue = RouteVisibility(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    // the routes to offer: (substance routes, other routes, hidden routes), using the substance's overrides
    fun routesFor(visibility: RouteVisibility): Triple<List<AdministrationRoute>, List<AdministrationRoute>, List<AdministrationRoute>> {
        val (shownPw, hiddenPw) = splitRoutes(substanceName, allPwRoutes, visibility)
        val (shownOther, hiddenOther) = splitRoutes(substanceName, allOtherRoutes, visibility)
        return Triple(shownPw, shownOther, hiddenPw + hiddenOther)
    }
```

In the public `ChooseRouteScreen(...)`, replace `pwRoutes = viewModel.pwRoutes, otherRoutesChunked = viewModel.otherRoutesChunked,` with:

```kotlin
        pwRoutes = routes.first,
        otherRoutesChunked = routes.second.chunked(2),
        hiddenRoutes = routes.third,
```

and add `val routes = viewModel.routesFor(viewModel.routeVisibilityFlow.collectAsState().value)` as the first line of the function body.

In the inner `ChooseRouteScreen(...)`, add the parameter `hiddenRoutes: List<AdministrationRoute> = emptyList(),` after `otherRoutesChunked`, and pass `hiddenRoutes = hiddenRoutes` to `AdministrationRoutePicker(`.

Add the imports for `UserPreferences`, `RouteVisibility`, `splitRoutes`, `stateIn`, `SharingStarted`, `viewModelScope` and `collectAsState` where missing.

- [ ] **Step 3: Hide routes for custom substances**

`CustomSubstanceChooseRouteScreen(onRouteTap)` currently has no view model. Add a small one in the same file:

```kotlin
@HiltViewModel
class CustomSubstanceChooseRouteViewModel @Inject constructor(
    userPreferences: UserPreferences,
) : ViewModel() {
    val routeVisibilityFlow = userPreferences.routeVisibilityFlow.stateIn(
        initialValue = RouteVisibility(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )
}
```

Then:
1. Change the composable's signature to `fun CustomSubstanceChooseRouteScreen(onRouteTap: (AdministrationRoute) -> Unit, viewModel: CustomSubstanceChooseRouteViewModel? = null)`. The preview passes no view model.
2. In the navigation graph call site (`addIngestionGraph.kt`), pass `viewModel = hiltViewModel()`.
3. Inside the composable, compute:

```kotlin
    val visibility = viewModel?.routeVisibilityFlow?.collectAsState()?.value ?: RouteVisibility()
    val (shownRoutes, hiddenRoutes) = splitRoutes(null, AdministrationRoute.entries.toList(), visibility)
    var isShowingHidden by remember { mutableStateOf(false) }
```

4. Replace `val routesChunked = AdministrationRoute.entries.toList().chunked(2)` with `val routesChunked = (if (isShowingHidden) shownRoutes + hiddenRoutes else shownRoutes).chunked(2)`.
5. After the `routesChunked.forEach { ... }` loop, add:

```kotlin
                if (hiddenRoutes.isNotEmpty() && !isShowingHidden) {
                    TextButton(onClick = { isShowingHidden = true }) {
                        Text("Show hidden routes (${hiddenRoutes.size})")
                    }
                }
```

Add the necessary imports (`HiltViewModel`, `Inject`, `ViewModel`, `viewModelScope`, `stateIn`, `SharingStarted`, `collectAsState`, `remember`, `mutableStateOf`, `getValue`, `setValue`, `TextButton`, `UserPreferences`, `RouteVisibility`, `splitRoutes`), and `hiltViewModel` in `addIngestionGraph.kt`.

- [ ] **Step 4: Build and run all tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --continue --console=plain 2>&1 | grep -E "^e: |FAILED|tests completed"`
Expected: no `e:` lines and only the 3 known failures.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Leave hidden routes out of the add-ingestion route choice

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Planned-ingestion chip editor

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestion.kt` (the `PreviewProblem` enum)
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/SubstancePicker.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestionActions.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestionUi.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/SubstancePickerTest.kt`

**Interfaces:**
- Consumes (Tasks 1-2): `RouteVisibility` and `UserPreferences.routeVisibilityFlow`.
- Produces:
  - `PreviewProblem.ROUTES_HIDDEN`
  - `buildPickerEntries(matchingSubstances, experienceSubstanceNamesByRecency, visibility: RouteVisibility = RouteVisibility())`
  - `Substance.substancePreviewProblem(visibility: RouteVisibility = RouteVisibility())`
  - `preselectedRoute(substance, experienceRoutesByRecency, visibility: RouteVisibility = RouteVisibility())`
  - `fun editorRoutes(substance: Substance, visibility: RouteVisibility, plannedRoute: AdministrationRoute?): List<Roa>`
  - `PlannedIngestionActions.routeVisibilityFlow: StateFlow<RouteVisibility>`

- [ ] **Step 1: Write the failing tests**

Append to `SubstancePickerTest` (inside the class), and add the imports `com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility` and `org.junit.Assert.assertTrue` if missing:

```kotlin
    @Test
    fun substanceWhosePreviewableRoutesAreHiddenGetsItsOwnReason() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.INSUFFLATED))
        assertEquals(PreviewProblem.ROUTES_HIDDEN, cocaine.substancePreviewProblem(visibility))
        assertEquals(PreviewProblem.ROUTES_HIDDEN, buildPickerEntries(listOf(cocaine), emptyList(), visibility).single().problem)
    }

    @Test
    fun hiddenRoutesWithoutDataKeepTheExistingReason() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.ORAL))
        assertEquals(PreviewProblem.NO_FULL_DURATION, mdma.substancePreviewProblem(visibility))
    }

    @Test
    fun overrideShowingAHiddenRouteMakesTheSubstancePreviewableAgain() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.INSUFFLATED))
            .withSubstanceRoute("Cocaine", AdministrationRoute.INSUFFLATED, isShown = true)
        assertEquals(null, cocaine.substancePreviewProblem(visibility))
    }

    @Test
    fun preselectedRouteSkipsHiddenRoutes() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.INSUFFLATED))
        assertNull(preselectedRoute(ketamine, listOf(AdministrationRoute.INSUFFLATED), visibility))
    }

    @Test
    fun editorListsOnlyShownRoutes() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.SUBLINGUAL))
        assertEquals(
            listOf(AdministrationRoute.INSUFFLATED, AdministrationRoute.ORAL),
            editorRoutes(ketamine, visibility, plannedRoute = null).map { it.route }
        )
    }

    @Test
    fun editorKeepsThePlannedRouteEvenIfHidden() {
        val visibility = RouteVisibility(hiddenRoutes = setOf(AdministrationRoute.INSUFFLATED))
        assertTrue(editorRoutes(ketamine, visibility, plannedRoute = AdministrationRoute.INSUFFLATED).any { it.route == AdministrationRoute.INSUFFLATED })
    }
```

- [ ] **Step 2: Run the tests to verify that they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*SubstancePickerTest*' --console=plain 2>&1 | grep -E "^e: |BUILD" | head -3`
Expected: `Unresolved reference 'ROUTES_HIDDEN'` (or `editorRoutes`).

- [ ] **Step 3: Implement**

In `PlannedIngestion.kt`, change the enum to:

```kotlin
enum class PreviewProblem { NO_FULL_DURATION, NO_DOSE_RANGES, ROUTES_HIDDEN }
```

In `SubstancePicker.kt`, replace `buildPickerEntries`, `substancePreviewProblem` and `preselectedRoute` with the following, and add `editorRoutes`:

```kotlin
// the substances of the experience first (most recent first), then the other matches in their given order
fun buildPickerEntries(
    matchingSubstances: List<Substance>,
    experienceSubstanceNamesByRecency: List<String>,
    visibility: RouteVisibility = RouteVisibility()
): List<PickerEntry> {
    val byName = matchingSubstances.associateBy { it.name }
    val inExperience = experienceSubstanceNamesByRecency.distinct().mapNotNull { byName[it] }
    val others = matchingSubstances.filter { it.name !in experienceSubstanceNamesByRecency }
    return inExperience.map { PickerEntry(it.name, isInExperience = true, problem = it.substancePreviewProblem(visibility)) } +
            others.map { PickerEntry(it.name, isInExperience = false, problem = it.substancePreviewProblem(visibility)) }
}

// why no shown route of the substance can be previewed; hidden routes only matter if they could be previewed
fun Substance.substancePreviewProblem(visibility: RouteVisibility = RouteVisibility()): PreviewProblem? {
    val shownRoas = roas.filter { visibility.isShown(name, it.route) }
    if (shownRoas.any { it.previewProblem() == null }) return null
    if (roas.any { !visibility.isShown(name, it.route) && it.previewProblem() == null }) return PreviewProblem.ROUTES_HIDDEN
    val problems = roas.map { it.previewProblem() }
    return when {
        problems.isNotEmpty() && problems.all { it == PreviewProblem.NO_DOSE_RANGES } -> PreviewProblem.NO_DOSE_RANGES
        else -> PreviewProblem.NO_FULL_DURATION
    }
}

fun preselectedRoute(
    substance: Substance,
    experienceRoutesByRecency: List<AdministrationRoute>,
    visibility: RouteVisibility = RouteVisibility()
): AdministrationRoute? {
    val previewableRoutes = substance.roas
        .filter { it.previewProblem() == null && visibility.isShown(substance.name, it.route) }
        .map { it.route }
    return experienceRoutesByRecency.firstOrNull { it in previewableRoutes } ?: previewableRoutes.firstOrNull()
}

// the routes offered in the editor: the shown ones, and the route being edited so a selection never disappears
fun editorRoutes(substance: Substance, visibility: RouteVisibility, plannedRoute: AdministrationRoute?): List<Roa> =
    substance.roas.filter { visibility.isShown(substance.name, it.route) || it.route == plannedRoute }
```

Add the imports `com.isaakhanimann.journal.data.substances.classes.roa.Roa` and `com.isaakhanimann.journal.ui.tabs.settings.routes.RouteVisibility`.

In `PlannedIngestionActions.kt`:
1. Add `val routeVisibilityFlow: StateFlow<RouteVisibility>` to the interface.
2. In `actionsFor`, add `val routeVisibility = userPreferences.routeVisibilityFlow.stateIn(scope, SharingStarted.Eagerly, RouteVisibility())`.
3. In the object, add `override val routeVisibilityFlow: StateFlow<RouteVisibility> = routeVisibility`.
4. In `searchSubstances`, pass `routeVisibility.value` as the third argument of `buildPickerEntries`.
5. Import `RouteVisibility`.

In `PlannedIngestionUi.kt`:
1. Add `val routeVisibility: RouteVisibility` to `PlannedIngestionState`.
2. In `rememberPlannedIngestionState`, add `val routeVisibility = actions.routeVisibilityFlow.collectAsState().value` and pass `routeVisibility = routeVisibility,`.
3. In the editor, replace `substance.roas.forEach { roa ->` with `editorRoutes(substance, state.routeVisibility, plannedRoute = initial?.route).forEach { roa ->`.
4. In `pick(...)`, change `preselectedRoute(substance, state.experienceRoutesByRecency(entry.substanceName))` to `preselectedRoute(substance, state.experienceRoutesByRecency(entry.substanceName), state.routeVisibility)`.
5. In the route chip's `when (problem)`, add `PreviewProblem.ROUTES_HIDDEN -> {}`.
6. In `PickerRow`'s `when (entry.problem)`, add `PreviewProblem.ROUTES_HIDDEN -> Text("Can't be previewed: its previewable routes are hidden", style = MaterialTheme.typography.labelSmall, color = textColor)`.
7. Import `RouteVisibility`.

- [ ] **Step 4: Run the tests and build**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --continue --console=plain 2>&1 | grep -E "^e: |FAILED|tests completed"`
Expected: no `e:` lines; only the 3 known failures; `SubstancePickerTest` has 6 more passing tests.

- [ ] **Step 5: Install and do the manual check with the user**

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Settings → "Routes of administration": hide Insufflated and Smoked.
2. Add ingestion → Ketamine:
   - the route choice doesn't offer insufflated;
   - "Other routes" → "Show hidden routes (2)" reveals both.
3. Ketamine's substance page: the "Routes shown in the app" card shows insufflated off. Switch it on: the note "hidden in app settings" appears, as does "Reset to app settings". Add ingestion → Ketamine now offers insufflated. Reset.
4. Chip editor: insufflated isn't offered for ketamine unless the plan already uses it.
5. Turn both routes back on in Settings.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Leave hidden routes out of the planned-ingestion chip editor

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
