package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.theme.JournalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChooseDoseMushroomWeightTest {
    @get:Rule
    val rule = createComposeRule()

    private var doseText by mutableStateOf("")

    private fun showDoseScreen(substanceName: String = "Psilocybin mushrooms") = rule.setContent {
        JournalTheme {
            ChooseDoseScreen(
                navigateToVolumetricDosingScreen = {},
                navigateToSaferSniffingScreen = {},
                substanceName = substanceName,
                roaDose = null,
                administrationRoute = AdministrationRoute.ORAL,
                doseRemark = null,
                doseText = doseText,
                onChangeDoseText = { doseText = it },
                estimatedDoseStandardDeviationText = "",
                onChangeEstimatedDoseStandardDeviationText = {},
                isValidDose = true,
                isEstimate = false,
                onChangeIsEstimate = {},
                navigateToNext = {},
                useUnknownDoseAndNavigate = {},
                currentDoseClass = null,
                purityText = "100",
                onPurityChange = {},
                isValidPurity = true,
                convertedDoseAndUnitText = null,
                isShowingUnitsField = false,
                units = "mg",
                onChangeOfUnits = {},
                navigateToCreateCustomUnit = {},
                navigateToCreateMushroomUnit = { _, _ -> },
            )
        }
    }

    @Test
    fun thePsilocybinDoseIsEnteredUntilMushroomWeightIsChosen() {
        showDoseScreen()
        rule.onNodeWithText("Pure Dose").assertIsDisplayed()
        rule.onNodeWithText("Mushrooms").assertDoesNotExist()
    }

    @Test
    fun choosingMushroomWeightHidesThePureDoseAndFillsItIn() {
        showDoseScreen()
        rule.onNodeWithText("Mushroom weight").performClick()
        rule.onNodeWithText("Pure Dose").assertDoesNotExist()
        rule.onNodeWithText("Mushrooms").performTextInput("2,5")
        assertEquals("25", doseText)
    }

    @Test
    fun choosingMushroomWeightClearsATypedDose() {
        showDoseScreen()
        rule.onNodeWithText("Pure Dose").performTextInput("40")
        rule.onNodeWithText("Mushroom weight").performClick()
        assertEquals("", doseText)
    }

    @Test
    fun choosingMushroomWeightAgainKeepsTheWeighedDose() {
        showDoseScreen()
        rule.onNodeWithText("Mushroom weight").performClick()
        rule.onNodeWithText("Mushrooms").performTextInput("2")
        rule.onNodeWithText("Mushroom weight").performClick()
        assertEquals("20", doseText)
    }

    @Test
    fun goingBackToPsilocybinDoseKeepsTheWorkedOutDoseToEdit() {
        showDoseScreen()
        rule.onNodeWithText("Mushroom weight").performClick()
        rule.onNodeWithText("Mushrooms").performTextInput("2")
        rule.onNodeWithText("Psilocybin dose").performClick()
        rule.onNodeWithText("Mushrooms").assertDoesNotExist()
        rule.onNodeWithText("Pure Dose").assertIsDisplayed()
        assertEquals("20", doseText)
    }

    @Test
    fun choosingMushroomWeightHidesTheCustomUnitCard() {
        showDoseScreen()
        rule.onNodeWithText("Create a custom unit").assertExists()
        rule.onNodeWithText("Mushroom weight").performClick()
        rule.onNodeWithText("Create a custom unit").assertDoesNotExist()
        rule.onNodeWithText("Psilocybin dose").performClick()
        rule.onNodeWithText("Create a custom unit").assertExists()
    }

    @Test
    fun otherSubstancesHaveNoMushroomWeightChoice() {
        showDoseScreen(substanceName = "LSD")
        rule.onNodeWithText("Mushroom weight").assertDoesNotExist()
    }
}
