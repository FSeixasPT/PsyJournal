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

package com.isaakhanimann.journal.ui.widget

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WidgetRefreshTest {
    private val next = Instant.parse("2026-01-01T20:05:00Z")
    // the model's content doesn't matter here, only the content's kind and refresh time
    private val line = DataForOneEffectLine(
        substanceName = "TestSubstance",
        route = AdministrationRoute.ORAL,
        roaDuration = null,
        height = 1f,
        horizontalWeight = 0.5f,
        color = AdaptiveColor.BLUE,
        startTime = Instant.parse("2026-01-01T20:00:00Z"),
        endTime = null,
    )
    private val active = WidgetContent.Active(
        experienceId = 1,
        title = "Experience",
        lines = emptyList(),
        model = AllTimelinesModel(listOf(line), emptyList(), emptyList(), false, false),
        nextRefreshAt = next
    )

    @Test
    fun anActiveExperienceRefreshesAtTheNextChange() {
        assertEquals(next, widgetRefreshAt(active, hasWidgets = true))
    }

    @Test
    fun nothingActiveSchedulesNoAlarm() {
        assertNull(widgetRefreshAt(WidgetContent.NothingActive, hasWidgets = true))
    }

    @Test
    fun noWidgetsSchedulesNoAlarm() {
        assertNull(widgetRefreshAt(active, hasWidgets = false))
    }
}
