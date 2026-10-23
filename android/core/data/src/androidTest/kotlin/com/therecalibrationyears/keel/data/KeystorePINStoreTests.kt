package com.therecalibrationyears.keel.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.therecalibrationyears.keel.domain.PINHasher
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The PIN store on a real Android Keystore (Robolectric has none). */
@RunWith(AndroidJUnit4::class)
class KeystorePINStoreTests {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.filesDir, "keystore-pin-test-${System.nanoTime()}.preferences_pb")
    private val prefs = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { file }
    private val store = KeystorePINStore(prefs)

    @After fun tearDown() = runBlocking { store.clear(); file.delete(); Unit }

    @Test fun savedPinComesBackAndIsNotStoredInTheClear() = runBlocking {
        val salt = PINHasher.makeSalt()
        val hash = PINHasher.hash("2468", salt, rounds = 10)
        assertFalse(store.hasPIN())
        store.save(salt, hash)
        assertTrue(store.hasPIN())
        val (s, h) = store.load()!!
        assertArrayEquals(salt, s)
        assertArrayEquals(hash, h)
        // What's on disk is the encrypted blob, not the salt or hash.
        val raw = file.readBytes()
        assertFalse(raw.containsSlice(hash))
        assertFalse(raw.containsSlice(salt))
    }

    @Test fun clearForgetsItAndRemovesTheKey() = runBlocking {
        store.save(PINHasher.makeSalt(), ByteArray(32) { 7 })
        store.clear()
        assertNull(store.load())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias("keel.pin.key"))
    }

    /** A blob whose key is gone (e.g. restored onto another phone) reads as no PIN: the lock can't trap her. */
    @Test fun aBlobWithoutItsKeyReadsAsNoPin() = runBlocking {
        store.save(PINHasher.makeSalt(), ByteArray(32) { 1 })
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("keel.pin.key")
        assertTrue(prefs.data.first().asMap().isNotEmpty())
        assertNull(store.load())
        assertFalse(store.hasPIN())
    }

    private fun ByteArray.containsSlice(part: ByteArray): Boolean =
        (0..size - part.size).any { i -> part.indices.all { this[i + it] == part[it] } }
}
