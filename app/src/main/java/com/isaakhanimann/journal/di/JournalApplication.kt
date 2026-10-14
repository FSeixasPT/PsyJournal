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

package com.isaakhanimann.journal.di

import com.isaakhanimann.journal.ui.supplements.SupplementRoutineController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.app.Application
import com.isaakhanimann.journal.ui.tabs.journal.experience.livenotification.LiveNotificationController
import com.isaakhanimann.journal.ui.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JournalApplication : Application() {
    @Inject
    lateinit var liveNotificationController: LiveNotificationController

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var supplementRoutineController: SupplementRoutineController

    override fun onCreate() {
        super.onCreate()
        liveNotificationController.observe()
        widgetUpdater.observe()
        // re-arms a running supplement routine whose alarm a battery saver may have removed
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { supplementRoutineController.resync() }
    }
}
