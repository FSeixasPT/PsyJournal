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

package com.isaakhanimann.journal.ui.supplements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.ui.tabs.search.substance.rememberNotificationGate

// shown after logging MDMA without a routine running
@Composable
fun SupplementRoutineOffer(onStart: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var isBlocked by remember { mutableStateOf(false) }
    val gate = rememberNotificationGate(onBlocked = { isBlocked = true })
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Start RollSafe's after-dose reminders?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hourly supplement and water reminders until 7 h after your dose. You can change the water amount on the MDMA page.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (isBlocked) {
                Text("Allow notifications for PsyJournal to get the reminders.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { gate(onStart) }) { Text("Start") }
                TextButton(onClick = onDismiss) { Text("No thanks") }
            }
        }
    }
}
