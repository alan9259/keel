package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.MainDatabase
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.GPAboutMe
import com.therecalibrationyears.keel.domain.GPMedCategory
import com.therecalibrationyears.keel.domain.GPPeriod
import com.therecalibrationyears.keel.domain.GPSummaryInputs
import com.therecalibrationyears.keel.domain.GPSummarySection
import com.therecalibrationyears.keel.domain.Hysterectomy
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.ScheduleWords
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private object PlainWords : ScheduleWords {
    override fun time(slot: DoseSlot) = if (slot.hasTime) "%d:%02d".format(slot.hour, slot.minute) else "Any time" // not-copy: test words
    override fun shortWeekday(weekday: Int) = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[weekday - 1] // not-copy: test words
    override val firstWeekday = 2
}

/** The service and its repositories on one in-memory store, "now" fixed at 14 Nov 2023 22:13 UTC (as iOS). */
private class GPStore {
    val clock = TestClock(Instant.ofEpochSecond(1_700_000_000))
    val db: MainDatabase = inMemoryDatabase()
    val checkIns = CheckInRepository(db.checkIns(), { "test-owner" }, clock)
    val cycle = CycleRepository(db.cycle(), { "test-owner" }, clock)
    val users = userRepository(db, clock)
    val medications = MedicationRepository(db.medications(), { "test-owner" }, clock)
    val service = GPSummaryService(checkIns, medications, cycle, users, clock) { PlainWords }
    val today: LocalDate = LocalDate.of(2023, 11, 14)

    fun d(offset: Int): LocalDate = today.plusDays(offset.toLong())
    fun at(offset: Int, hour: Int = 0): Instant = d(offset).atStartOfDay(ZoneOffset.UTC).plusHours(hour.toLong()).toInstant()

    /** "d MMMM yyyy" in English, as the PDF prints dates. */
    fun styled(offset: Int): String = GPSummaryService.DAY_MONTH_YEAR.format(d(offset))

    suspend fun symptom(name: String): SymptomEntity {
        val t = clock.now()
        return SymptomEntity(UUID.randomUUID().toString().uppercase(), name, "body", isCustom = name.first() == 'z', ownerID = "test-owner", createdAt = t, updatedAt = t)
            .also { db.symptoms().upsert(it) }
    }

    suspend fun med(
        name: String, dosage: String, timing: String = "", kind: String, group: String? = null, active: Boolean = true,
        started: Instant? = null, changed: Instant? = null, stopped: Instant? = null,
    ) = MedicationEntity(
        id = UUID.randomUUID().toString().uppercase(), name = name, dosage = dosage, timing = timing, kindRaw = kind,
        catalogGroupID = group, isActive = active, date = started, doseChangedAt = changed, stoppedAt = stopped,
        ownerID = "test-owner", createdAt = clock.now(), updatedAt = clock.now(),
    ).also { db.medications().upsert(it) }

    suspend fun seedFullPicture() {
        val sleep = symptom("Trouble sleeping")
        val flush = symptom("Hot flushes")
        checkIns.create(Mood.OKAY, 60, null, listOf(SymptomPick(sleep.id, 2), SymptomPick(flush.id, 3)), at(-2))
        checkIns.create(Mood.OKAY, 40, null, listOf(SymptomPick(flush.id, 2)), at(-5))
        checkIns.create(Mood.LOW, 80, null, listOf(SymptomPick(sleep.id, 1)), at(-10))

        for (k in listOf(20, 19, 18)) cycle.togglePeriodDay(d(-k)) // a 3-day run: one start
        cycle.setFlow(FlowLevel.SPOTTING, d(-10)) // standalone spotting

        med("Oestrogel", "2 pumps", "Morning", kind = "treatment", group = "oestrogen", started = at(-100), changed = at(-3))
        med("Magnesium", "400mg", "Night", kind = "supplement")

        users.updateBasicInfo(firstName = "Mischa", lastName = null, birthYear = 1977, mobile = null, email = null)
    }

    fun close() = db.close()
}

/**
 * Port of KeelTests/GPSummaryServiceTests.swift: given seeded check-ins, cycle entries and
 * treatments, the service produces the resolved document with the right counts,
 * denominators and her-words fields. The file-name test is with the renderer (feature
 * module); the Apple Health periods test arrives with Health Connect in M7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GPSummaryServiceTests {
    private lateinit var s: GPStore

    @Before fun setUp() { s = GPStore() }
    @After fun tearDown() = s.close()

    private suspend fun doc(inputs: GPSummaryInputs = GPSummaryInputs()) = s.service.makeDocument(inputs, s.clock.now())

    /**
     * Port of testImportedAppleHealthPeriodsAreNotInTheSummary: periods imported from Health
     * Connect never reach the GP Visit Summary, whose cycle block reads only the periods she
     * logged in Keel (the service can't even see the health store).
     */
    @Test fun importedHealthConnectPeriodsAreNotInTheSummary() = runBlocking {
        val health = com.therecalibrationyears.keel.data.db.HealthDatabase.inMemory(org.robolectric.RuntimeEnvironment.getApplication())
        try {
            HealthIngestor(health, s.db, { "test-owner" }, s.clock).ingest(
                com.therecalibrationyears.keel.domain.HealthSnapshot(menstrualFlow = listOf(-40, -39, -12, -11).associate { s.d(it) to FlowLevel.MEDIUM }),
            )
            val doc = doc(GPSummaryInputs(period = GPPeriod.TwelveWeeks))
            assertEquals(0, doc.cycle.periodsRecorded)
            assertNull(doc.cycle.lastPeriodStart)
            assertNull(doc.cycle.flow)
        } finally {
            health.close()
        }
    }

    @Test fun resolvesCountsSymptomsCycleAndMeds() = runBlocking {
        s.seedFullPicture()
        val doc = doc(GPSummaryInputs(period = GPPeriod.FourWeeks, includeName = true))

        // About + the flat check-in line (the denominator is the whole window).
        assertEquals("Mischa", doc.name)
        assertEquals("3 of 28 days", doc.checkInsLabel)
        assertEquals("18 October to 14 November 2023", doc.periodLabel)

        // Symptoms: tie on days (2 each) broken by higher mean severity (flush 2.5 > 1.5).
        val table = doc.symptomTable(doc.defaultSymptomMaxRows)
        assertEquals(listOf("Hot flushes", "Trouble sleeping"), table.rows.map { it.name })
        assertEquals("2 of 3 check-in days", table.rows.first().thisPeriod)
        assertFalse(table.showsPreviousColumn) // no earlier check-ins
        assertEquals(6, doc.defaultSymptomMaxRows)

        // Sleep, energy, mood.
        assertEquals("2 of 3", doc.sleepLine) // Trouble sleeping on 2 of 3 check-in days
        assertEquals("average 6.0 of 10, from 3 entries", doc.energyLine) // (6 + 4 + 8) / 3
        assertEquals("Okay 2, Low 1", doc.moodLine)

        // Cycle: one recorded start; standalone spotting reads as bleeding between periods.
        assertEquals(1, doc.cycle.periodsRecorded)
        assertEquals("not enough recorded periods to show a range", doc.cycle.cycleLengthRange)
        assertEquals("Yes", doc.cycle.intermenstrualBleeding)
        assertEquals("Period", doc.cycle.flow) // the most-recorded descriptor
        assertEquals(s.d(-20), doc.cycle.lastPeriodStart)

        // Treatment tables and the dated change.
        assertEquals(1, doc.mht.rows.size)
        assertEquals("2 pumps", doc.mht.rows.first().col2)
        assertEquals(s.styled(-3), doc.mht.rows.first().col3)
        assertEquals("Magnesium", doc.supplements.rows.first().col1)
        assertEquals("400mg", doc.supplements.rows.first().col2)
        assertEquals("Night", doc.supplements.rows.first().col3) // her timing, as she entered it
        assertTrue(doc.otherMeds.isEmpty)
        assertEquals(listOf("${s.styled(-3)}: changed Oestrogel dose"), doc.treatmentChanges)
        assertEquals(s.today, doc.generatedOn)
    }

    @Test fun autoSectionsIncludedByDefaultAndRemovable() = runBlocking {
        s.seedFullPicture()
        val inputs = GPSummaryInputs(period = GPPeriod.FourWeeks)

        // Default: every auto-populated section is in.
        val full = doc(inputs)
        assertTrue(full.includeCycle && full.includeSleep && full.includeEnergy && full.includeMood)

        // Removing each section turns off exactly its flag; the data is still computed.
        val trimmed = doc(inputs.copy(removedSections = setOf(GPSummarySection.CYCLE, GPSummarySection.SLEEP, GPSummarySection.ENERGY, GPSummarySection.MOOD)))
        assertFalse(trimmed.includeCycle)
        assertFalse(trimmed.includeSleep)
        assertFalse(trimmed.includeEnergy)
        assertFalse(trimmed.includeMood)

        // A single removal leaves the others in.
        val moodOff = doc(inputs.copy(removedSections = setOf(GPSummarySection.MOOD)))
        assertFalse(moodOff.includeMood)
        assertTrue(moodOff.includeCycle && moodOff.includeSleep && moodOff.includeEnergy)
    }

    @Test fun stoppedTreatmentsShowInMHTAndChangesButNotOtherTables() = runBlocking {
        s.seedFullPicture() // active Oestrogel (MHT) and Magnesium (supplement)
        // A stopped MHT and a stopped supplement, both dated inside the 4-week window.
        s.med("Old patch", "50mcg", kind = "treatment", group = "oestrogen", active = false, stopped = s.at(-10))
        s.med("Old magnesium", "300mg", kind = "supplement", active = false, stopped = s.at(-10))

        val doc = doc(GPSummaryInputs(period = GPPeriod.FourWeeks))

        // MHT lists the stopped treatment with "Stopped [date]".
        val stoppedRow = doc.mht.rows.first { it.col1 == "Old patch" }
        assertTrue("got ${stoppedRow.col3}", "Stopped ${s.styled(-10)}" in stoppedRow.col3)

        // A stopped supplement never becomes a supplement row (spec: MHT only).
        assertFalse(doc.supplements.rows.any { it.col1 == "Old magnesium" })

        // Both stops are restated in the dated changes list.
        assertTrue("${s.styled(-10)}: stopped Old patch" in doc.treatmentChanges)
        assertTrue("${s.styled(-10)}: stopped Old magnesium" in doc.treatmentChanges)
    }

    @Test fun periodsNotApplicableReasonFlowsIntoCycleBlock() = runBlocking {
        s.seedFullPicture() // creates a profile
        s.users.setPeriodsNotApplicableReason("  After a hysterectomy in 2019  ")
        assertEquals("After a hysterectomy in 2019", doc().cycle.notApplicable) // trimmed, her words
    }

    /** A check-in saved without an energy pick doesn't count toward the energy average. */
    @Test fun energyAverageSkipsCheckInsWithoutAnEnergyPick() = runBlocking {
        s.seedFullPicture()
        s.checkIns.create(Mood.OKAY, EnergyLevel.NOT_RECORDED, null, emptyList(), s.at(-3))
        assertEquals("average 6.0 of 10, from 3 entries", doc().energyLine) // unchanged by the skipped one
    }

    @Test fun stopOutsideWindowIsNotShown() = runBlocking {
        s.seedFullPicture()
        s.med("Ancient HRT", "1mg", kind = "treatment", group = "oestrogen", active = false, stopped = s.at(-400))
        val doc = doc(GPSummaryInputs(period = GPPeriod.FourWeeks))
        assertFalse(doc.mht.rows.any { it.col1 == "Ancient HRT" })
        assertFalse(doc.treatmentChanges.any { "Ancient HRT" in it })
    }

    @Test fun nameAndAgeOffByDefaultAndPrioritiesDropSymptomRow() = runBlocking {
        s.seedFullPicture()
        val doc = doc(
            GPSummaryInputs(
                period = GPPeriod.FourWeeks,
                priorities = listOf("Sleep help", "  ", "Mood", "Energy"), // blank dropped, capped at 3
                impactAreas = listOf("Sleep", "Work or concentration"),
                impactOverall = "Significant",
                questions = listOf("Do I need a blood test?"),
            ),
        )
        assertNull("name is off by default", doc.name)
        assertNull("age is off by default", doc.age)
        assertEquals(listOf("Sleep help", "Mood", "Energy"), doc.priorities)
        assertEquals("three priorities reduce the symptom rows", 5, doc.defaultSymptomMaxRows)
        assertEquals("Affecting: sleep, work or concentration. Overall impact: Significant.", doc.impactLine)
        assertEquals(listOf("Do I need a blood test?"), doc.questions)
    }

    @Test fun emptyStoreGivesHonestEmptyStates() = runBlocking {
        val doc = doc()
        assertEquals("0 of 84 days", doc.checkInsLabel) // 12-week default
        assertTrue(doc.symptomTable(6).isEmpty)
        assertEquals(0, doc.cycle.periodsRecorded)
        assertEquals("Not recorded", doc.cycle.intermenstrualBleeding)
        assertNull(doc.cycle.lastPeriodStart)
        assertEquals("0 of 0", doc.sleepLine)
        assertNull(doc.energyLine) // no check-ins, no entries
        assertNull(doc.moodLine)
        assertTrue(doc.mht.isEmpty && doc.otherMeds.isEmpty && doc.supplements.isEmpty)
        assertTrue(doc.treatmentChanges.isEmpty())
        assertNull(doc.impactLine)
    }

    // Android additions

    @Test fun ageAndOptInsComeFromHerProfile() = runBlocking {
        s.seedFullPicture()
        val doc = doc(GPSummaryInputs(includeName = true, includeAge = true))
        assertEquals(46, doc.age) // 2023 - 1977, as iOS counts it
        assertEquals(GPAboutMe(hasName = true, hasAge = true, hasHysterectomyAnswer = false), s.service.aboutMe())
        assertEquals(
            listOf("Oestrogel" to GPMedCategory.MHT, "Magnesium" to GPMedCategory.SUPPLEMENT),
            s.service.reviewMeds().map { it.name to it.category },
        )
    }

    /** "there" stands in for a name she left blank; it is never offered or printed (plan D26). */
    @Test fun thePlaceholderNameIsNeverOfferedOrPrinted() = runBlocking {
        s.users.upsertProfile(UserRepository.DEFAULT_FIRST_NAME, email = null, appleUserID = null)
        assertFalse(s.service.aboutMe().hasName)
        assertNull(doc(GPSummaryInputs(includeName = true)).name)
    }

    @Test fun thePreviousColumnCountsTheWindowJustBefore() = runBlocking {
        val flush = s.symptom("Hot flushes")
        // Four weeks: this window is days -27..0, the previous one -55..-28. Check in on
        // every day of the previous window (so the column shows) and flushes on two of them.
        for (k in 28..55) s.checkIns.create(Mood.OKAY, 60, null, if (k <= 29) listOf(SymptomPick(flush.id, 2)) else emptyList(), s.at(-k, hour = 9))
        s.checkIns.create(Mood.OKAY, 60, null, listOf(SymptomPick(flush.id, 2)), s.at(-1, hour = 9))
        val table = doc(GPSummaryInputs(period = GPPeriod.FourWeeks)).let { it.symptomTable(it.defaultSymptomMaxRows) }
        assertTrue(table.showsPreviousColumn)
        assertEquals("1 of 1 check-in days", table.rows.single().thisPeriod)
        assertEquals("2 of 28 check-in days", table.rows.single().previousPeriod)
    }

    @Test fun aScheduledMedicineReadsAsItsScheduleInHerWords() = runBlocking {
        val m = s.medications.add(TreatmentDraft(name = "Sertraline", kind = TreatmentKind.TREATMENT, legacyDosage = "50mg"))
        val row = doc().otherMeds.rows.single()
        assertEquals("Sertraline", row.col1)
        assertEquals(m.schedule(ZoneOffset.UTC).summary(PlainWords), row.col3)
    }
}

/**
 * Port of KeelTests/HysterectomyTests.swift, the recording and GP summary parts (the pure
 * parts are in core/domain). The backup tests arrive with Backup & Restore in M6.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HysterectomyTests {
    private lateinit var s: GPStore

    @Before fun setUp() {
        s = GPStore()
        runBlocking { s.users.upsertProfile("Mischa", email = null, appleUserID = null) }
    }

    @After fun tearDown() = s.close()

    @Test fun recordsTheAnswerAndKeepsAYearOnlyForYes() = runBlocking {
        s.users.setHysterectomy(Hysterectomy.YES_OVARIES_KEPT, 2015)
        assertEquals(Hysterectomy.YES_OVARIES_KEPT, s.users.currentProfile()?.hysterectomy)
        assertEquals(2015, s.users.currentProfile()?.hysterectomyYear)

        s.users.setHysterectomy(Hysterectomy.NO, 2015)
        assertEquals(Hysterectomy.NO, s.users.currentProfile()?.hysterectomy)
        assertNull(s.users.currentProfile()?.hysterectomyYear)

        s.users.setHysterectomy(null, null)
        assertNull(s.users.currentProfile()?.hysterectomy)
    }

    @Test fun offByDefaultOnTheSummary() = runBlocking {
        s.users.setHysterectomy(Hysterectomy.YES_ONE_OVARY_REMOVED, null)
        assertNull(s.service.makeDocument(GPSummaryInputs(), s.clock.now()).hysterectomyLine)
        assertFalse(GPSummaryInputs().includeHysterectomy)
    }

    @Test fun includedOnlyWhenSheTurnsItOn() = runBlocking {
        s.users.setHysterectomy(Hysterectomy.YES_ONE_OVARY_REMOVED, null)
        assertEquals(
            "yes, one ovary removed (as recorded by her)",
            s.service.makeDocument(GPSummaryInputs(includeHysterectomy = true), s.clock.now()).hysterectomyLine,
        )
    }

    @Test fun ratherNotSayIsNeverOfferedOrPrinted() = runBlocking {
        s.users.setHysterectomy(Hysterectomy.RATHER_NOT_SAY, null)
        assertFalse(s.service.aboutMe().hasHysterectomyAnswer)
        assertNull(s.service.makeDocument(GPSummaryInputs(includeHysterectomy = true), s.clock.now()).hysterectomyLine)

        s.users.setHysterectomy(Hysterectomy.NO, null)
        assertTrue(s.service.aboutMe().hasHysterectomyAnswer)
    }
}

/** Port of KeelTests/AlcoholTests.swift, the GP Visit Summary part (recording is in CheckInDataTests). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlcoholSummaryTests {
    private lateinit var s: GPStore

    @Before fun setUp() { s = GPStore() }
    @After fun tearDown() = s.close()

    private suspend fun entry(day: Int, alcohol: Int? = null, hour: Int = 9) {
        val c = s.checkIns.create(Mood.OKAY, 50, null, emptyList(), s.at(day, hour))
        if (alcohol != null) s.checkIns.setAlcohol(c.id, alcohol)
    }

    private suspend fun doc(inputs: GPSummaryInputs = GPSummaryInputs(period = GPPeriod.FourWeeks)) = s.service.makeDocument(inputs, s.clock.now())

    @Test fun onlyDaysWithASavedNumberCountAndBlankIsNeverZero() = runBlocking {
        entry(-1, alcohol = 2)
        entry(-2, alcohol = 0) // zero counts as recorded
        entry(-3) // blank: a check-in day, but not alcohol-free
        entry(-4)
        entry(-5, alcohol = 1, hour = 8) // two entries on one day count once
        entry(-5, alcohol = null, hour = 20)
        val doc = doc()
        assertEquals(5, doc.checkInDaysThisPeriod)
        assertEquals("recorded on 3 of 5 check-in days", doc.alcoholLine)
    }

    @Test fun noLineWhenSheNeverRecordedAlcohol() = runBlocking {
        entry(-1); entry(-2)
        assertNull(doc().alcoholLine)
    }

    @Test fun sheCanLeaveTheAlcoholRowOff() = runBlocking {
        entry(-1, alcohol = 1)
        assertTrue(doc().includeAlcohol)
        assertFalse(doc(GPSummaryInputs(period = GPPeriod.FourWeeks, removedSections = setOf(GPSummarySection.ALCOHOL))).includeAlcohol)
    }
}
