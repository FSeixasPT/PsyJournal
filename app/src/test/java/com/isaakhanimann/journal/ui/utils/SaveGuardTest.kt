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

package com.isaakhanimann.journal.ui.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveGuardTest {
    @Test
    fun aSecondTapWhileSavingIsIgnored() = runBlocking {
        val guard = SaveGuard(this)
        val release = CompletableDeferred<Unit>()
        var saves = 0
        assertTrue(guard.save(action = { saves++; release.await() }))
        yield()
        assertFalse(guard.save(action = { saves++ }))
        release.complete(Unit)
        yield()
        assertEquals(1, saves)
    }

    @Test
    fun afterSavingTheGuardStaysClosedAndCallsOnSaved() = runBlocking {
        val guard = SaveGuard(this)
        var saved = false
        guard.save(action = {}, onSaved = { saved = true })
        yield()
        assertTrue(saved)
        assertTrue(guard.isSaving.value)
        assertFalse(guard.save(action = {}))
    }

    @Test
    fun aFailedSaveCanBeRetried() = runBlocking {
        val guard = SaveGuard(this)
        var saved = false
        guard.save(action = { throw IllegalStateException("disk full") }, onSaved = { saved = true })
        yield()
        assertFalse(saved)
        assertFalse(guard.isSaving.value)
        assertNotNull(guard.failure.value)
        assertTrue(guard.save(action = {}, onSaved = { saved = true }))
        yield()
        assertTrue(saved)
        assertNull(guard.failure.value)
    }

    @Test
    fun reopenAllowsTheNextSave() = runBlocking {
        val guard = SaveGuard(this)
        var saves = 0
        guard.save(action = { saves++ }, onSaved = { guard.reopen() })
        yield()
        assertTrue(guard.save(action = { saves++ }))
        yield()
        assertEquals(2, saves)
    }
}
