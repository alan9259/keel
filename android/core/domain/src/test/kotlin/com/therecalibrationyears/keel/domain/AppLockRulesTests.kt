package com.therecalibrationyears.keel.domain

import java.security.MessageDigest
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of KeelTests/PINHasherTests.swift. */
class PINHasherTests {
    private val salt = ByteArray(16) { it.toByte() }

    @Test fun samePINAndSaltHashEqual() =
        assertArrayEquals(PINHasher.hash("1234", salt, rounds = 10), PINHasher.hash("1234", salt, rounds = 10))

    @Test fun differentPINDiffers() =
        assertFalse(PINHasher.hash("1234", salt, rounds = 10).contentEquals(PINHasher.hash("1235", salt, rounds = 10)))

    @Test fun differentSaltDiffersForSamePIN() =
        assertFalse(PINHasher.hash("1234", salt, rounds = 10).contentEquals(PINHasher.hash("1234", ByteArray(16), rounds = 10)))

    @Test fun hashIsNotThePlaintext() {
        val h = PINHasher.hash("1234", salt)
        assertEquals(32, h.size)
        assertFalse(h.contentEquals("1234".toByteArray()))
    }

    @Test fun makeSaltIsRandom16Bytes() {
        val a = PINHasher.makeSalt()
        assertEquals(16, a.size)
        assertFalse(a.contentEquals(PINHasher.makeSalt()))
    }

    @Test fun constantTimeEquals() {
        assertTrue(PINHasher.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(PINHasher.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(PINHasher.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
    }

    /** The same construction as iOS: SHA-256 over salt + PIN bytes, then over each digest. */
    @Test fun matchesTheIosConstruction() {
        val sha = MessageDigest.getInstance("SHA-256")
        val once = sha.digest(salt + "1234".toByteArray())
        assertArrayEquals(sha.digest(once), PINHasher.hash("1234", salt, rounds = 2))
        assertArrayEquals(once, PINHasher.hash("1234", salt, rounds = 0)) // at least one round, as iOS
    }
}

/** The pure parts of KeelTests/AppLockServiceTests.swift. */
class AppLockRulesTests {
    private val now = Instant.parse("2026-10-23T10:00:00Z")

    @Test fun shouldLockOnlyAfterGraceElapses() {
        assertFalse(AppLockRules.shouldLock(now, null))
        assertFalse(AppLockRules.shouldLock(now, now.minusSeconds(5)))
        assertTrue(AppLockRules.shouldLock(now, now.minusSeconds(31)))
        assertTrue(AppLockRules.shouldLock(now, now.minusSeconds(30))) // the boundary locks, as iOS (>=)
    }

    @Test fun aLockoutStartsOnEveryFifthFailure() {
        (1..4).forEach { assertNull(AppLockRules.lockoutAfterFailure(it, now)) }
        assertEquals(now.plusSeconds(60), AppLockRules.lockoutAfterFailure(5, now))
        assertNull(AppLockRules.lockoutAfterFailure(6, now))
        assertEquals(now.plusSeconds(60), AppLockRules.lockoutAfterFailure(10, now))
    }

    @Test fun secondsLeftRoundUp() {
        assertEquals(60, AppLockRules.secondsLeft(now.plusSeconds(60), now))
        assertEquals(1, AppLockRules.secondsLeft(now.plusMillis(200), now))
        assertEquals(0, AppLockRules.secondsLeft(now, now))
        assertEquals(0, AppLockRules.secondsLeft(null, now))
    }

    @Test fun onlyFourDigitsMakeAPIN() {
        assertTrue(AppLockRules.isValidPIN("0042"))
        listOf("123", "12345", "12a4", "", "١٢٣٤").forEach { assertFalse(it, AppLockRules.isValidPIN(it)) }
    }
}
