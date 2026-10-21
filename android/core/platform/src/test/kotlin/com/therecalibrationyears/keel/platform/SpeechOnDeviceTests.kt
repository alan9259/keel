package com.therecalibrationyears.keel.platform

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of KeelTests/SpeechOnDeviceTests.swift: dictation only runs when it can stay on the
 * phone, so the voice audio never leaves it.
 */
class SpeechOnDeviceTests {
    @Test fun dictatesOnlyWhenOnDeviceRecognitionIsThere() {
        assertTrue(SpeechGate.canDictateOnDevice(sdk = 31, onDeviceAvailable = true, languageInstalled = null))
        assertTrue(SpeechGate.canDictateOnDevice(sdk = 36, onDeviceAvailable = true, languageInstalled = true))
    }

    /** Would otherwise fall back to a server: refused instead. */
    @Test fun noDictationWhenOnDeviceIsUnavailable() =
        assertFalse(SpeechGate.canDictateOnDevice(sdk = 36, onDeviceAvailable = false, languageInstalled = true))

    /** Android 9 to 11 have no on-device recogniser API: voice is simply unavailable. */
    @Test fun noDictationBeforeAndroid12() =
        assertFalse(SpeechGate.canDictateOnDevice(sdk = 30, onDeviceAvailable = true, languageInstalled = true))

    /** From Android 13 her language must already be on the phone; Keel never downloads it. */
    @Test fun noDictationWhenHerLanguageIsntInstalled() {
        assertFalse(SpeechGate.canDictateOnDevice(sdk = 33, onDeviceAvailable = true, languageInstalled = false))
        assertFalse(SpeechGate.canDictateOnDevice(sdk = 33, onDeviceAvailable = true, languageInstalled = null))
    }

    @Test fun herLanguageMatchesByTagThenByLanguage() {
        val au = Locale.forLanguageTag("en-AU")
        assertEquals("en-AU", SpeechGate.matchingLanguage(listOf("en-US", "en-AU"), au))
        assertEquals("en-US", SpeechGate.matchingLanguage(listOf("fr-FR", "en-US"), au))
        assertNull(SpeechGate.matchingLanguage(listOf("fr-FR"), au))
    }

    @Test fun messagesOnlyWhenVoiceCantRun() {
        assertEquals("Voice notes aren't available on this phone. You can type instead.", SpeechGate.unavailableMessage(SpeechState.UNAVAILABLE))
        assertNull(SpeechGate.unavailableMessage(SpeechState.IDLE))
        assertNull(SpeechGate.unavailableMessage(SpeechState.RECORDING))
    }
}
