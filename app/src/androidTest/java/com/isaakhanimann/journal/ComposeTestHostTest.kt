package com.isaakhanimann.journal

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// the screens under test are hosted in a plain ComponentActivity, which otherwise gets the splash theme
// and its window action bar: the action bar's height drops out of the insets while the keyboard comes up,
// so the screen jumps and clicks in that moment miss
@RunWith(AndroidJUnit4::class)
class ComposeTestHostTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theTestHostHasNoWindowActionBar() {
        rule.setContent { Text("host") }
        var actionBar: Any? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            actionBar = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first().actionBar
        }
        assertNull(actionBar)
    }
}
