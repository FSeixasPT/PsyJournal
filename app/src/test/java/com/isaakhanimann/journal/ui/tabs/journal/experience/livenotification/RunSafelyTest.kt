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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSafelyTest {
    @Test
    fun aFailureIsReportedInsteadOfThrown() = runBlocking {
        var failure: Throwable? = null
        runSafely(onFailure = { failure = it }) { throw IllegalStateException("database closed") }
        assertEquals("database closed", failure?.message)
    }

    @Test
    fun successDoesntReportAFailure() = runBlocking {
        var isReported = false
        var isRun = false
        runSafely(onFailure = { isReported = true }) { isRun = true }
        assertTrue(isRun)
        assertFalse(isReported)
    }

    @Test(expected = CancellationException::class)
    fun cancellationStillCancels() = runBlocking {
        runSafely(onFailure = {}) { throw CancellationException("cancelled") }
    }
}
