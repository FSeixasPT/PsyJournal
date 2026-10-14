# Supplement Routine Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hourly reminders that walk the user through RollSafe's MDMA supplement routine (regular or minimal) with a running water total, started from a planned dose time on the MDMA page or offered after logging MDMA.

**Architecture:** Pure Kotlin in `data/supplements/` (routine data, step timing, water totals, texts, stored state), unit-tested; a Hilt singleton controller that keeps one `setAndAllowWhileIdle` alarm armed for the next step, a receiver that posts reminders, a boot receiver that re-arms; UI on the MDMA page and the experience screen.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, DataStore, AlarmManager, NotificationCompat, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-14-supplement-routine-design.md`

## Global Constraints

- Every new Kotlin file starts with the GPL header: the first 17 lines of `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/components/ExperienceDuration.kt`.
- Amounts and offsets exactly as the spec's tables (RollSafe, checked 2026-10-14). All user-facing wording is our own; never copy RollSafe's text.
- Alarms use `setAndAllowWhileIdle` (no exact-alarm permission). The only new permission is `RECEIVE_BOOT_COMPLETED`.
- Only one routine at a time. Reminders follow `isLiveShownOnLockScreenFlow` for lock-screen details.
- Build with JDK 21: `export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"`; single test class `./gradlew testDebugUnitTest --tests "<class>" -q`; full suite `./gradlew testDebugUnitTest assembleDebug -q`; then `./gradlew --stop`.
- Every user-facing change gets a CHANGELOG.md entry under "## Unreleased" in the same commit.
- Commits end with the session's `Co-Authored-By` / `Claude-Session` trailer lines.

## Review Focus

1. A step whose time has already passed when the routine starts (dose planned soon, or started after logging) must never be posted.
2. After a phone restart or a long Doze delay, only the most recent missed step is posted, once; never a burst, never a repeat.
3. The water line must never appear before the dose, and must always carry the "too much water is dangerous" caution.
4. Stopping (from the notification or the app) cancels the pending alarm and removes the notification; a later alarm from an old routine must not post anything.
5. The 5-HTP date uses the latest of the planned dose time and the user's own MDMA doses, so a redose pushes it later, never earlier.

---

### Task 1: Routine data and timing

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/data/supplements/SupplementRoutine.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/data/supplements/SupplementRoutineTest.kt`

**Interfaces:**
- Produces:
  - `enum class RoutineKind { REGULAR, MINIMAL }`
  - `data class Supplement(val name: String, val amount: String)`
  - `data class RoutineStep(val offset: Duration, val take: List<Supplement>, val optional: List<Supplement> = emptyList())`
  - `fun stepsOf(kind: RoutineKind): List<RoutineStep>` (sorted by offset)
  - `fun stepTime(doseTime: Instant, step: RoutineStep): Instant`
  - `fun remainingSteps(kind: RoutineKind, doseTime: Instant, now: Instant): List<RoutineStep>` (steps at or after `now`)
  - `fun stepToPost(kind: RoutineKind, doseTime: Instant, now: Instant, lastPostedAt: Instant?): RoutineStep?` (latest step with time ≤ now and time > lastPostedAt)
  - `fun isFinished(kind: RoutineKind, doseTime: Instant, now: Instant): Boolean` (now ≥ last step time)
  - `fun waterTotalMl(doseTime: Instant, at: Instant, mlPerHour: Int): Int?` (null before the dose; rounded to the nearest 250 ml)
  - `const val ROLLSAFE_URL = "https://rollsafe.org/mdma-supplements/"`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.isaakhanimann.journal.data.supplements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SupplementRoutineTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")
    private fun h(hours: Long) = Duration.ofHours(hours)

    @Test
    fun regularStepsMatchRollSafe() {
        val steps = stepsOf(RoutineKind.REGULAR)
        assertEquals(listOf(-4L, -3, -1, 0, 1, 2, 3, 4, 5, 6, 7), steps.map { it.offset.toHours() })
        assertEquals(listOf(Supplement("Ginger", "2 g")), steps[0].take)
        assertEquals(listOf(Supplement("ALCAR", "500 mg"), Supplement("Vitamin C", "500 mg")), steps[1].take)
        assertEquals(listOf(Supplement("Magnesium", "100 mg")), steps[2].optional)
        assertEquals(listOf(Supplement("ALA", "300 mg"), Supplement("Vitamin C", "500 mg")), steps[3].take)
        assertEquals(listOf(Supplement("ALA", "300 mg"), Supplement("ALCAR", "500 mg")), steps[4].take)
        assertEquals(listOf(Supplement("Magnesium", "100 mg")), steps[5].optional)
        assertEquals(listOf(Supplement("ALA", "300 mg")), steps[10].take)
    }

    @Test
    fun minimalStepsMatchRollSafeAndAddHourlyWater() {
        val steps = stepsOf(RoutineKind.MINIMAL)
        assertEquals(Duration.ofHours(-13), steps[0].offset)
        assertEquals(listOf(Supplement("Vitamin E", "800 IU")), steps[0].take)
        assertEquals(Duration.ofMinutes(-390), steps[1].offset)
        assertEquals(listOf(Supplement("CoQ10", "200–600 mg")), steps[1].take)
        assertEquals(listOf(Supplement("ALCAR", "1000 mg")), steps[2].take)
        assertEquals((0L..7L).toList(), steps.drop(3).map { it.offset.toHours() })
        assertTrue(steps.drop(3).all { it.take.isEmpty() && it.optional.isEmpty() })
    }

    @Test
    fun pastStepsAreNotRemaining() {
        val remaining = remainingSteps(RoutineKind.REGULAR, dose, dose.minus(Duration.ofMinutes(150)))
        assertEquals(-1L, remaining.first().offset.toHours())
    }

    @Test
    fun stepToPostIsTheLatestDueStepNotYetPosted() {
        // phone off from -4 h to +2 h 10 min: only +2 h is posted
        val step = stepToPost(RoutineKind.REGULAR, dose, dose.plus(Duration.ofMinutes(130)), lastPostedAt = dose.minus(h(4)))
        assertEquals(2L, step!!.offset.toHours())
        assertNull(stepToPost(RoutineKind.REGULAR, dose, dose.plus(Duration.ofMinutes(130)), lastPostedAt = dose.plus(h(2))))
    }

    @Test
    fun finishedAfterTheLastStep() {
        assertFalse(isFinished(RoutineKind.REGULAR, dose, dose.plus(Duration.ofMinutes(419))))
        assertTrue(isFinished(RoutineKind.REGULAR, dose, dose.plus(h(7))))
        assertTrue(isFinished(RoutineKind.MINIMAL, dose, dose.plus(h(7))))
    }

    @Test
    fun waterTotals() {
        assertNull(waterTotalMl(dose, dose.minus(h(1)), 250))
        assertEquals(0, waterTotalMl(dose, dose, 250))
        assertEquals(500, waterTotalMl(dose, dose.plus(h(2)), 250))
        assertEquals(1000, waterTotalMl(dose, dose.plus(h(2)), 500))
        // 2 h 10 min at 250 ml = 542 ml, rounded to 500
        assertEquals(500, waterTotalMl(dose, dose.plus(Duration.ofMinutes(130)), 250))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*SupplementRoutineTest" -q`
Expected: FAIL, unresolved `stepsOf`.

- [ ] **Step 3: Implement**

```kotlin
package com.isaakhanimann.journal.data.supplements

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

// RollSafe's MDMA supplement routines (https://rollsafe.org/mdma-supplements/, checked 2026-10-14);
// the amounts and times are theirs, the wording in the app is ours
const val ROLLSAFE_URL = "https://rollsafe.org/mdma-supplements/"

enum class RoutineKind { REGULAR, MINIMAL }

data class Supplement(val name: String, val amount: String)

// offset from the planned MDMA dose
data class RoutineStep(val offset: Duration, val take: List<Supplement>, val optional: List<Supplement> = emptyList())

private val ginger = Supplement("Ginger", "2 g")
private val alcar = Supplement("ALCAR", "500 mg")
private val vitaminC = Supplement("Vitamin C", "500 mg")
private val magnesium = Supplement("Magnesium", "100 mg")
private val ala = Supplement("ALA", "300 mg")

private fun hours(h: Long) = Duration.ofHours(h)

private val regular = listOf(
    RoutineStep(hours(-4), listOf(ginger)),
    RoutineStep(hours(-3), listOf(alcar, vitaminC)),
    RoutineStep(hours(-1), listOf(ginger), optional = listOf(magnesium)),
    RoutineStep(hours(0), listOf(ala, vitaminC)),
    RoutineStep(hours(1), listOf(ala, alcar)),
    RoutineStep(hours(2), listOf(ala), optional = listOf(magnesium)),
    RoutineStep(hours(3), listOf(ala, vitaminC)),
    RoutineStep(hours(4), listOf(ala)),
    RoutineStep(hours(5), listOf(ala, alcar)),
    RoutineStep(hours(6), listOf(ala, vitaminC)),
    RoutineStep(hours(7), listOf(ala)),
)

// for when pills can't be taken at the event; after the dose only water reminders
private val minimal = listOf(
    RoutineStep(hours(-13), listOf(Supplement("Vitamin E", "800 IU"))),
    RoutineStep(Duration.ofMinutes(-390), listOf(Supplement("CoQ10", "200–600 mg"))),
    RoutineStep(hours(-3), listOf(Supplement("ALCAR", "1000 mg"))),
) + (0L..7L).map { RoutineStep(hours(it), emptyList()) }

fun stepsOf(kind: RoutineKind): List<RoutineStep> = when (kind) {
    RoutineKind.REGULAR -> regular
    RoutineKind.MINIMAL -> minimal
}

fun stepTime(doseTime: Instant, step: RoutineStep): Instant = doseTime.plus(step.offset)

fun remainingSteps(kind: RoutineKind, doseTime: Instant, now: Instant): List<RoutineStep> =
    stepsOf(kind).filter { !stepTime(doseTime, it).isBefore(now) }

// after a restart or a late alarm only the most recent missed step is shown, once
fun stepToPost(kind: RoutineKind, doseTime: Instant, now: Instant, lastPostedAt: Instant?): RoutineStep? =
    stepsOf(kind).lastOrNull { step ->
        val time = stepTime(doseTime, step)
        !time.isAfter(now) && (lastPostedAt == null || time.isAfter(lastPostedAt))
    }

fun isFinished(kind: RoutineKind, doseTime: Instant, now: Instant): Boolean =
    !now.isBefore(stepTime(doseTime, stepsOf(kind).last()))

fun waterTotalMl(doseTime: Instant, at: Instant, mlPerHour: Int): Int? {
    if (at.isBefore(doseTime)) return null
    val hoursSinceDose = Duration.between(doseTime, at).toMinutes() / 60.0
    return ((hoursSinceDose * mlPerHour) / 250.0).roundToInt() * 250
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*SupplementRoutineTest" -q`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/data/supplements/SupplementRoutine.kt app/src/test/java/com/isaakhanimann/journal/data/supplements/SupplementRoutineTest.kt
git commit -m "Add RollSafe's supplement routines as data"
```

---

### Task 2: Reminder texts

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/data/supplements/ReminderText.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/data/supplements/ReminderTextTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `fun reminderTitle(step: RoutineStep, doseTime: Instant, formatTime: (Instant) -> String): String`
  - `fun reminderLines(kind: RoutineKind, step: RoutineStep, doseTime: Instant, mlPerHour: Int, lastMdmaDose: Instant, formatTime: (Instant) -> String, formatDayTime: (Instant) -> String): List<String>`
  - `fun takeLine(step: RoutineStep): String?`
  - `fun fiveHtpFrom(lastMdmaDose: Instant): Instant` (= +24 h)

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.isaakhanimann.journal.data.supplements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReminderTextTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")
    private val time: (Instant) -> String = { "T" + Duration.between(dose, it).toMinutes() }
    private val dayTime: (Instant) -> String = { "D" + Duration.between(dose, it).toHours() }
    private fun step(kind: RoutineKind, hours: Long) = stepsOf(kind).first { it.offset == Duration.ofHours(hours) }
    private fun lines(kind: RoutineKind, hours: Long, rate: Int = 250, lastDose: Instant = dose) =
        reminderLines(kind, step(kind, hours), dose, rate, lastDose, time, dayTime)

    @Test
    fun titles() {
        assertEquals("MDMA routine · 3 h before (T-180)", reminderTitle(step(RoutineKind.REGULAR, -3), dose, time))
        assertEquals("MDMA routine · with your dose (T0)", reminderTitle(step(RoutineKind.REGULAR, 0), dose, time))
        assertEquals("MDMA routine · +2 h (T120)", reminderTitle(step(RoutineKind.REGULAR, 2), dose, time))
        val coq10 = stepsOf(RoutineKind.MINIMAL)[1]
        assertEquals("MDMA routine · 6 h 30 min before (T-390)", reminderTitle(coq10, dose, time))
    }

    @Test
    fun takeLineListsOptionalSeparately() {
        assertEquals("Take: ALA 300 mg · optional: Magnesium 100 mg", takeLine(step(RoutineKind.REGULAR, 2)))
        assertNull(takeLine(step(RoutineKind.MINIMAL, 2)))
    }

    @Test
    fun beforeTheDoseThereIsNoWater() {
        val result = lines(RoutineKind.REGULAR, -3)
        assertEquals(listOf("Take: ALCAR 500 mg · Vitamin C 500 mg", "Planned MDMA at T0."), result)
    }

    @Test
    fun atTheDoseWaterStarts() {
        val result = lines(RoutineKind.REGULAR, 0, rate = 500)
        assertTrue(result[1].startsWith("Start drinking about 500 ml an hour."))
        assertTrue(result[1].contains("too much water is dangerous"))
    }

    @Test
    fun afterTheDoseTheWaterTotal() {
        val result = lines(RoutineKind.MINIMAL, 2)
        assertEquals(1, result.size)
        assertTrue(result[0].startsWith("Water so far: about 0.5 L (250 ml an hour)."))
        assertTrue(result[0].contains("too much water is dangerous"))
    }

    @Test
    fun lastReminderWarnsAboutFiveHtpFromTheLatestDose() {
        val redose = dose.plus(Duration.ofHours(2))
        val result = lines(RoutineKind.REGULAR, 7, lastDose = redose)
        assertEquals("No 5-HTP until 24 h after your last MDMA dose (from D26).", result.last())
        assertEquals(redose.plus(Duration.ofHours(24)), fiveHtpFrom(redose))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*ReminderTextTest" -q`
Expected: FAIL, unresolved `reminderTitle`.

- [ ] **Step 3: Implement**

```kotlin
package com.isaakhanimann.journal.data.supplements

import java.time.Duration
import java.time.Instant
import java.util.Locale

private fun offsetText(offset: Duration): String {
    val minutes = offset.abs().toMinutes()
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "$h h" else "$h h $m min"
}

fun reminderTitle(step: RoutineStep, doseTime: Instant, formatTime: (Instant) -> String): String {
    val at = formatTime(stepTime(doseTime, step))
    val whenText = when {
        step.offset.isNegative -> "${offsetText(step.offset)} before"
        step.offset.isZero -> "with your dose"
        else -> "+${offsetText(step.offset)}"
    }
    return "MDMA routine · $whenText ($at)"
}

private fun Supplement.text() = "$name $amount"

fun takeLine(step: RoutineStep): String? {
    if (step.take.isEmpty() && step.optional.isEmpty()) return null
    val parts = step.take.map { it.text() } + step.optional.map { "optional: ${it.text()}" }
    return "Take: " + parts.joinToString(" · ")
}

private const val WATER_CAUTION = "Don't drink much more than that: too much water is dangerous. An electrolyte drink is better than plain water."

private fun litres(ml: Int) = String.format(Locale.US, "%.2f", ml / 1000.0).trimEnd('0').trimEnd('.')

fun fiveHtpFrom(lastMdmaDose: Instant): Instant = lastMdmaDose.plus(Duration.ofHours(24))

fun reminderLines(
    kind: RoutineKind,
    step: RoutineStep,
    doseTime: Instant,
    mlPerHour: Int,
    lastMdmaDose: Instant,
    formatTime: (Instant) -> String,
    formatDayTime: (Instant) -> String,
): List<String> {
    val lines = mutableListOf<String>()
    takeLine(step)?.let { lines += it }
    val at = stepTime(doseTime, step)
    when {
        step.offset.isNegative -> lines += "Planned MDMA at ${formatTime(doseTime)}."
        step.offset.isZero -> lines += "Start drinking about $mlPerHour ml an hour. $WATER_CAUTION"
        else -> {
            val total = waterTotalMl(doseTime, at, mlPerHour) ?: 0
            lines += "Water so far: about ${litres(total)} L ($mlPerHour ml an hour). $WATER_CAUTION"
        }
    }
    if (step == stepsOf(kind).last()) {
        lines += "No 5-HTP until 24 h after your last MDMA dose (from ${formatDayTime(fiveHtpFrom(lastMdmaDose))})."
    }
    return lines
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*ReminderTextTest" -q`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/data/supplements/ReminderText.kt app/src/test/java/com/isaakhanimann/journal/data/supplements/ReminderTextTest.kt
git commit -m "Word the supplement reminders"
```

---

### Task 3: Stored routine state

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/data/supplements/ActiveRoutine.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/combinations/UserPreferences.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/data/supplements/ActiveRoutineTest.kt`

**Interfaces:**
- Produces:
  - `data class ActiveRoutine(val kind: RoutineKind, val doseTime: Instant, val mlPerHour: Int, val lastPostedAt: Instant?)` with `toPreferenceString()` and `companion fun fromPreferenceString(value: String?): ActiveRoutine?`
  - `UserPreferences.activeRoutineFlow: Flow<ActiveRoutine?>`, `suspend fun saveActiveRoutine(value: ActiveRoutine?)`
  - `UserPreferences.dismissedRoutineOffersFlow: Flow<Set<Int>>`, `suspend fun dismissRoutineOffer(experienceId: Int)`

- [ ] **Step 1: Failing test**

```kotlin
package com.isaakhanimann.journal.data.supplements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ActiveRoutineTest {
    private val dose = Instant.parse("2026-10-16T22:00:00Z")

    @Test
    fun roundTrips() {
        val routine = ActiveRoutine(RoutineKind.MINIMAL, dose, 500, lastPostedAt = dose.minusSeconds(60))
        assertEquals(routine, ActiveRoutine.fromPreferenceString(routine.toPreferenceString()))
        val fresh = ActiveRoutine(RoutineKind.REGULAR, dose, 250, lastPostedAt = null)
        assertEquals(fresh, ActiveRoutine.fromPreferenceString(fresh.toPreferenceString()))
    }

    @Test
    fun brokenValuesGiveNull() {
        assertNull(ActiveRoutine.fromPreferenceString(null))
        assertNull(ActiveRoutine.fromPreferenceString("garbage"))
        assertNull(ActiveRoutine.fromPreferenceString("UNKNOWN|2026-10-16T22:00:00Z|250|"))
    }
}
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*ActiveRoutineTest" -q` — Expected: FAIL, unresolved `ActiveRoutine`.

- [ ] **Step 3: Implement `ActiveRoutine.kt`**

```kotlin
package com.isaakhanimann.journal.data.supplements

import java.time.Instant

// the one running routine; lastPostedAt keeps a step from being shown twice
data class ActiveRoutine(val kind: RoutineKind, val doseTime: Instant, val mlPerHour: Int, val lastPostedAt: Instant?) {
    fun toPreferenceString(): String = "${kind.name}|$doseTime|$mlPerHour|${lastPostedAt ?: ""}"

    companion object {
        fun fromPreferenceString(value: String?): ActiveRoutine? {
            val parts = value?.split("|") ?: return null
            if (parts.size != 4) return null
            val kind = RoutineKind.entries.firstOrNull { it.name == parts[0] } ?: return null
            val dose = runCatching { Instant.parse(parts[1]) }.getOrNull() ?: return null
            val rate = parts[2].toIntOrNull() ?: return null
            val posted = if (parts[3].isEmpty()) null else runCatching { Instant.parse(parts[3]) }.getOrNull() ?: return null
            return ActiveRoutine(kind, dose, rate, posted)
        }
    }
}
```

- [ ] **Step 4: UserPreferences.** Add keys `KEY_ACTIVE_ROUTINE = stringPreferencesKey("KEY_ACTIVE_ROUTINE")` and `KEY_DISMISSED_ROUTINE_OFFERS = stringSetPreferencesKey("KEY_DISMISSED_ROUTINE_OFFERS")` to `PreferencesKeys`, and at the end of the class:

```kotlin
    val activeRoutineFlow: Flow<ActiveRoutine?> = dataStore.data
        .map { preferences -> ActiveRoutine.fromPreferenceString(preferences[PreferencesKeys.KEY_ACTIVE_ROUTINE]) }
        .distinctUntilChanged()

    suspend fun saveActiveRoutine(value: ActiveRoutine?) {
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(PreferencesKeys.KEY_ACTIVE_ROUTINE)
            else preferences[PreferencesKeys.KEY_ACTIVE_ROUTINE] = value.toPreferenceString()
        }
    }

    val dismissedRoutineOffersFlow: Flow<Set<Int>> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.KEY_DISMISSED_ROUTINE_OFFERS].orEmpty().mapNotNull { it.toIntOrNull() }.toSet() }

    suspend fun dismissRoutineOffer(experienceId: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_DISMISSED_ROUTINE_OFFERS] =
                preferences[PreferencesKeys.KEY_DISMISSED_ROUTINE_OFFERS].orEmpty() + experienceId.toString()
        }
    }
```

- [ ] **Step 5: Run** `./gradlew testDebugUnitTest assembleDebug -q` — Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/data/supplements/ActiveRoutine.kt app/src/test/java/com/isaakhanimann/journal/data/supplements/ActiveRoutineTest.kt app/src/main/java/com/isaakhanimann/journal/ui/tabs/settings/combinations/UserPreferences.kt
git commit -m "Store the running supplement routine"
```

---

### Task 4: Reminders, alarms and restart

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/supplements/SupplementReminderNotifier.kt`
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/supplements/SupplementRoutineController.kt`
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/supplements/SupplementReminderReceiver.kt` (also the boot receiver)
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/livenotification/LiveNotificationIntents.kt` (new target)
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/main/MainScreen.kt` (route the target)

**Interfaces:**
- Consumes: Tasks 1–3, `ExperienceRepository.getPastIngestions(setOf("MDMA"), since)`, `UserPreferences.isLiveShownOnLockScreenFlow`.
- Produces:
  - `@Singleton class SupplementRoutineController` with `suspend fun start(kind: RoutineKind, doseTime: Instant, mlPerHour: Int)`, `suspend fun stop()`, `suspend fun change(doseTime: Instant, mlPerHour: Int)`, `suspend fun onAlarm()`, `val activeRoutineFlow`.
  - `NotificationTarget.Substance(val substanceName: String)`.

- [ ] **Step 1: Notifier.** `SupplementReminderNotifier` (`@Singleton`, `@ApplicationContext`): channel `"supplement_reminders"`, name "Supplement reminders", `IMPORTANCE_DEFAULT`; `canPost()` like `LiveNotificationNotifier.canPost` (permission + enabled + channel usable); `post(title: String, lines: List<String>, showDetailsOnLockScreen: Boolean)` builds `NotificationCompat.Builder` with `R.drawable.ic_live_timeline`, `setContentTitle(title)`, `setContentText(lines.first())`, `BigTextStyle(lines.joinToString("\n"))`, `setAutoCancel(true)`, content intent `LiveNotificationIntents.openSubstance(context, "MDMA")`, action "Stop routine" → `SupplementReminderReceiver.stopIntent(context)`, visibility and public version ("PsyJournal reminder") exactly like the live notifier; one notification id `NOTIFICATION_ID = 4711`; `cancel()`.

- [ ] **Step 2: Intents and routing.** In `LiveNotificationIntents` add `EXTRA_OPEN_SUBSTANCE = "live_open_substance"`, `fun openSubstance(context, name) = PendingIntent.getActivity(context, 4, mainActivityIntent(context).putExtra(EXTRA_OPEN_SUBSTANCE, name), FLAGS)`, `NotificationTarget.Substance(substanceName)`, and in `target()` check the extra first. In `MainScreen` add `is NotificationTarget.Substance -> navController.navigate(SubstanceRoute(target.substanceName)) { launchSingleTop = true }`.

- [ ] **Step 3: Controller.**

```kotlin
@Singleton
class SupplementRoutineController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val experienceRepo: ExperienceRepository,
    private val notifier: SupplementReminderNotifier,
) {
    private val mutex = Mutex()
    val activeRoutineFlow = userPreferences.activeRoutineFlow

    // steps already past when starting are never shown
    suspend fun start(kind: RoutineKind, doseTime: Instant, mlPerHour: Int) = mutex.withLock {
        val now = Instant.now()
        userPreferences.saveActiveRoutine(ActiveRoutine(kind, doseTime, mlPerHour, lastPostedAt = now))
        armNext(now)
    }

    suspend fun change(doseTime: Instant, mlPerHour: Int) = mutex.withLock {
        val current = userPreferences.activeRoutineFlow.first() ?: return@withLock
        userPreferences.saveActiveRoutine(current.copy(doseTime = doseTime, mlPerHour = mlPerHour, lastPostedAt = Instant.now()))
        armNext(Instant.now())
    }

    suspend fun stop() = mutex.withLock { stopLocked() }

    // the alarm, a restart or a late Doze wake-up: show the most recent due step once, then arm the next
    suspend fun onAlarm() = mutex.withLock {
        val routine = userPreferences.activeRoutineFlow.first() ?: return@withLock
        val now = Instant.now()
        stepToPost(routine.kind, routine.doseTime, now, routine.lastPostedAt)?.let { step ->
            val lastMdma = experienceRepo.getPastIngestions(setOf("MDMA"), routine.doseTime.minus(Duration.ofHours(2)))
                .maxOfOrNull { it.time }?.takeIf { it.isAfter(routine.doseTime) } ?: routine.doseTime
            notifier.post(
                title = reminderTitle(step, routine.doseTime, ::formatTime),
                lines = reminderLines(routine.kind, step, routine.doseTime, routine.mlPerHour, lastMdma, ::formatTime, ::formatDayTime),
                showDetailsOnLockScreen = userPreferences.isLiveShownOnLockScreenFlow.first()
            )
            userPreferences.saveActiveRoutine(routine.copy(lastPostedAt = stepTime(routine.doseTime, step)))
        }
        if (isFinished(routine.kind, routine.doseTime, now)) {
            userPreferences.saveActiveRoutine(null)
            cancelAlarm()
        } else {
            // a second later, so the step just shown isn't armed again
            armNext(now.plusSeconds(1))
        }
    }

    private suspend fun stopLocked() {
        userPreferences.saveActiveRoutine(null)
        cancelAlarm()
        notifier.cancel()
    }

    private suspend fun armNext(now: Instant) {
        val routine = userPreferences.activeRoutineFlow.first() ?: return
        val next = remainingSteps(routine.kind, routine.doseTime, now).firstOrNull() ?: return
        // inexact, so no exact-alarm permission is needed; RollSafe's timing doesn't need to be exact
        alarmManager().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stepTime(routine.doseTime, next).toEpochMilli(), SupplementReminderReceiver.alarmIntent(context))
    }

    private fun cancelAlarm() = alarmManager().cancel(SupplementReminderReceiver.alarmIntent(context))
    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)
    private fun formatTime(instant: Instant) = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(instant.atZone(ZoneId.systemDefault()))
    private fun formatDayTime(instant: Instant) = instant.getStringOfPattern("EEE ") + formatTime(instant)
}
```

Imports: `android.app.AlarmManager`, `android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.sync.Mutex`, `kotlinx.coroutines.sync.withLock`, `java.time.Duration`, `java.time.Instant`, `java.time.ZoneId`, `java.time.format.DateTimeFormatter`, `java.time.format.FormatStyle`, `javax.inject.Inject`, `javax.inject.Singleton`, `com.isaakhanimann.journal.data.room.experiences.ExperienceRepository`, `com.isaakhanimann.journal.data.supplements.*`, `com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences`, `com.isaakhanimann.journal.ui.utils.getStringOfPattern`.

- [ ] **Step 4: Receivers.** In `SupplementReminderReceiver.kt`: an `@EntryPoint` interface `SupplementEntryPoint { fun supplementRoutineController(): SupplementRoutineController }`; `class SupplementReminderReceiver : BroadcastReceiver()` handling `ACTION_ALARM` → `onAlarm()`, `ACTION_STOP` → `stop()`, using `goAsync()` like `LiveNotificationReceiver`; companion `alarmIntent(context)` (request code 20) and `stopIntent(context)` (21), `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`. `class SupplementBootReceiver : BroadcastReceiver()` handling `Intent.ACTION_BOOT_COMPLETED` → `onAlarm()`.

- [ ] **Step 5: Manifest.** Add `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`, `<receiver android:name=".ui.supplements.SupplementReminderReceiver" android:exported="false" />`, and

```xml
        <receiver
            android:name=".ui.supplements.SupplementBootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 6: Build and suite** — `./gradlew testDebugUnitTest assembleDebug -q` — Expected: BUILD SUCCESSFUL, all pass.

- [ ] **Step 7: Commit** `git add -A app/src/main && git commit -m "Remind each step of the supplement routine"`

---

### Task 5: Routine section on the MDMA page

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/search/substance/SupplementRoutineSection.kt`
- Modify: `SubstanceViewModel.kt`, `SubstanceScreen.kt` (same folder)
- Modify: `CHANGELOG.md`, `README.md`

**Interfaces:**
- Consumes: `SupplementRoutineController`, Task 1–2 functions.
- Produces: `@Composable fun SupplementRoutineSection(activeRoutine: ActiveRoutine?, onStart: (RoutineKind, Instant, Int) -> Unit, onChange: (Instant, Int) -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: View model.** Inject `SupplementRoutineController`; `val activeRoutineFlow = controller.activeRoutineFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)`; `fun startRoutine(kind, dose, rate) = viewModelScope.launch { controller.start(kind, dose, rate) }`, same for `changeRoutine`, `stopRoutine`.

- [ ] **Step 2: Section composable.**
  - Not running: text "Hourly reminders for RollSafe's supplement routine, with how much water to drink." and a "Plan…" button opening `RoutinePlanSheet`.
  - Running: "Next: <reminderTitle without the 'MDMA routine · ' prefix> · <takeLine or 'water'>", the schedule (each step `stepTime` + `takeLine`, past ones with `alpha(0.5f)`), "Water: 250 ml an hour" (or 500), and "Change" / "Stop" buttons.
  - `RoutinePlanSheet` (`ModalBottomSheet`):
    - time picker defaulting to now + 4 h rounded up to the next 15 minutes (use the app's existing `TimePickerButton`/`DatePickerButton` from `addingestion/time` if they fit, otherwise Material3 `TimePicker` in a dialog);
    - `SegmentedButton` Regular / Minimal with one line each ("Pills from 4 h before to 7 h after the dose." / "Three pills before the event (from 13 h before), then only water reminders.");
    - when the first step of the chosen routine is already past for the chosen time, "Steps before <time> are skipped.";
    - `Switch` "Dancing or somewhere hot?" (500/250);
    - the note "The evidence for these supplements comes from animal studies, and RollSafe earns from some of the supplement links on its site. Supplements don't make MDMA safe." with a "RollSafe" link (`ROLLSAFE_URL`);
    - "Start" button. When running and the sheet was opened via "Plan…", show the replace confirmation dialog first.
  - Notification permission: before calling `onStart`, reuse the `POST_NOTIFICATIONS` request pattern from `LiveNotificationToggle.kt` (`rememberLauncherForActivityResult(RequestPermission())`); if denied or notifications are off, show "Allow notifications for PsyJournal to get the reminders." and don't start.

- [ ] **Step 3: Screen.** Add `activeRoutine: ActiveRoutine? = null`, `onStartRoutine`, `onChangeRoutine`, `onStopRoutine` parameters; render `SectionWithTitle(title = "Supplement routine") { SupplementRoutineSection(...) }` after the Frequency section only when `substance.name == "MDMA"`.

- [ ] **Step 4: Changelog and README.** Under Unreleased → Added:

```markdown
- **Supplement routine reminders.** On the MDMA page, plan when you'll take it and PsyJournal reminds you each hour what to take in RollSafe's supplement routine, from 4 hours before to 7 hours after, or its minimal version. Each reminder also says roughly how much water you should have had by then (more if you're dancing), with a reminder that too much water is dangerous, and the last one says when 5-HTP is OK again. If you log MDMA without a routine, the experience offers to start the after-dose part.
```

README "Stay safer": add "- **Supplement routine:** optional hourly reminders for RollSafe's MDMA supplement routine and how much water to drink."

- [ ] **Step 5: Build and suite**, then **commit** `git add -A app/src/main CHANGELOG.md README.md && git commit -m "Plan the supplement routine from the MDMA page"`

---

### Task 6: Offer after logging MDMA

**Files:**
- Modify: `ExperienceViewModel.kt`, `ExperienceScreen.kt`
- Create: `app/src/main/java/com/isaakhanimann/journal/data/supplements/RoutineOffer.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/data/supplements/RoutineOfferTest.kt`

**Interfaces:**
- Produces: `fun routineOfferDose(ownMdmaTimes: List<Instant>, now: Instant, isRoutineRunning: Boolean, isDismissed: Boolean): Instant?` (the latest own MDMA dose within the last 2 h, or null).

- [ ] **Step 1: Failing test**

```kotlin
package com.isaakhanimann.journal.data.supplements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RoutineOfferTest {
    private val now = Instant.parse("2026-10-16T23:00:00Z")

    @Test
    fun offeredForARecentDose() {
        val dose = now.minus(Duration.ofMinutes(30))
        assertEquals(dose, routineOfferDose(listOf(dose), now, isRoutineRunning = false, isDismissed = false))
    }

    @Test
    fun notOfferedWhenOldRunningOrDismissed() {
        val dose = now.minus(Duration.ofMinutes(30))
        assertNull(routineOfferDose(listOf(now.minus(Duration.ofHours(3))), now, false, false))
        assertNull(routineOfferDose(listOf(dose), now, isRoutineRunning = true, isDismissed = false))
        assertNull(routineOfferDose(listOf(dose), now, isRoutineRunning = false, isDismissed = true))
        assertNull(routineOfferDose(emptyList(), now, false, false))
    }
}
```

- [ ] **Step 2: Run** — Expected: FAIL, unresolved `routineOfferDose`.

- [ ] **Step 3: Implement**

```kotlin
package com.isaakhanimann.journal.data.supplements

import java.time.Duration
import java.time.Instant

// the after-dose reminders are offered for an MDMA dose of the user's own from the last 2 hours
fun routineOfferDose(ownMdmaTimes: List<Instant>, now: Instant, isRoutineRunning: Boolean, isDismissed: Boolean): Instant? {
    if (isRoutineRunning || isDismissed) return null
    return ownMdmaTimes.filter { !it.isAfter(now) && Duration.between(it, now) <= Duration.ofHours(2) }.maxOrNull()
}
```

- [ ] **Step 4: Run** — Expected: PASS (2 tests).

- [ ] **Step 5: Wire.** In `ExperienceViewModel` inject `SupplementRoutineController`; add `val routineOfferDoseFlow: StateFlow<Instant?>` combining this experience's own MDMA ingestion times (from `ingestionsWithCompanionsFlow`, `consumerName == null && substanceName == "MDMA"`), `controller.activeRoutineFlow`, `userPreferences.dismissedRoutineOffersFlow`, and `currentTimeFlow` into `routineOfferDose(...)`; `fun startRoutineFrom(dose: Instant) = viewModelScope.launch { controller.start(RoutineKind.REGULAR, dose, 250) }`; `fun dismissRoutineOffer() = viewModelScope.launch { userPreferences.dismissRoutineOffer(experienceId) }`. In `ExperienceScreen`, thread `routineOfferDose: Instant?`, `onStartRoutine: (Instant) -> Unit`, `onDismissRoutineOffer: () -> Unit` (defaults null/no-op) and show, right after the timeline section, an `ElevatedCard` "Start RollSafe's after-dose reminders?" / "Hourly supplement and water reminders until 7 h after your dose." with "Start" and "No thanks" buttons when `routineOfferDose != null`. Start needs the notification permission: reuse the same request pattern as Task 5.

- [ ] **Step 6: Build and suite**, then **commit** `git add -A app/src && git commit -m "Offer the after-dose reminders after logging MDMA"`

---

### Task 7: Emulator check

**Files:** none (verification only). Use a separate `.demo` install with an imported test journal, never the user's own journal.

- [ ] **Step 1:** Plan a regular routine with the dose 4 h and 2 minutes from now: no reminder at start (nothing past), the first reminder ("4 h before", ginger) arrives about 2 minutes later; the MDMA page shows "Next: 3 h before …".
- [ ] **Step 2:** Tap the reminder: the MDMA page opens. Tap "Stop routine" on the next reminder (or Stop in the app): the routine section returns to "Plan…", `adb shell dumpsys alarm | grep psyjournal` shows no pending supplement alarm.
- [ ] **Step 3:** Start a regular routine with the dose 61 minutes from now. When the "1 h before" reminder has posted (about a minute later), restart the emulator (`adb reboot`). After boot: `adb shell dumpsys alarm | grep -A3 psyjournal` shows the supplement alarm armed for the dose time, and the "1 h before" reminder is not posted again.
- [ ] **Step 4:** Log MDMA now in a new experience: the experience screen shows the offer; Start creates a routine from that dose (MDMA page shows "Next: +1 h"); "No thanks" hides it for that experience.
- [ ] **Step 5:** With "Live notification details on lock screen" off, the locked screen shows "PsyJournal reminder".
- [ ] **Step 6:** Uninstall the demo copy and delete the test file.
