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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// lets a save run only once, so repeated taps on done don't create duplicates;
// after a failed save the entries stay on screen and the save can be retried
class SaveGuard(private val scope: CoroutineScope) {
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _failure = MutableStateFlow<Throwable?>(null)
    val failure: StateFlow<Throwable?> = _failure.asStateFlow()

    // returns false when the tap was ignored because a save already started
    fun save(action: suspend () -> Unit, onSaved: () -> Unit = {}): Boolean {
        if (!_isSaving.compareAndSet(expect = false, update = true)) return false
        _failure.value = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failure.value = e
                _isSaving.value = false
                return@launch
            }
            // stays closed, the screen is about to close
            onSaved()
        }
        return true
    }

    // for actions that can be repeated on the same screen, like logging a planned ingestion
    fun reopen() {
        _isSaving.value = false
    }

    fun dismissFailure() {
        _failure.value = null
    }
}
