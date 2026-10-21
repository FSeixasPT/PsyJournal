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

    @Test
    fun aCustomUnitIsOneGramOfThatKindWithItsRangeAsTheDeviation() {
        val azurescens = mushroomKinds.single { it.name == "P. azurescens" }
        val unit = azurescens.customUnit(isDried = true)
        assertEquals("P. azurescens, dried", unit.name)
        assertEquals(15.0, unit.mgPerGram, 1e-9)
        // the low to high range is read as about two standard deviations either side
        assertEquals(3.25, unit.standardDeviation, 1e-9)
    }

    @Test
    fun aWeightWorksOutItsDoseText() {
        assertEquals("25", MushroomWeight(gramsText = "2.5").doseText)
        assertEquals("20", MushroomWeight(gramsText = "20", isDried = false).doseText)
    }

    @Test
    fun noWeightOrZeroHasNoDose() {
        assertEquals("", MushroomWeight().doseText)
        assertEquals("", MushroomWeight(gramsText = "0").doseText)
        assertEquals("", MushroomWeight(gramsText = ".").doseText)
    }

    @Test
    fun aFreshCustomUnitHoldsAsMuchAsAFreshGram() {
        assertEquals("Truffles, fresh", truffles.customUnit(isDried = false).name)
        assertEquals(1.0, truffles.customUnit(isDried = false).mgPerGram, 1e-9)
    }
}
