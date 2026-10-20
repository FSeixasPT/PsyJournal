package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import org.junit.Assert.assertEquals
import org.junit.Test

class MushroomDoseTest {
    @Test
    fun driedCubensisAreAboutOnePercentPsilocybin() {
        assertEquals(25.0, psilocybinMgInMushrooms(grams = 2.5, isDried = true), 1e-9)
    }

    @Test
    fun freshCubensisAreAboutATenthOfAPercent() {
        assertEquals(20.0, psilocybinMgInMushrooms(grams = 20.0, isDried = false), 1e-9)
    }
}
