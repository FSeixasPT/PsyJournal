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
package com.isaakhanimann.journal.ui.tabs.settings

import android.content.Context
import android.os.Build

const val DONATE_URL = "https://ko-fi.com/psyjournal"

private const val PLAY_STORE_INSTALLER = "com.android.vending"

// Play doesn't allow linking to outside payments for tips, so its installs don't show the donate button
fun isDonationOffered(installer: String?): Boolean = installer != PLAY_STORE_INSTALLER

fun isDonationOffered(context: Context): Boolean {
    val installer = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (e: Exception) {
        null
    }
    return isDonationOffered(installer)
}
