package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import androidx.compose.ui.test.assertIsDisplayed
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
    private var savedUnit: MushroomCustomUnit? = null
    private var savedGrams: Double? = null

    private fun showHelper() = rule.setContent {
        JournalTheme {
            MushroomDoseHelper(
                onUseDose = { usedDose = it },
                onSaveAsCustomUnit = { unit, grams ->
                    savedUnit = unit
                    savedGrams = grams
                }
            )
        }
    }

    @Test
    fun savingAsACustomUnitPassesTheKindAndTheGrams() {
        showHelper()
        rule.onNodeWithText("P. cubensis").performClick()
        rule.onNodeWithText("Truffles").performClick()
        rule.onNodeWithText("Fresh").performClick()
        rule.onNodeWithText("Mushrooms").performTextInput("10")
        rule.onNodeWithText("Save as custom unit").performClick()
        assertEquals("Truffles, fresh", savedUnit?.name)
        assertEquals(10.0, savedGrams)
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

    @Test
    fun aStrongerSpeciesGivesMorePsilocybinAndShowsItsRange() {
        showHelper()
        rule.onNodeWithText("P. cubensis").performClick()
        rule.onNodeWithText("P. azurescens").performClick()
        rule.onNodeWithText("Mushrooms").performTextInput("2")
        rule.onNodeWithText("Could be 20–46 mg", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Use 30 mg").performClick()
        assertEquals("30", usedDose)
    }
}
