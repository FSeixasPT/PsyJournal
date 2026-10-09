/*
 * Copyright (c) 2022. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification

import android.app.NotificationManager
import android.content.Intent
import java.time.Duration
import java.time.Instant

// the notification's Log planned opens the quick-log dialog only right after the experience opens,
// so the dialog can't pop up later by surprise and invite logging the same dose twice
class QuickLogRequest(private var requestedAt: Instant?) {
    fun isPending(now: Instant): Boolean {
        val requestedAt = requestedAt ?: return false
        return now < requestedAt + validity
    }

    fun consume() {
        requestedAt = null
    }

    private companion object {
        val validity: Duration = Duration.ofSeconds(5)
    }
}

// reopening the app from recent apps repeats the intent that created it, which must not navigate again
fun isFreshNotificationLaunch(intentFlags: Int): Boolean =
    intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

// the user can block just the live timeline channel while notifications stay allowed;
// null means the channel doesn't exist yet and is created when posting
fun isChannelUsable(importance: Int?): Boolean = importance != NotificationManager.IMPORTANCE_NONE
