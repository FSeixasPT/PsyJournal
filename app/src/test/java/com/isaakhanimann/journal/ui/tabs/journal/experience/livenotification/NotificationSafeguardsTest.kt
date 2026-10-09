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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationSafeguardsTest {
    private val openedAt = Instant.parse("2026-01-01T20:00:00Z")

    @Test
    fun aQuickLogRequestOnlyAppliesRightAfterOpening() {
        val request = QuickLogRequest(requestedAt = openedAt)
        assertTrue(request.isPending(openedAt.plusSeconds(2)))
        assertFalse(request.isPending(openedAt.plusSeconds(6)))
        assertFalse(QuickLogRequest(requestedAt = null).isPending(openedAt))
    }

    @Test
    fun aConsumedRequestIsNoLongerPending() {
        val request = QuickLogRequest(requestedAt = openedAt)
        request.consume()
        assertFalse(request.isPending(openedAt.plusSeconds(1)))
    }

    @Test
    fun reopeningFromRecentAppsDoesntRepeatTheNotificationsTarget() {
        assertTrue(isFreshNotificationLaunch(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        assertFalse(isFreshNotificationLaunch(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    }

    @Test
    fun aBlockedChannelCantShowTheNotification() {
        assertTrue(isChannelUsable(null))
        assertTrue(isChannelUsable(NotificationManager.IMPORTANCE_LOW))
        assertFalse(isChannelUsable(NotificationManager.IMPORTANCE_NONE))
    }
}
