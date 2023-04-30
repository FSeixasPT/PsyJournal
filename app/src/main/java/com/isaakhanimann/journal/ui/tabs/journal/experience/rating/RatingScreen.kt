/*
 * Copyright (c) 2023. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.journal.experience.rating

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import com.isaakhanimann.journal.ui.theme.JournalTheme
import java.time.LocalDateTime


@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RatingScreenPreview() {
    JournalTheme {
        RatingScreen(
            title = "Edit Shulgin Rating",
            onDone = {},
            selectedTime = LocalDateTime.now(),
            onTimeChange = {},
            selectedRating = ShulginRatingOption.TWO_PLUS,
            onRatingChange = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    title: String,
    onDone: () -> Unit,
    selectedTime: LocalDateTime,
    onTimeChange: (LocalDateTime) -> Unit,
    selectedRating: ShulginRatingOption,
    onRatingChange: (ShulginRatingOption) -> Unit,
    onDelete: (() -> Unit)? = null
) {

}