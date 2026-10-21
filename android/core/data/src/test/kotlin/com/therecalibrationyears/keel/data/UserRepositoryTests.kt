package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.domain.Pathway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Port of the repository parts of KeelTests/ProfileTests.swift and
 * KeelTests/OnboardingStateTests.swift (the ownership re-stamp is iOS-only: plan D21).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserRepositoryTests {
    private lateinit var db: MainDatabase
    private val clock = TestClock()
    private lateinit var repo: UserRepository

    @Before fun setUp() {
        db = inMemoryDatabase()
        repo = userRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun upsertCreatesAProfileThenUpdatesTheSameOne() = runBlocking {
        val created = repo.upsertProfile("there", email = null, appleUserID = null)
        assertEquals("local-TEST", created.ownerID)
        assertEquals(created.id.uppercase(), created.id)
        assertEquals(clock.instant, created.trackingStartDate)
        assertEquals("en_AU", created.localeID) // device context stamped
        assertEquals("Android 16 (API 36)", created.osVersion)

        clock.advanceSeconds(60)
        val updated = repo.upsertProfile("Mischa", email = null, appleUserID = null)
        assertEquals(created.id, updated.id)
        assertEquals("Mischa", repo.currentProfile()?.firstName)
        assertEquals(clock.instant, updated.updatedAt)
        assertEquals(created.createdAt, updated.createdAt)
    }

    @Test fun upsertKeepsAnEmailItWasNotGiven() = runBlocking {
        repo.upsertProfile("Mischa", email = "m@example.com", appleUserID = null)
        repo.upsertProfile("Mischa", email = null, appleUserID = null)
        assertEquals("m@example.com", repo.currentProfile()?.email)
    }

    /** Port of testUpdateBasicInfoRoundTripsAndClears. */
    @Test fun updateBasicInfoRoundTripsAndClears() = runBlocking {
        repo.upsertProfile("there", email = null, appleUserID = null)

        repo.updateBasicInfo("Mischa", "Reed", 1977, "0400 000 000", "m@example.com")
        repo.currentProfile()!!.let {
            assertEquals("Mischa", it.firstName)
            assertEquals("Reed", it.lastName)
            assertEquals(1977, it.birthYear)
            assertEquals("0400 000 000", it.mobile)
            assertEquals("m@example.com", it.email)
        }

        // null clears the optional fields (the form is the source of truth).
        repo.updateBasicInfo("Mischa", null, null, null, null)
        repo.currentProfile()!!.let {
            assertNull(it.lastName); assertNull(it.birthYear); assertNull(it.mobile); assertNull(it.email)
        }

        // A blank first name is ignored, so she's never left unnamed.
        repo.updateBasicInfo("   ", null, null, null, null)
        assertEquals("Mischa", repo.currentProfile()?.firstName)
    }

    /** Port of testUpdateBasicInfoCreatesAProfileIfNoneExists. */
    @Test fun updateBasicInfoCreatesAProfileIfNoneExists() = runBlocking {
        assertNull(repo.currentProfile())
        repo.updateBasicInfo("Mischa", null, null, null, null)
        assertEquals("Mischa", repo.currentProfile()?.firstName)
    }

    @Test fun setPathwayStoresTheIosRawValue() = runBlocking {
        repo.upsertProfile("Mischa", null, null)
        repo.setPathway(Pathway.MEDICAL)
        assertEquals("medical", repo.currentProfile()?.pathwayRaw)
        assertEquals(Pathway.MEDICAL, repo.currentProfile()?.pathway)
    }

    @Test fun setPathwayWithoutAProfileDoesNothing() = runBlocking {
        repo.setPathway(Pathway.BOTH)
        assertNull(repo.currentProfile())
    }

    @Test fun setHealthConnectedCreatesAProfileRatherThanDropIt() = runBlocking {
        repo.setHealthConnected(true)
        assertEquals(true, repo.currentProfile()?.healthConnected)
        assertEquals(UserRepository.DEFAULT_FIRST_NAME, repo.currentProfile()?.firstName)
    }

    /** Port of OnboardingStateTests.testMarkingAgainKeepsTheFirstDate. */
    @Test fun markingAgainKeepsTheFirstDate() = runBlocking {
        val first = clock.instant
        repo.markOnboardingCompleted(first)
        repo.markOnboardingCompleted(first.plusSeconds(86_400))
        assertEquals(first, repo.currentProfile()?.onboardingCompletedAt)
    }

    /** Port of OnboardingStateTests.testMarkingWithNoProfileCreatesOne. */
    @Test fun markingWithNoProfileCreatesOne() = runBlocking {
        repo.markOnboardingCompleted()
        assertNotNull(repo.currentProfile()?.onboardingCompletedAt)
    }

    /** Port of OnboardingStateTests.testAProfileAloneIsNotOnboarded (quit midway). */
    @Test fun aProfileAloneIsNotOnboarded() = runBlocking {
        repo.upsertProfile("Mischa", null, null)
        assertNull(repo.currentProfile()?.onboardingCompletedAt)
    }

    /** Two quick taps on Continue must not create two profiles. */
    @Test fun concurrentUpsertsMakeOneProfile() = runBlocking {
        coroutineScope { (1..8).map { async { repo.upsertProfile("Mischa", null, null) } }.awaitAll() }
        val ids = (1..8).map { repo.currentProfile()!!.id }.toSet()
        assertEquals(1, ids.size)
        val rows = withContext(Dispatchers.IO) {
            db.query("SELECT COUNT(*) FROM user_profile", null).use { it.moveToFirst(); it.getInt(0) }
        }
        assertEquals(1, rows)
    }

    /** If a restore ever brought several profiles, the oldest live one is hers, every read. */
    @Test fun currentProfileIsTheOldestLiveOne() = runBlocking {
        val dao = db.userProfiles()
        val older = repo.upsertProfile("Older", null, null)
        dao.upsert(older.copy(id = "B", firstName = "Newer", createdAt = older.createdAt.plusSeconds(10)))
        dao.upsert(older.copy(id = "A", firstName = "Gone", createdAt = older.createdAt.minusSeconds(10), deletedAt = clock.instant))
        assertEquals("Older", repo.currentProfile()?.firstName)
        assertTrue(repo.currentProfile()?.deletedAt == null)
    }
}
