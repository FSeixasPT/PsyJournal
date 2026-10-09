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

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AfterSavingTest {
    @Test
    fun startedFromTheSameExperienceJustGoesBack() {
        assertNull(experienceToOpenAfterSaving(experienceBelowAddFlow = 7, savedExperience = 7))
    }

    @Test
    fun startedFromAnotherExperienceOpensTheSavedOne() {
        assertEquals(8, experienceToOpenAfterSaving(experienceBelowAddFlow = 7, savedExperience = 8))
    }

    @Test
    fun startedFromTheJournalOpensTheSavedOne() {
        assertEquals(8, experienceToOpenAfterSaving(experienceBelowAddFlow = null, savedExperience = 8))
    }
}
