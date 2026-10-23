package com.therecalibrationyears.keel.designsystem

import android.provider.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Android-only (iOS reads Reduce Motion from SwiftUI's environment): "Remove animations" sets
 * the animator duration scale to 0, and only then does Keel stop its repeating pulse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeelMotionTests {
    private val resolver = RuntimeEnvironment.getApplication().contentResolver

    @Test
    fun animationsAreOnByDefault() {
        assertFalse(animationsOff(resolver))
    }

    @Test
    fun removeAnimationsTurnsThemOff() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(animationsOff(resolver))
    }

    @Test
    fun aSlowerOrFasterScaleStillAnimates() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)
        assertFalse(animationsOff(resolver))
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 10f)
        assertFalse(animationsOff(resolver))
    }
}
