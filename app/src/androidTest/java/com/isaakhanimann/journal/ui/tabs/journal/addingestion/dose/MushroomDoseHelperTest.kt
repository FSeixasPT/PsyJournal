package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.theme.JournalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MushroomDoseHelperTest {
    @get:Rule
    val rule = createComposeRule()

    private var filledDose: String? = null
    private var savedUnit: MushroomCustomUnit? = null
    private var savedGrams: Double? = null

    private fun showHelper() = rule.setContent {
        JournalTheme {
            MushroomDoseHelper(
                onDoseChange = { filledDose = it },
                onSaveAsCustomUnit = { unit, grams ->
                    savedUnit = unit
                    savedGrams = grams
                }
            )
        }
    }

    @Test
    fun nothingIsFilledInUntilTheMushroomsAreWeighed() {
        showHelper()
        rule.onNodeWithText("Fresh").performClick()
        assertNull(filledDose)
    }

    @Test
    fun gramsOfDriedMushroomsFillInTheDose() {
        showHelper()
        rule.onNodeWithText("Mushrooms").performTextInput("2,5")
        assertEquals("25", filledDose)
    }

    @Test
    fun switchingToFreshFillsInATenthOfThePsilocybin() {
        showHelper()
        rule.onNodeWithText("Mushrooms").performTextInput("20")
        rule.onNodeWithText("Fresh").performClick()
        assertEquals("20", filledDose)
    }

    @Test
    fun aStrongerSpeciesFillsInMorePsilocybinAndShowsItsRange() {
        showHelper()
        rule.onNodeWithText("Mushrooms").performTextInput("2")
        rule.onNodeWithText("P. cubensis").performClick()
        rule.onNodeWithText("P. azurescens").performClick()
        rule.onNodeWithText("Could be 20–46 mg", substring = true).assertIsDisplayed()
        assertEquals("30", filledDose)
    }

    @Test
    fun clearingTheWeightClearsTheDoseItFilledIn() {
        showHelper()
        rule.onNodeWithText("Mushrooms").performTextInput("2")
        rule.onNodeWithText("Mushrooms").performTextClearance()
        assertEquals("", filledDose)
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
}
