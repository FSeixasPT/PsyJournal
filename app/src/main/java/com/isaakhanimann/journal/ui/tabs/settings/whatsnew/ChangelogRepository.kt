package com.isaakhanimann.journal.ui.tabs.settings.whatsnew

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChangelogRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    // read on first use, off the main thread by the callers
    val releases: List<ChangelogRelease> by lazy {
        parseChangelog(appContext.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() })
    }
}
