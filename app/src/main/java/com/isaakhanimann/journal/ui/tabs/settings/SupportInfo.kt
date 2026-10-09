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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat

// what to paste into a bug report; nothing personal and nothing is sent anywhere
fun supportInfoText(
    versionName: String,
    versionCode: Long,
    isDebug: Boolean,
    androidRelease: String,
    sdkInt: Int,
    manufacturer: String,
    model: String,
): String {
    val maker = manufacturer.replaceFirstChar { it.uppercase() }
    val withoutMaker = model.removePrefix(manufacturer).trim()
    val build = if (isDebug) "debug" else "release"
    return "PsyJournal $versionName ($versionCode), $build\n" +
            "Android $androidRelease (API $sdkInt)\n" +
            "Device: $maker $withoutMaker"
}

fun copySupportInfo(context: Context) {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val text = supportInfoText(
        versionName = packageInfo.versionName ?: "?",
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        isDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
    )
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Support info", text))
    // Android 13 and later confirm copies themselves
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Support info copied", Toast.LENGTH_SHORT).show()
    }
}
