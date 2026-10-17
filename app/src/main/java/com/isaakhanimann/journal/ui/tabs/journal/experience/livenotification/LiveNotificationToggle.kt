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

package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// shared by the header button and the blocked message below the chart
class LiveNotificationToggleState internal constructor(
    private val context: Context,
    private val setLive: (Boolean) -> Unit,
    private val blocked: MutableState<Boolean>,
    private val requestPermission: () -> Unit,
) {
    val isBlocked: Boolean get() = blocked.value

    fun recheck() {
        if (blocked.value && areLiveNotificationsAllowed(context)) blocked.value = false
    }

    fun toggle(isLive: Boolean) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            isLive -> setLive(false)
            needsPermission -> requestPermission()
            !areLiveNotificationsAllowed(context) -> blocked.value = true
            else -> {
                blocked.value = false
                setLive(true)
            }
        }
    }
}

@Composable
fun rememberLiveNotificationToggleState(setLive: (Boolean) -> Unit): LiveNotificationToggleState {
    val context = LocalContext.current
    val blocked = remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) setLive(true) else blocked.value = true
    }
    return remember(context, setLive, permissionLauncher) {
        LiveNotificationToggleState(context, setLive, blocked) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun LiveNotificationButton(isLive: Boolean, state: LiveNotificationToggleState) {
    TextButton(
        onClick = { state.toggle(isLive) },
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = CompactTextButtonPadding,
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = if (isLive) "On" else "Off"
        }
    ) {
        // on shows in the accent colour; a check mark wouldn't leave room for the header's title on common phones
        Text(text = "Live notification")
    }
}

@Composable
fun LiveNotificationBlockedMessage(state: LiveNotificationToggleState) {
    val context = LocalContext.current
    // coming back from Android's settings after allowing notifications clears the message
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.recheck()
    }
    if (!state.isBlocked) return
    Column {
        Text(
            text = "Notifications are blocked for this app, so the live notification can't be shown.",
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }) {
            Text("Open notification settings")
        }
    }
}

// notifications in general and the live timeline category in particular, which can be blocked on its own
private fun areLiveNotificationsAllowed(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    return manager.areNotificationsEnabled() &&
            isChannelUsable(manager.getNotificationChannelCompat(LiveNotificationNotifier.CHANNEL_ID)?.importance)
}

// narrower than a text button's default, so the timeline header fits on one line on common phones
val CompactTextButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
