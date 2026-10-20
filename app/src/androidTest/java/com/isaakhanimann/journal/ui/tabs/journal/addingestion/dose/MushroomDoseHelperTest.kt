package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.theme.JournalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MushroomDoseHelperTest {
    @get:Rule
    val rule = createComposeRule()

    private var usedDose: String? = null

    private fun showHelper() = rule.setContent {
        JournalTheme { MushroomDoseHelper(onUseDose = { usedDose = it }) }
    }

    @Test
    fun gramsOfDriedMushroomsBecomeTheDose() {
        showHelper()
        rule.onNodeWithText("Mushrooms").performTextInput("2,5")
        rule.onNodeWithText("Use 25 mg").performClick()
        assertEquals("25", usedDose)
    }

    @Test
    fun freshMushroomsHaveATenthOfThePsilocybin() {
        showHelper()
        rule.onNodeWithText("Mushrooms").performTextInput("20")
        rule.onNodeWithText("Fresh").performClick()
        rule.onNodeWithText("Use 20 mg").performClick()
        assertEquals("20", usedDose)
    }
}
