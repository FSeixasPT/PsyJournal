package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import org.junit.Assert.assertEquals
import org.junit.Test

class MushroomDoseTest {
    private val cubensis = mushroomKinds.first()
    private val truffles = mushroomKinds.single { it.name == "Truffles" }

    @Test
    fun driedCubensisAreAboutOnePercentPsilocybin() {
        assertEquals(25.0, cubensis.psilocybinMg(grams = 2.5, isDried = true), 1e-9)
    }

    @Test
    fun freshCubensisAreAboutATenthOfAPercent() {
        assertEquals(20.0, cubensis.psilocybinMg(grams = 20.0, isDried = false), 1e-9)
    }

    @Test
    fun freshTrufflesHoldMoreThanFreshMushroomsBecauseTheyAreLessWater() {
        assertEquals(10.0, truffles.psilocybinMg(grams = 10.0, isDried = false), 1e-9)
    }

    @Test
    fun theRangeUsesTheKindsLowestAndHighestPercent() {
        assertEquals(12.5, cubensis.psilocybinMg(grams = 2.5, isDried = true, percent = cubensis.lowPercent), 1e-9)
        assertEquals(37.5, cubensis.psilocybinMg(grams = 2.5, isDried = true, percent = cubensis.highPercent), 1e-9)
    }

    @Test
    fun everyKindsTypicalPercentLiesInsideItsRange() {
        mushroomKinds.forEach { kind ->
            assert(kind.lowPercent < kind.typicalPercent && kind.typicalPercent < kind.highPercent) { kind.name }
        }
    }
}
