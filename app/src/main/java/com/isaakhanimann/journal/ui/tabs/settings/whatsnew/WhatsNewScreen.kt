package com.isaakhanimann.journal.ui.tabs.settings.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.isaakhanimann.journal.ui.tabs.search.substance.SectionWithTitle
import com.isaakhanimann.journal.ui.theme.horizontalPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class WhatsNewViewModel @Inject constructor(changelogRepository: ChangelogRepository) : ViewModel() {
    val releasesFlow = flow { emit(changelogRepository.releases) }.flowOn(Dispatchers.IO)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(viewModel: WhatsNewViewModel = hiltViewModel()) {
    val releases by viewModel.releasesFlow.collectAsState(initial = emptyList())
    Scaffold(
        topBar = { TopAppBar(title = { Text("What's new") }) }
    ) { padding ->
        WhatsNewList(releases = releases, contentPadding = padding)
    }
}

@Composable
fun WhatsNewList(releases: List<ChangelogRelease>, contentPadding: PaddingValues) {
    LazyColumn(contentPadding = contentPadding) {
        item { Spacer(modifier = Modifier.height(5.dp)) }
        items(releases, key = { it.version }) { release ->
            SectionWithTitle(title = release.title()) {
                Column(
                    modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    release.sections.forEach { section ->
                        SectionHeading(section.title)
                        section.items.forEach { BulletItem(it) }
                    }
                }
            }
        }
        item {
            Text(
                text = "Changes up to 11.11 are in the original PsychonautWiki Journal, which PsyJournal is a fork of.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 10.dp)
            )
        }
    }
}

private fun ChangelogRelease.title(): String {
    val formattedDate = date?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it) }
    return if (formattedDate == null) version else "$version · $formattedDate"
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun BulletItem(item: ChangelogItem) {
    val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Row {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(inlineMarkdown(item.text, linkStyles), style = MaterialTheme.typography.bodyMedium)
            item.children.forEach { BulletItem(it) }
        }
    }
}

// shown once after an update: only the lead of each change, the screen in Settings has the rest
@Composable
fun WhatsNewDialog(
    releases: List<ChangelogRelease>,
    onDismiss: () -> Unit,
    onShowDetails: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(releases.singleOrNull()?.let { "What's new in ${it.version}" } ?: "What's new") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                releases.forEach { release ->
                    if (releases.size > 1) {
                        Text(release.version, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                    release.sections.forEach { section ->
                        SectionHeading(section.title)
                        section.items.forEach { item ->
                            Row {
                                Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                Text(inlineMarkdown(item.summary()), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onShowDetails) { Text("Details") }
        }
    )
}
