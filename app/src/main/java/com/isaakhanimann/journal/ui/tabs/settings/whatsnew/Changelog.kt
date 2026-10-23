package com.isaakhanimann.journal.ui.tabs.settings.whatsnew

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import java.time.LocalDate

// the app reads CHANGELOG.md itself (the build copies it into the assets), so the release notes are written once
const val CHANGELOG_ASSET = "CHANGELOG.md"

data class ChangelogRelease(val version: String, val date: LocalDate?, val sections: List<ChangelogSection>)

data class ChangelogSection(val title: String, val items: List<ChangelogItem>)

data class ChangelogItem(val text: String, val children: List<ChangelogItem> = emptyList())

private val releaseHeading = Regex("""^## (\S+)(?:\s+—\s+(\d{4}-\d{2}-\d{2}))?""")
private val bullet = Regex("""^( *)- (.*)$""")

private class ItemBuilder(val text: String, val indent: Int) {
    val children = mutableListOf<ItemBuilder>()
    fun build(): ChangelogItem = ChangelogItem(text, children.map { it.build() })
}

// newest first, as in the file; Unreleased is left out, it's only what main has so far
fun parseChangelog(markdown: String): List<ChangelogRelease> {
    val releases = mutableListOf<ChangelogRelease>()
    var version: String? = null
    var date: LocalDate? = null
    val sections = mutableListOf<ChangelogSection>()
    var sectionTitle: String? = null
    val items = mutableListOf<ItemBuilder>()
    val openItems = ArrayDeque<ItemBuilder>()

    fun closeSection() {
        sectionTitle?.let { sections.add(ChangelogSection(it, items.map { item -> item.build() })) }
        sectionTitle = null
        items.clear()
        openItems.clear()
    }

    fun closeRelease() {
        closeSection()
        version?.let { releases.add(ChangelogRelease(it, date, sections.toList())) }
        version = null
        sections.clear()
    }

    for (line in markdown.lines()) {
        val heading = releaseHeading.find(line)
        when {
            heading != null -> {
                closeRelease()
                val name = heading.groupValues[1]
                if (name != "Unreleased") {
                    version = name
                    date = heading.groupValues[2].takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                }
            }
            line.startsWith("### ") -> {
                closeSection()
                if (version != null) sectionTitle = line.removePrefix("### ").trim()
            }
            sectionTitle != null -> {
                val match = bullet.find(line) ?: continue
                val item = ItemBuilder(match.groupValues[2].trim(), match.groupValues[1].length)
                while (openItems.isNotEmpty() && openItems.last().indent >= item.indent) openItems.removeLast()
                if (openItems.isEmpty()) items.add(item) else openItems.last().children.add(item)
                openItems.addLast(item)
            }
        }
    }
    closeRelease()
    return releases
}

// what the popup lists: the bold lead of an entry, or its first sentence when it has none
fun ChangelogItem.summary(): String {
    if (text.startsWith("**")) {
        val end = text.indexOf("**", startIndex = 2)
        if (end > 2) return text.substring(2, end).trim()
    }
    val sentenceEnd = text.indexOf(". ")
    return if (sentenceEnd == -1) text else text.substring(0, sentenceEnd + 1)
}

fun compareVersions(a: String, b: String): Int {
    val left = a.split(".").map { it.toIntOrNull() ?: 0 }
    val right = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(left.size, right.size)) {
        val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
        if (difference != 0) return difference
    }
    return 0
}

// without a last seen version the update is from before What's new existed, so only this release is new to them
fun releasesToAnnounce(
    releases: List<ChangelogRelease>,
    lastSeenVersion: String?,
    currentVersion: String
): List<ChangelogRelease> = releases.filter { release ->
    if (lastSeenVersion == null) {
        release.version == currentVersion
    } else {
        compareVersions(release.version, lastSeenVersion) > 0 && compareVersions(release.version, currentVersion) <= 0
    }
}

// Play installs don't offer donations, so they also don't hear about the Ko-fi page
fun withoutDonationMentions(releases: List<ChangelogRelease>): List<ChangelogRelease> = releases.mapNotNull { release ->
    val sections = release.sections.mapNotNull { section ->
        val items = section.items.filterNot { it.mentionsKofi() }
        if (items.isEmpty()) null else section.copy(items = items)
    }
    if (sections.isEmpty()) null else release.copy(sections = sections)
}

private fun ChangelogItem.mentionsKofi(): Boolean =
    text.contains("ko-fi", ignoreCase = true) || children.any { it.mentionsKofi() }

// the changelog only uses **bold** and [links](url)
fun inlineMarkdown(text: String, linkStyles: TextLinkStyles? = null): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val linkMatch = if (text[index] == '[') Regex("""\[([^]]+)]\(([^)]+)\)""").matchAt(text, index) else null
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end == -1) {
                    append(text.substring(index))
                    index = text.length
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(index + 2, end)) }
                    index = end + 2
                }
            }
            linkMatch != null -> {
                withLink(LinkAnnotation.Url(linkMatch.groupValues[2], linkStyles)) { append(linkMatch.groupValues[1]) }
                index = linkMatch.range.last + 1
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
}
