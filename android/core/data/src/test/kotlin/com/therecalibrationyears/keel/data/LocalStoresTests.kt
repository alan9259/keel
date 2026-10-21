package com.therecalibrationyears.keel.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The owner id and settings: same keys and defaults as iOS `AuthService` / `SettingsStore`. */
class LocalStoresTests {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun ownerIdIsEmptyUntilOnboardingThenStable() = runBlocking {
        val identity = LocalIdentity(tempDataStore(tmp.root))
        assertEquals("", identity.ownerId())
        val first = identity.continueLocally()
        assertTrue(first.matches(Regex("local-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}")))
        assertEquals(first, identity.continueLocally()) // reused, never replaced
        assertEquals(first, identity.ownerId())
    }

    @Test fun settingsDefaultsMatchIos() = runBlocking {
        val settings = SettingsStore(tempDataStore(tmp.root))
        assertNull(settings.colourModeRaw.first())   // follows the system
        assertNull(settings.themeId.first())         // default theme
        assertTrue(settings.haptics.first())
        assertFalse(settings.notificationExplainerPending.first())
    }

    @Test fun settingsPersistAcrossInstancesOfTheSameFile() = runBlocking {
        val store = tempDataStore(tmp.root)
        SettingsStore(store).setNotificationExplainerPending(true)
        SettingsStore(store).setThemeId("slate")
        assertTrue(SettingsStore(store).notificationExplainerPending.first())
        assertEquals("slate", SettingsStore(store).themeId.first())
    }
}
