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

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRating
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RatingsOnTimelineTest {
    private fun rating(time: String?, option: ShulginRatingOption, isPartOfTimeline: Boolean = true) = ShulginRating(
        time = time?.let { Instant.parse(it) },
        option = option,
        experienceId = 1,
        isPartOfTimeline = isPartOfTimeline
    )

    @Test
    fun ratingsWithATimeAreShown() {
        val shown = ratingsOnTimeline(listOf(rating("2026-01-01T20:00:00Z", ShulginRatingOption.TWO_PLUS)))
        assertEquals(listOf(DataForOneRating(Instant.parse("2026-01-01T20:00:00Z"), ShulginRatingOption.TWO_PLUS)), shown)
    }

    @Test
    fun theOverallRatingHasNoTimeAndIsLeftOut() {
        assertEquals(emptyList<DataForOneRating>(), ratingsOnTimeline(listOf(rating(null, ShulginRatingOption.FOUR_PLUS))))
    }

    @Test
    fun hiddenRatingsAreLeftOut() {
        val shown = ratingsOnTimeline(
            listOf(
                rating("2026-01-01T20:00:00Z", ShulginRatingOption.PLUS),
                rating("2026-01-01T21:00:00Z", ShulginRatingOption.THREE_PLUS, isPartOfTimeline = false),
            )
        )
        assertEquals(listOf(ShulginRatingOption.PLUS), shown.map { it.option })
    }
}
