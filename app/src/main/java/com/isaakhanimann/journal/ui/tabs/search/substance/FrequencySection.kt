/*
 * Copyright (c) 2026. Francisco Seixas.
 * This file is part of PsyJournal, a fork of PsychonautWiki Journal.
 *
 * PsyJournal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsyJournal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsyJournal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.search.substance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.frequency.FrequencySummary
import com.isaakhanimann.journal.ui.utils.getStringOfPattern
import com.isaakhanimann.journal.ui.utils.getTimeDifferenceText
import java.time.Duration
import java.time.Instant

@Composable
fun FrequencySection(summary: FrequencySummary, modifier: Modifier = Modifier) {
    val now = Instant.now()
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val lastSession = summary.lastSessionEnd
        Text(
            text = if (lastSession != null) {
                "Last session: ${getTimeDifferenceText(lastSession, now)} ago"
            } else {
                "No sessions logged recently"
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        summary.sessionsInWindow?.let {
            Text(text = "Sessions in the last 7 days: $it", style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = "Recommended gap: ${summary.recommendedGapText}", style = MaterialTheme.typography.bodyMedium)
        summary.okAgainAt?.takeIf { it.isAfter(now) }?.let {
            Text(text = "OK again from ${it.getStringOfPattern("EEE d MMM")}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = summary.reason, style = MaterialTheme.typography.bodySmall)
        if (summary.sourceUrl.isNotBlank()) {
            TextButton(onClick = { uriHandler.openUri(summary.sourceUrl) }) {
                Text("Source")
            }
        }
    }
}

@Preview
@Composable
fun FrequencySectionPreview() {
    val now = Instant.now()
    FrequencySection(
        FrequencySummary(
            lastSessionEnd = now.minus(Duration.ofDays(35)),
            recommendedGapText = "3 months",
            okAgainAt = now.plus(Duration.ofDays(55)),
            sessionsInWindow = null,
            reason = "Waiting at least 3 months between sessions is recommended.",
            sourceUrl = "https://example.org",
        )
    )
}
