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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AutoStartTest {
    private val now = Instant.parse("2026-01-01T20:00:00Z")

    @Test
    fun ingestionsAroundNowStartIt() {
        assertTrue(isRecentForAutoStart(now, now))
        assertTrue(isRecentForAutoStart(now.minusSeconds(60 * 60), now))
        assertTrue(isRecentForAutoStart(now.plusSeconds(5 * 60), now))
    }

    @Test
    fun olderOrLaterIngestionsDont() {
        assertFalse(isRecentForAutoStart(now.minusSeconds(60 * 60 + 1), now))
        assertFalse(isRecentForAutoStart(now.plusSeconds(5 * 60 + 1), now))
    }
}
