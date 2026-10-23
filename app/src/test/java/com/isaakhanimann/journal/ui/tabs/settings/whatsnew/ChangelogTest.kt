package com.isaakhanimann.journal.ui.tabs.settings.whatsnew

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import com.isaakhanimann.journal.ui.VERSION_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ChangelogTest {
    private val markdown = """
        # Changelog

        PsyJournal is a fork of [PsychonautWiki Journal](https://example.org) 11.11.

        ## Unreleased

        ### Added

        - **Not out yet.** Still being made.

        ## 12.1 — 2026-10-17

        ### Added

        - **Journal filter.** The funnel in the Journal shows only some experiences.
        - **Widgets.** Follow the current experience:
          - **Timeline:** the chart.
            - **Deeper** detail.
          - Status only.

        ### Fixed

        - Mirtazapine was wrongly flagged. It matched the word "depressant".

        ## 12.0 — 2026-10-14

        ### Added

        - **Live notification.** Follow it from the shade.
    """.trimIndent()

    private val releases = parseChangelog(markdown)

    @Test
    fun releasesAreReadNewestFirstWithoutUnreleased() {
        assertEquals(listOf("12.1", "12.0"), releases.map { it.version })
        assertEquals(LocalDate.of(2026, 10, 17), releases[0].date)
    }

    @Test
    fun sectionsKeepTheirItemsInOrder() {
        val sections = releases[0].sections
        assertEquals(listOf("Added", "Fixed"), sections.map { it.title })
        assertEquals(
            "**Journal filter.** The funnel in the Journal shows only some experiences.",
            sections[0].items[0].text
        )
        assertEquals(2, sections[0].items.size)
    }

    @Test
    fun indentedBulletsBelongToTheBulletAboveThem() {
        val widgets = releases[0].sections[0].items[1]
        assertEquals(listOf("**Timeline:** the chart.", "Status only."), widgets.children.map { it.text })
        assertEquals(listOf("**Deeper** detail."), widgets.children[0].children.map { it.text })
    }

    @Test
    fun theSummaryIsTheBoldLead() {
        assertEquals("Journal filter.", releases[0].sections[0].items[0].summary())
    }

    @Test
    fun withoutABoldLeadTheSummaryIsTheFirstSentence() {
        assertEquals("Mirtazapine was wrongly flagged.", releases[0].sections[1].items[0].summary())
    }

    @Test
    fun afterAnUpdateTheNewReleasesUpToThisOneAreAnnounced() {
        val all = parseChangelog(markdown.replace("## 12.1 —", "## 12.2 — 2026-10-30\n\n### Fixed\n\n- A fix.\n\n## 12.1 —"))
        assertEquals(listOf("12.2", "12.1"), releasesToAnnounce(all, lastSeenVersion = "12.0", currentVersion = "12.2").map { it.version })
    }

    @Test
    fun aReleaseAlreadySeenIsNotAnnouncedAgain() {
        assertEquals(emptyList<ChangelogRelease>(), releasesToAnnounce(releases, lastSeenVersion = "12.1", currentVersion = "12.1"))
    }

    @Test
    fun updatingFromBeforeWhatsNewExistedAnnouncesOnlyThisRelease() {
        assertEquals(listOf("12.1"), releasesToAnnounce(releases, lastSeenVersion = null, currentVersion = "12.1").map { it.version })
    }

    @Test
    fun versionsCompareByNumberNotText() {
        assertTrue(compareVersions("12.10", "12.9") > 0)
        assertTrue(compareVersions("12.1.1", "12.1") > 0)
        assertTrue(compareVersions("12.2", "12.1.1") > 0)
        assertEquals(0, compareVersions("12.1", "12.1"))
    }

    @Test
    fun boldAndLinksBecomeStyledText() {
        val text = inlineMarkdown("**Bold** and [a link](https://example.org).")
        assertEquals("Bold and a link.", text.text)
        val bold = text.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals(0 to 4, bold.start to bold.end)
        val link = text.getLinkAnnotations(0, text.length).single()
        assertEquals("https://example.org", (link.item as LinkAnnotation.Url).url)
        assertEquals(9 to 15, link.start to link.end)
    }

    @Test
    fun withoutDonationsTheKofiItemsAreLeftOut() {
        val all = parseChangelog(markdown.replace("- **Live notification.**", "- **Donate.** Opens the Ko-fi page.\n- **Live notification.**"))
        val kept = withoutDonationMentions(all)
        assertEquals(listOf("**Live notification.** Follow it from the shade."), kept[1].sections[0].items.map { it.text })
        assertEquals(releases[0], kept[0])
    }

    @Test
    fun aSectionOrReleaseLeftEmptyIsDropped() {
        val onlyDonate = parseChangelog(
            """
            ## 12.1 — 2026-10-17

            ### Added

            - **Donate.** Opens the ko-fi page.

            ### Fixed

            - A fix.

            ## 12.0 — 2026-10-14

            ### Added

            - **Donate.** Ko-fi.
            """.trimIndent()
        )
        val kept = withoutDonationMentions(onlyDonate)
        assertEquals(listOf("12.1"), kept.map { it.version })
        assertEquals(listOf("Fixed"), kept[0].sections.map { it.title })
    }

    @Test
    fun theAppsOwnChangelogHasAnEntryForThisVersion() {
        val own = parseChangelog(File("../CHANGELOG.md").readText())
        assertTrue(own.any { it.version == VERSION_NAME })
        own.flatMap { it.sections }.flatMap { it.items }.forEach { item ->
            assertTrue(item.text, item.summary().isNotBlank())
        }
    }
}
