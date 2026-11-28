package com.example

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesWithoutCrashOrSecureFlagBlackout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Folio Journal", context.getString(R.string.app_name))

        composeRule.waitForIdle()
        val flags = composeRule.activity.window.attributes.flags
        assertFalse(
            "FLAG_SECURE should not block display rendering on startup",
            (flags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
        )
    }
}
