# Frequency Warnings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Warn users when they use MDMA/MDA, psychedelics or ketamine more often than harm-reduction advice suggests, counting sessions (experiences), in the add flow, on the substance page and in the redose preview.

**Architecture:** Plain-Kotlin rules, parser and checker in `data/frequency/` (no Android dependencies, fully unit-tested); one new Room query for past own ingestions; three small UI hooks that call the checker.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Room, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-14-frequency-warnings-design.md`

## Global Constraints

- Every new Kotlin file starts with the GPL license header: copy the first 17 lines of `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/components/ExperienceDuration.kt`.
- Only the user's own ingestions count (`consumerName == null`); ingestions hidden from the timeline still count.
- A session is an experience; ingestions in the current experience never warn each other.
- Warnings never block any flow.
- Build with JDK 21: `export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"`. Run tests with `./gradlew testDebugUnitTest --tests "<class>" -q`, full suite `./gradlew testDebugUnitTest assembleDebug -q`, then `./gradlew --stop`.
- Every user-facing change gets a CHANGELOG.md entry under "## Unreleased" in the same commit.
- Commits end with the session's `Co-Authored-By` / `Claude-Session` trailer lines; git author is the noreply address already configured.

## Review Focus

1. A redose logged in the same experience as the first dose must never warn (MDMA redose during the roll).
2. An experience still "current" when logging (any ingestion in the last 12 hours) must not count as a previous session, even if the new dose is logged after midnight.
3. Ingestions logged for someone else (consumer set) must never trigger the user's warnings.
4. Unreadable PsychonautWiki tolerance text must produce no warning, never a crash or a guessed gap.
5. Ketamine counts sessions, not doses: three doses in one experience is one session.

---

### Task 1: Tolerance duration parser

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/data/frequency/ToleranceDuration.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/data/frequency/ToleranceDurationTest.kt`

**Interfaces:**
- Produces: `fun parseToleranceDuration(text: String?): java.time.Duration?`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.data.frequency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class ToleranceDurationTest {
    @Test
    fun singleValues() {
        assertEquals(Duration.ofDays(5), parseToleranceDuration("5 days"))
        assertEquals(Duration.ofDays(14), parseToleranceDuration("2 weeks"))
        assertEquals(Duration.ofDays(30), parseToleranceDuration("1 month"))
        assertEquals(Duration.ofHours(2), parseToleranceDuration("2 hours"))
    }

    @Test
    fun rangesUseTheUpperEnd() {
        assertEquals(Duration.ofDays(10), parseToleranceDuration("7-10 days"))
        assertEquals(Duration.ofDays(14), parseToleranceDuration("7 - 14 days"))
        assertEquals(Duration.ofDays(14), parseToleranceDuration("1 - 2 weeks"))
    }

    @Test
    fun decimals() {
        assertEquals(Duration.ofDays(75), parseToleranceDuration("2.5 months"))
    }

    @Test
    fun unreadableTextGivesNull() {
        assertNull(parseToleranceDuration("almost immediately after ingestion"))
        assertNull(parseToleranceDuration(""))
        assertNull(parseToleranceDuration(null))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*ToleranceDurationTest" -q`
Expected: FAIL, compilation error "Unresolved reference: parseToleranceDuration".

- [ ] **Step 3: Implement**

```kotlin
package com.isaakhanimann.journal.data.frequency

import java.time.Duration
import kotlin.math.roundToLong

private val durationPattern =
    Regex("""(\d+(?:\.\d+)?)\s*(?:-\s*(\d+(?:\.\d+)?))?\s*(hour|day|week|month)s?""", RegexOption.IGNORE_CASE)

// PsychonautWiki tolerance text such as "7-10 days" or "2.5 months"; ranges use the upper end,
// a month is 30 days, and anything else is null so it never turns into a guessed gap
fun parseToleranceDuration(text: String?): Duration? {
    val match = text?.let { durationPattern.find(it) } ?: return null
    val value = match.groupValues[2].ifEmpty { match.groupValues[1] }.toDouble()
    val minutesPerUnit = when (match.groupValues[3].lowercase()) {
        "hour" -> 60.0
        "day" -> 60.0 * 24
        "week" -> 60.0 * 24 * 7
        else -> 60.0 * 24 * 30
    }
    return Duration.ofMinutes((value * minutesPerUnit).roundToLong())
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*ToleranceDurationTest" -q`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/data/frequency/ToleranceDuration.kt app/src/test/java/com/isaakhanimann/journal/data/frequency/ToleranceDurationTest.kt
git commit -m "Parse PsychonautWiki tolerance times into durations"
```

---

### Task 2: Frequency rules

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/data/frequency/FrequencyRules.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/data/frequency/FrequencyRulesTest.kt`

**Interfaces:**
- Consumes: `parseToleranceDuration` (Task 1); `Substance` (`name`, `url`, `categories`, `tolerance?.full`, `tolerance?.zero`).
- Produces:
  - `sealed interface FrequencyRule { val substanceNames: Set<String>; val reason: String }`
  - `data class MinimumGapRule(substanceNames, gap: Duration, reason, sourceUrl: String)`
  - `data class ToleranceRule(resetTimes: Map<String, Duration>, sourceUrls: Map<String, String>, reason)` with `substanceNames = resetTimes.keys`
  - `data class MaxSessionsRule(substanceNames, maxOtherSessions: Int, window: Duration, reason, sourceUrl: String)` (warns when other sessions in the window are `>= maxOtherSessions`)
  - `fun buildFrequencyRules(substances: List<Substance>): List<FrequencyRule>`
  - `fun List<FrequencyRule>.ruleFor(substanceName: String): FrequencyRule?`

- [ ] **Step 1: Write the failing test** (uses the real bundled data; unit tests run with `app/` as working directory)

```kotlin
package com.isaakhanimann.journal.data.frequency

import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration

class FrequencyRulesTest {
    private val substances = SubstanceParser()
        .parseSubstanceFile(File("src/main/assets/Substances.json").readText()).substances
    private val rules = buildFrequencyRules(substances)

    @Test
    fun mdmaAndMdaShareThreeMonths() {
        val rule = rules.ruleFor("MDMA") as MinimumGapRule
        assertEquals(setOf("MDMA", "MDA"), rule.substanceNames)
        assertEquals(Duration.ofDays(90), rule.gap)
        assertTrue(rules.ruleFor("MDA") === rule)
    }

    @Test
    fun psychedelicsUseTheirOwnResetTime() {
        val rule = rules.ruleFor("LSD") as ToleranceRule
        assertEquals(Duration.ofDays(5), rule.resetTimes["LSD"])
        assertEquals(Duration.ofDays(7), rule.resetTimes["Psilocybin mushrooms"])
        assertEquals(Duration.ofDays(14), rule.resetTimes["1P-LSD"])
        assertTrue(rules.ruleFor("Mescaline") === rule)
        assertEquals(70, rule.resetTimes.size)
    }

    @Test
    fun excludedSubstancesHaveNoPsychedelicRule() {
        listOf("2C-B", "DMT", "5-MeO-DMT", "Ayahuasca").forEach { assertNull(it, rules.ruleFor(it)) }
        assertTrue(rules.ruleFor("MDA") is MinimumGapRule)
    }

    @Test
    fun ketamineCountsSessionsPerWeek() {
        val rule = rules.ruleFor("Ketamine") as MaxSessionsRule
        assertEquals(2, rule.maxOtherSessions)
        assertEquals(Duration.ofDays(7), rule.window)
    }

    @Test
    fun otherSubstancesHaveNoRule() {
        listOf("Cocaine", "Alcohol", "Cannabis").forEach { assertNull(it, rules.ruleFor(it)) }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*FrequencyRulesTest" -q`
Expected: FAIL, unresolved `buildFrequencyRules`.

- [ ] **Step 3: Implement**

```kotlin
package com.isaakhanimann.journal.data.frequency

import com.isaakhanimann.journal.data.substances.classes.Substance
import java.time.Duration

sealed interface FrequencyRule {
    val substanceNames: Set<String>
    val reason: String
}

// warns when a previous session of any substance in the group ended less than the gap ago
data class MinimumGapRule(
    override val substanceNames: Set<String>,
    val gap: Duration,
    override val reason: String,
    val sourceUrl: String,
) : FrequencyRule

// psychedelics: the gap is the reset time of the substance being taken, and every member counts (cross-tolerance)
data class ToleranceRule(
    val resetTimes: Map<String, Duration>,
    val sourceUrls: Map<String, String>,
    override val reason: String,
) : FrequencyRule {
    override val substanceNames: Set<String> get() = resetTimes.keys
}

// warns when there were at least maxOtherSessions other sessions within the window
data class MaxSessionsRule(
    override val substanceNames: Set<String>,
    val maxOtherSessions: Int,
    val window: Duration,
    override val reason: String,
    val sourceUrl: String,
) : FrequencyRule

fun buildFrequencyRules(substances: List<Substance>): List<FrequencyRule> {
    val psychedelics = substances.mapNotNull { substance ->
        val isPsychedelic = substance.categories.any { it.equals("psychedelic", ignoreCase = true) }
        val buildsImmediately = substance.tolerance?.full?.contains("immediately", ignoreCase = true) == true
        val reset = parseToleranceDuration(substance.tolerance?.zero)
        if (isPsychedelic && buildsImmediately && reset != null && reset >= Duration.ofDays(1)) {
            substance to reset
        } else {
            null
        }
    }
    return listOf(
        MinimumGapRule(
            substanceNames = setOf("MDMA", "MDA"),
            gap = Duration.ofDays(90),
            reason = "Waiting at least 3 months between sessions is recommended: serotonin needs time to recover, and frequent use is linked to lasting low mood and memory problems.",
            sourceUrl = "https://tripsitter.substack.com/p/rolling-safe-mdma",
        ),
        ToleranceRule(
            resetTimes = psychedelics.associate { (substance, reset) -> substance.name to reset },
            sourceUrls = psychedelics.associate { (substance, _) -> substance.name to substance.url },
            reason = "Tolerance, also between different psychedelics, means the effects will likely be weaker. Taking more to make up for it raises the risk of a difficult trip.",
        ),
        MaxSessionsRule(
            substanceNames = setOf("Ketamine"),
            maxOtherSessions = 2,
            window = Duration.ofDays(7),
            reason = "Using ketamine several times a week can damage the bladder. Early damage often heals if you cut down.",
            sourceUrl = "https://knowyourstuff.nz/ketamine-bladder-0-stars-do-not-recommend/",
        ),
    )
}

fun List<FrequencyRule>.ruleFor(substanceName: String): FrequencyRule? =
    firstOrNull { substanceName in it.substanceNames }
```

Note: `MinimumGapRule` is listed first so MDA (which is also in the "psychedelic" category) resolves to it; MDA is excluded from `ToleranceRule` anyway because its tolerance text doesn't contain "immediately".

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*FrequencyRulesTest" -q`
Expected: PASS (5 tests). If the 70 count differs, print `rule.resetTimes.keys` and compare with the spec's exclusion list before changing the number.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/data/frequency/FrequencyRules.kt app/src/test/java/com/isaakhanimann/journal/data/frequency/FrequencyRulesTest.kt
git commit -m "Define the frequency rules for MDMA, psychedelics and ketamine"
```

---

### Task 3: Frequency checker

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/data/frequency/FrequencyChecker.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/data/frequency/FrequencyCheckerTest.kt`

**Interfaces:**
- Consumes: the rule classes (Task 2).
- Produces:
  - `data class PastIngestion(val substanceName: String, val time: Instant, val experienceId: Int)`
  - `data class FrequencyWarning(val rule: FrequencyRule, val lastSubstanceName: String, val lastSessionEnd: Instant, val recommendedGap: Duration?, val okAgainAt: Instant?, val sessionsInWindow: Int, val sourceUrl: String)`
  - `fun checkFrequency(rule: FrequencyRule, substanceName: String, at: Instant, currentExperienceIds: Set<Int>, past: List<PastIngestion>): FrequencyWarning?`
  - `fun currentExperienceIdsAt(at: Instant, past: List<PastIngestion>, limit: Duration): Set<Int>` (experiences with an ingestion in `[at - limit, at]`)

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.isaakhanimann.journal.data.frequency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FrequencyCheckerTest {
    private val now = Instant.parse("2026-10-14T20:00:00Z")
    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))
    private fun hoursAgo(hours: Long) = now.minus(Duration.ofHours(hours))

    private val mdma = MinimumGapRule(setOf("MDMA", "MDA"), Duration.ofDays(90), "r", "s")
    private val psychedelics = ToleranceRule(
        resetTimes = mapOf("LSD" to Duration.ofDays(5), "Psilocybin mushrooms" to Duration.ofDays(7)),
        sourceUrls = mapOf("LSD" to "lsd-url", "Psilocybin mushrooms" to "mush-url"),
        reason = "r",
    )
    private val ketamine = MaxSessionsRule(setOf("Ketamine"), 2, Duration.ofDays(7), "r", "s")

    @Test
    fun redoseInTheSameExperienceDoesNotWarn() {
        val past = listOf(PastIngestion("MDMA", hoursAgo(2), experienceId = 1))
        assertNull(checkFrequency(mdma, "MDMA", now, currentExperienceIds = setOf(1), past))
    }

    @Test
    fun mdmaWarnsWithinNinetyDays() {
        val warning = checkFrequency(mdma, "MDMA", now, emptySet(), listOf(PastIngestion("MDMA", daysAgo(89), 1)))
        assertNotNull(warning)
        assertEquals(daysAgo(89).plus(Duration.ofDays(90)), warning!!.okAgainAt)
        assertNull(checkFrequency(mdma, "MDMA", now, emptySet(), listOf(PastIngestion("MDMA", daysAgo(91), 1))))
    }

    @Test
    fun sessionEndsAtItsLastDose() {
        val past = listOf(PastIngestion("MDMA", daysAgo(95), 1), PastIngestion("MDMA", daysAgo(85), 1))
        assertEquals(daysAgo(85), checkFrequency(mdma, "MDMA", now, emptySet(), past)!!.lastSessionEnd)
    }

    @Test
    fun mdaCountsForMdma() {
        val warning = checkFrequency(mdma, "MDMA", now, emptySet(), listOf(PastIngestion("MDA", daysAgo(30), 1)))
        assertEquals("MDA", warning!!.lastSubstanceName)
    }

    @Test
    fun crossToleranceUsesTheResetTimeOfTheLoggedSubstance() {
        val mushrooms3DaysAgo = listOf(PastIngestion("Psilocybin mushrooms", daysAgo(3), 1))
        val warning = checkFrequency(psychedelics, "LSD", now, emptySet(), mushrooms3DaysAgo)
        assertEquals(Duration.ofDays(5), warning!!.recommendedGap)
        assertEquals("lsd-url", warning.sourceUrl)
        val mushrooms6DaysAgo = listOf(PastIngestion("Psilocybin mushrooms", daysAgo(6), 1))
        assertNull(checkFrequency(psychedelics, "LSD", now, emptySet(), mushrooms6DaysAgo))
        val lsd6DaysAgo = listOf(PastIngestion("LSD", daysAgo(6), 1))
        assertNotNull(checkFrequency(psychedelics, "Psilocybin mushrooms", now, emptySet(), lsd6DaysAgo))
    }

    @Test
    fun ketamineCountsSessionsNotDoses() {
        val twoSessions = listOf(
            PastIngestion("Ketamine", daysAgo(2), 1),
            PastIngestion("Ketamine", daysAgo(2).plusSeconds(3600), 1),
            PastIngestion("Ketamine", daysAgo(5), 2),
        )
        assertEquals(2, checkFrequency(ketamine, "Ketamine", now, emptySet(), twoSessions)!!.sessionsInWindow)
        val oneSessionThreeDoses = listOf(
            PastIngestion("Ketamine", daysAgo(2), 1),
            PastIngestion("Ketamine", daysAgo(2).plusSeconds(1800), 1),
            PastIngestion("Ketamine", daysAgo(2).plusSeconds(3600), 1),
        )
        assertNull(checkFrequency(ketamine, "Ketamine", now, emptySet(), oneSessionThreeDoses))
        val oneInWindow = listOf(PastIngestion("Ketamine", daysAgo(2), 1), PastIngestion("Ketamine", daysAgo(9), 2))
        assertNull(checkFrequency(ketamine, "Ketamine", now, emptySet(), oneInWindow))
    }

    @Test
    fun recentExperiencesAreCurrent() {
        val past = listOf(
            PastIngestion("MDMA", hoursAgo(11), 1),
            PastIngestion("MDMA", hoursAgo(13), 2),
            PastIngestion("MDMA", daysAgo(40), 3),
        )
        assertEquals(setOf(1), currentExperienceIdsAt(now, past, Duration.ofHours(12)))
    }

    @Test
    fun futureIngestionsAreIgnored() {
        val past = listOf(PastIngestion("MDMA", now.plus(Duration.ofDays(1)), 1))
        assertNull(checkFrequency(mdma, "MDMA", now, emptySet(), past))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*FrequencyCheckerTest" -q`
Expected: FAIL, unresolved `checkFrequency`.

- [ ] **Step 3: Implement**

```kotlin
package com.isaakhanimann.journal.data.frequency

import java.time.Duration
import java.time.Instant

data class PastIngestion(val substanceName: String, val time: Instant, val experienceId: Int)

data class FrequencyWarning(
    val rule: FrequencyRule,
    // the substance of the most recent previous session, e.g. mushrooms when LSD is logged
    val lastSubstanceName: String,
    val lastSessionEnd: Instant,
    val recommendedGap: Duration?,
    val okAgainAt: Instant?,
    val sessionsInWindow: Int,
    val sourceUrl: String,
)

private data class Session(val end: Instant, val lastSubstanceName: String)

// one session per experience, ending at its last dose of a substance in the rule
private fun previousSessions(
    rule: FrequencyRule,
    at: Instant,
    currentExperienceIds: Set<Int>,
    past: List<PastIngestion>,
): List<Session> = past
    .filter { it.substanceName in rule.substanceNames && it.time <= at && it.experienceId !in currentExperienceIds }
    .groupBy { it.experienceId }
    .values
    .map { doses -> doses.maxBy { it.time }.let { Session(it.time, it.substanceName) } }
    .sortedByDescending { it.end }

fun checkFrequency(
    rule: FrequencyRule,
    substanceName: String,
    at: Instant,
    currentExperienceIds: Set<Int>,
    past: List<PastIngestion>,
): FrequencyWarning? {
    val sessions = previousSessions(rule, at, currentExperienceIds, past)
    val last = sessions.firstOrNull() ?: return null
    val sinceLast = Duration.between(last.end, at)
    return when (rule) {
        is MinimumGapRule -> if (sinceLast < rule.gap) {
            FrequencyWarning(rule, last.lastSubstanceName, last.end, rule.gap, last.end.plus(rule.gap), 1, rule.sourceUrl)
        } else {
            null
        }

        is ToleranceRule -> {
            val reset = rule.resetTimes[substanceName] ?: return null
            if (sinceLast < reset) {
                FrequencyWarning(
                    rule, last.lastSubstanceName, last.end, reset, last.end.plus(reset), 1,
                    rule.sourceUrls[substanceName].orEmpty()
                )
            } else {
                null
            }
        }

        is MaxSessionsRule -> {
            val inWindow = sessions.count { Duration.between(it.end, at) < rule.window }
            if (inWindow >= rule.maxOtherSessions) {
                FrequencyWarning(rule, last.lastSubstanceName, last.end, null, null, inWindow, rule.sourceUrl)
            } else {
                null
            }
        }
    }
}

// the app's own grouping: an experience with a dose in the last hours is the one a new dose would join
fun currentExperienceIdsAt(at: Instant, past: List<PastIngestion>, limit: Duration): Set<Int> =
    past.filter { it.time <= at && Duration.between(it.time, at) <= limit }.map { it.experienceId }.toSet()
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*FrequencyCheckerTest" -q`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/data/frequency/FrequencyChecker.kt app/src/test/java/com/isaakhanimann/journal/data/frequency/FrequencyCheckerTest.kt
git commit -m "Check how long ago the last session of a substance was"
```

---

### Task 4: Warning texts

**Files:**
- Create: `app/src/main/java/com/isaakhanimann/journal/data/frequency/FrequencyText.kt`
- Test: `app/src/test/java/com/isaakhanimann/journal/data/frequency/FrequencyTextTest.kt`

**Interfaces:**
- Consumes: `FrequencyWarning` (Task 3), `getTimeDifferenceText(from, to)` in `ui/utils`.
- Produces:
  - `fun frequencyTitle(warning: FrequencyWarning, now: Instant): String` — e.g. "Last MDMA session was 5 weeks ago", "Last psychedelic session: Psilocybin mushrooms, 3 days ago", "3rd ketamine session in 7 days"
  - `fun frequencyPreviewLine(warning: FrequencyWarning, now: Instant): String` — one short line for the redose preview

- [ ] **Step 1: Write the failing test**

```kotlin
package com.isaakhanimann.journal.data.frequency

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FrequencyTextTest {
    private val now = Instant.parse("2026-10-14T20:00:00Z")
    private val mdma = MinimumGapRule(setOf("MDMA", "MDA"), Duration.ofDays(90), "r", "s")
    private val psychedelics = ToleranceRule(mapOf("LSD" to Duration.ofDays(5)), mapOf(), "r")
    private val ketamine = MaxSessionsRule(setOf("Ketamine"), 2, Duration.ofDays(7), "r", "s")

    private fun warning(rule: FrequencyRule, name: String, ago: Duration, sessions: Int = 1) =
        FrequencyWarning(rule, name, now.minus(ago), null, null, sessions, "s")

    @Test
    fun mdmaTitle() {
        assertEquals("Last MDMA session was 3 days ago", frequencyTitle(warning(mdma, "MDMA", Duration.ofDays(3)), now))
    }

    @Test
    fun psychedelicTitleNamesTheSubstance() {
        assertEquals(
            "Last psychedelic session: Psilocybin mushrooms, 3 days ago",
            frequencyTitle(warning(psychedelics, "Psilocybin mushrooms", Duration.ofDays(3)), now)
        )
    }

    @Test
    fun ketamineTitleCountsThisSession() {
        assertEquals("3rd ketamine session in 7 days", frequencyTitle(warning(ketamine, "Ketamine", Duration.ofDays(2), sessions = 2), now))
    }

    @Test
    fun previewLines() {
        assertEquals(
            "Tolerance from Psilocybin mushrooms 3 days ago: effects will be weaker",
            frequencyPreviewLine(warning(psychedelics, "Psilocybin mushrooms", Duration.ofDays(3)), now)
        )
        assertEquals(
            "Last MDMA session 3 days ago: 3 months between sessions is recommended",
            frequencyPreviewLine(warning(mdma, "MDMA", Duration.ofDays(3)), now)
        )
    }
}
```

`getTimeDifferenceText` returns "3 days" for 3 days and "5 weeks" for 5 weeks (checked while writing this plan).

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*FrequencyTextTest" -q`
Expected: FAIL, unresolved `frequencyTitle`.

- [ ] **Step 3: Implement**

```kotlin
package com.isaakhanimann.journal.data.frequency

import com.isaakhanimann.journal.ui.utils.getTimeDifferenceText
import java.time.Instant

private fun ordinal(n: Int) = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

fun frequencyTitle(warning: FrequencyWarning, now: Instant): String {
    val ago = getTimeDifferenceText(warning.lastSessionEnd, now)
    return when (val rule = warning.rule) {
        is MinimumGapRule -> "Last ${warning.lastSubstanceName} session was $ago ago"
        is ToleranceRule -> "Last psychedelic session: ${warning.lastSubstanceName}, $ago ago"
        is MaxSessionsRule -> "${ordinal(warning.sessionsInWindow + 1)} ${warning.lastSubstanceName.lowercase()} session in ${rule.window.toDays()} days"
    }
}

fun frequencyPreviewLine(warning: FrequencyWarning, now: Instant): String {
    val ago = getTimeDifferenceText(warning.lastSessionEnd, now)
    return when (val rule = warning.rule) {
        is MinimumGapRule -> "Last ${warning.lastSubstanceName} session $ago ago: ${rule.gap.toDays() / 30} months between sessions is recommended"
        is ToleranceRule -> "Tolerance from ${warning.lastSubstanceName} $ago ago: effects will be weaker"
        is MaxSessionsRule -> "${frequencyTitle(warning, now)}: risk of bladder damage"
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*FrequencyTextTest" -q`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/data/frequency/FrequencyText.kt app/src/test/java/com/isaakhanimann/journal/data/frequency/FrequencyTextTest.kt
git commit -m "Word the frequency warnings"
```

---

### Task 5: Past ingestions query and rule book

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/data/room/experiences/ExperienceDao.kt` (next to `getLatestIngestionOfEverySubstanceSinceDate`)
- Modify: `app/src/main/java/com/isaakhanimann/journal/data/room/experiences/ExperienceRepository.kt`
- Create: `app/src/main/java/com/isaakhanimann/journal/data/frequency/FrequencyRuleBook.kt`

**Interfaces:**
- Produces:
  - DAO: `suspend fun getOwnIngestionsSince(substanceNames: List<String>, instant: Instant): List<Ingestion>` and `fun getOwnIngestionsSinceFlow(substanceNames: List<String>, instant: Instant): Flow<List<Ingestion>>`
  - Repository: `suspend fun getPastIngestions(substanceNames: Set<String>, since: Instant): List<PastIngestion>` and `fun getPastIngestionsFlow(substanceNames: Set<String>, since: Instant): Flow<List<PastIngestion>>`
  - `@Singleton class FrequencyRuleBook @Inject constructor(substanceRepo: SubstanceRepository)` with `val rules: List<FrequencyRule>`, `fun ruleFor(name: String): FrequencyRule?`, and `val longestWindow: Duration` (90 days: the longest gap/window of all rules, used as the query's `since`)

- [ ] **Step 1: Add the DAO queries**

```kotlin
    @Query("SELECT * FROM ingestion WHERE consumerName IS NULL AND substanceName IN (:substanceNames) AND time > :instant ORDER BY time DESC")
    suspend fun getOwnIngestionsSince(substanceNames: List<String>, instant: Instant): List<Ingestion>

    @Query("SELECT * FROM ingestion WHERE consumerName IS NULL AND substanceName IN (:substanceNames) AND time > :instant ORDER BY time DESC")
    fun getOwnIngestionsSinceFlow(substanceNames: List<String>, instant: Instant): Flow<List<Ingestion>>
```

- [ ] **Step 2: Add the repository methods**

```kotlin
    suspend fun getPastIngestions(substanceNames: Set<String>, since: Instant): List<PastIngestion> =
        experienceDao.getOwnIngestionsSince(substanceNames.toList(), since).map { PastIngestion(it.substanceName, it.time, it.experienceId) }

    fun getPastIngestionsFlow(substanceNames: Set<String>, since: Instant): Flow<List<PastIngestion>> =
        experienceDao.getOwnIngestionsSinceFlow(substanceNames.toList(), since)
            .map { list -> list.map { PastIngestion(it.substanceName, it.time, it.experienceId) } }
```

(Add imports for `PastIngestion` and `kotlinx.coroutines.flow.map` if missing.)

- [ ] **Step 3: Add the rule book**

```kotlin
package com.isaakhanimann.journal.data.frequency

import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrequencyRuleBook @Inject constructor(substanceRepo: SubstanceRepository) {
    val rules: List<FrequencyRule> = buildFrequencyRules(substanceRepo.getAllSubstances())

    fun ruleFor(substanceName: String): FrequencyRule? = rules.ruleFor(substanceName)

    // how far back previous sessions matter for any rule
    val longestWindow: Duration = rules.maxOf { rule ->
        when (rule) {
            is MinimumGapRule -> rule.gap
            is ToleranceRule -> rule.resetTimes.values.maxOrNull() ?: Duration.ZERO
            is MaxSessionsRule -> rule.window
        }
    }
}
```

Check that `SubstanceRepository` is injectable as a concrete class (it's injected that way in `CheckInteractionsViewModel`); if Hilt binds only the interface, inject `SubstanceRepositoryInterface` instead.

- [ ] **Step 4: Build**

Run: `./gradlew testDebugUnitTest assembleDebug -q`
Expected: BUILD SUCCESSFUL, all tests pass (Room validates the new queries at compile time).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/isaakhanimann/journal/data/room/experiences/ExperienceDao.kt app/src/main/java/com/isaakhanimann/journal/data/room/experiences/ExperienceRepository.kt app/src/main/java/com/isaakhanimann/journal/data/frequency/FrequencyRuleBook.kt
git commit -m "Query past own ingestions for frequency rules"
```

---

### Task 6: Warning card in the add-ingestion flow

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/addingestion/interactions/CheckInteractionsViewModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/addingestion/interactions/CheckInteractionsScreen.kt`
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/addingestion/interactions/FrequencyWarningCard.kt`
- Modify: `CHANGELOG.md`, `README.md`

**Interfaces:**
- Consumes: `FrequencyRuleBook`, `ExperienceRepository.getPastIngestions`, `checkFrequency`, `currentExperienceIdsAt`, `frequencyTitle`, `hourLimitToSeparateIngestions` (`ui/tabs/journal/addingestion/time/FinishIngestionScreenViewModel.kt`).
- Produces: `@Composable fun FrequencyWarningCard(title: String, reason: String, sourceUrl: String, modifier: Modifier = Modifier)` (reused in Task 7).

- [ ] **Step 1: View model.** Inject `private val frequencyRuleBook: FrequencyRuleBook`. Add `var frequencyWarning by mutableStateOf<FrequencyWarning?>(null)`. In the existing `init` coroutine, after loading the interaction data and before `isSearchingForInteractions = false`:

```kotlin
            frequencyRuleBook.ruleFor(substanceName)?.let { rule ->
                val now = Instant.now()
                val past = experienceRepo.getPastIngestions(rule.substanceNames, now.minus(frequencyRuleBook.longestWindow))
                val current = currentExperienceIdsAt(now, past, Duration.ofHours(hourLimitToSeparateIngestions))
                frequencyWarning = checkFrequency(rule, substanceName, now, current, past)
            }
```

- [ ] **Step 2: Card composable** in `FrequencyWarningCard.kt`: an `ElevatedCard` (or `Card` with `MaterialTheme.colorScheme.errorContainer` colors) containing a warning icon + bold `title`, `reason` in `bodyMedium`, and a `TextButton` "Source" that opens `sourceUrl` with `LocalUriHandler.current.openUri`. Add a `@Preview`.

- [ ] **Step 3: Screen.** Add parameters `frequencyTitle: String? = null, frequencyReason: String = "", frequencySourceUrl: String = ""` to the inner `CheckInteractionsScreen`. The view-model overload passes them from `viewModel.frequencyWarning` (title via `frequencyTitle(it, Instant.now())`, reason `it.rule.reason`, source `it.sourceUrl`). Show the card at the top of the content `Column`, right after the progress indicator, when `frequencyTitle != null`, with `Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp)`. It must be visible both when the interaction list is empty and when it isn't.

- [ ] **Step 4: Changelog and README.** Under "## Unreleased" → "### Added":

```markdown
- **Frequency warnings.** When you log MDMA or MDA less than 3 months after your last session, a psychedelic before your tolerance from the last one (of any psychedelic) has reset, or ketamine for the third time in a week, PsyJournal tells you how long ago the last session was and why spacing them out matters, with a source. Redoses within the same experience don't count. The substance page shows when you last took it and the recommended gap, and the redose preview mentions it too.
```

In README "### Stay safer", add after the interaction-warnings bullet:

```markdown
- **Frequency warnings:** PsyJournal tells you when you take MDMA, a psychedelic or ketamine again sooner than harm-reduction advice recommends, counting sessions rather than redoses.
```

- [ ] **Step 5: Build and run the suite**

Run: `./gradlew testDebugUnitTest assembleDebug -q`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main CHANGELOG.md README.md
git commit -m "Warn when logging a substance too soon after the last session"
```

---

### Task 7: Frequency card on the substance page

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/search/substance/SubstanceViewModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/search/substance/SubstanceScreen.kt`
- Create: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/search/substance/FrequencySection.kt`

**Interfaces:**
- Consumes: `FrequencyRuleBook`, `ExperienceRepository.getPastIngestionsFlow`, `checkFrequency`.
- Produces: `data class FrequencySummary(val lastSessionEnd: Instant?, val recommendedGapText: String, val okAgainAt: Instant?, val sessionsInWindow: Int?, val reason: String, val sourceUrl: String)`

- [ ] **Step 1: View model.** Inject `FrequencyRuleBook`. Add `val frequencySummaryFlow: StateFlow<FrequencySummary?>`: `null` when `ruleFor(substanceName)` is null; otherwise map `getPastIngestionsFlow(rule.substanceNames, now - longestWindow)` to a summary. Use `checkFrequency(rule, substanceName, Instant.now(), emptySet(), past)` for `okAgainAt`/`sessionsInWindow` when a warning exists; `lastSessionEnd` is the latest past ingestion's time (or null). `recommendedGapText`: "3 months" for `MinimumGapRule`, the substance's own reset in days ("5 days") for `ToleranceRule`, "at most 2 sessions a week" for `MaxSessionsRule`. Source URL: rule source, or `substance.url` for psychedelics.

- [ ] **Step 2: Section.** `FrequencySection(summary: FrequencySummary)` shows "Last session: <getTimeDifferenceText> ago" (or "No sessions logged recently"), "Recommended gap: …", "OK again from <date>" when `okAgainAt` is in the future (format with the existing date helpers used elsewhere on the page, e.g. `getStringOfPattern` / `getLocalDateTime`), the reason, and a "Source" link.

- [ ] **Step 3: Screen.** Add `frequencySummary: FrequencySummary? = null` to the inner `SubstanceScreen`, pass it from the view-model overload, and render `SectionWithTitle(title = "Frequency") { FrequencySection(it) }` directly after the "Tolerance" section when non-null.

- [ ] **Step 4: Build and run the suite**

Run: `./gradlew testDebugUnitTest assembleDebug -q`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main
git commit -m "Show the last session and recommended gap on substance pages"
```

---

### Task 8: Line in the redose preview

**Files:**
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceViewModel.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/ExperienceScreen.kt`
- Modify: `app/src/main/java/com/isaakhanimann/journal/ui/tabs/journal/experience/timeline/PlannedIngestionUi.kt`

**Interfaces:**
- Consumes: `FrequencyRuleBook`, `getPastIngestionsFlow`, `checkFrequency`, `frequencyPreviewLine`.
- Produces: `PlannedIngestionSection(..., frequencyLine: (substanceName: String) -> String? = { null })`.

- [ ] **Step 1: View model.** Inject `FrequencyRuleBook`. Keep `private val pastRuleIngestionsFlow` = `getPastIngestionsFlow(allRuleSubstanceNames, now - longestWindow).stateIn(...)` where `allRuleSubstanceNames = rules.flatMap { it.substanceNames }.toSet()`. Add:

```kotlin
    fun frequencyPreviewLine(substanceName: String): String? {
        val rule = frequencyRuleBook.ruleFor(substanceName) ?: return null
        val now = Instant.now()
        val warning = checkFrequency(rule, substanceName, now, setOf(experienceId), pastRuleIngestionsFlow.value)
        return warning?.let { frequencyPreviewLine(it, now) }
    }
```

Collect `pastRuleIngestionsFlow` in `ExperienceScreen` (`collectAsState()`) so the preview recomposes when it loads.

- [ ] **Step 2: Preview section.** Add the `frequencyLine` parameter. In the block that builds `lines`, append `state.planned?.let { frequencyLine(it.substanceName) }` to the list in both branches (`listOfNotNull(...)`), so it shows under the redose or "If taken now" text.

- [ ] **Step 3: Wire it.** In `ExperienceScreen.kt` pass `frequencyLine = viewModel::frequencyPreviewLine` (thread it through the screen parameters the same way `plannedIngestionActions` is passed; default `{ null }` for previews).

- [ ] **Step 4: Build and run the suite**

Run: `./gradlew testDebugUnitTest assembleDebug -q`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main
git commit -m "Mention frequency warnings in the redose preview"
```

---

### Task 9: Emulator check

**Files:** none (verification only).

- [ ] **Step 1:** Install the debug build, start the app (`MSYS_NO_PATHCONV=1 adb shell am start -W -n io.github.fseixaspt.psyjournal/com.isaakhanimann.journal.MainActivity`), wait until screenshots are larger than 70 KB.
- [ ] **Step 2:** Log MDMA in a new experience dated 5 weeks ago, then start logging MDMA now: the interactions step shows "Last MDMA session was 5 weeks ago" with the reason and a Source link; "Next" continues. Log the redose in the same experience 2 hours later: no card.
- [ ] **Step 3:** Open MDMA in the Drugs tab: the Frequency section shows the last session, "Recommended gap: 3 months" and the OK-again date.
- [ ] **Step 4:** Log mushrooms 3 days ago, then start logging LSD: "Last psychedelic session: Psilocybin mushrooms, 3 days ago". Start logging 2C-B: no card.
- [ ] **Step 5:** Log ketamine in an experience 2 days ago but set "Consumed by" to another person; then start logging ketamine twice more in separate experiences: the someone-else ingestion must not be counted (the database query filters `consumerName IS NULL`; unit tests can't reach it).
- [ ] **Step 6:** In an experience, open "Preview next ingestion" and pick a substance within its gap: the extra line appears.
- [ ] **Step 7:** Delete the test experiences afterwards so the user's emulator journal is as before.
