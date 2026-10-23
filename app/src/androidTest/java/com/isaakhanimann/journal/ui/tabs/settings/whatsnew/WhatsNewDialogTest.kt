package com.isaakhanimann.journal.ui.tabs.settings.whatsnew

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.ui.theme.JournalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WhatsNewDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val release = ChangelogRelease(
        version = "12.2",
        date = LocalDate.of(2026, 10, 30),
        sections = listOf(
            ChangelogSection(
                "Added",
                listOf(ChangelogItem("**Journal filter.** The funnel in the Journal shows only some experiences."))
            )
        )
    )

    private var detailsShown = false
    private var dismissed = false

    private fun showDialog(releases: List<ChangelogRelease> = listOf(release)) = rule.setContent {
        JournalTheme {
            WhatsNewDialog(releases = releases, onDismiss = { dismissed = true }, onShowDetails = { detailsShown = true })
        }
    }

    @Test
    fun itListsTheLeadOfEachChangeWithoutTheDetails() {
        showDialog()
        rule.onNodeWithText("What's new in 12.2").assertIsDisplayed()
        rule.onNodeWithText("Journal filter.").assertIsDisplayed()
        rule.onNodeWithText("The funnel", substring = true).assertDoesNotExist()
    }

    @Test
    fun detailsOpensTheFullNotes() {
        showDialog()
        rule.onNodeWithText("Details").performClick()
        assertTrue(detailsShown)
    }

    @Test
    fun okClosesIt() {
        showDialog()
        rule.onNodeWithText("OK").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun severalReleasesAreListedUnderTheirVersions() {
        showDialog(listOf(release, release.copy(version = "12.1.1")))
        rule.onNodeWithText("What's new").assertIsDisplayed()
        rule.onNodeWithText("12.1.1").assertIsDisplayed()
    }
}
