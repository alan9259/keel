package com.therecalibrationyears.keel.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of KeelTests/GPSummaryTests.swift: the GP Visit Summary's deterministic core (the
 * symptom section's ranking, denominators, previous-period rule and overflow; the cycle,
 * sleep, energy and mood lines; the medicine tables) and the banned-verb guard.
 */
class GPSummaryTests {
    private val base = LocalDate.of(2020, 9, 13)
    private fun d(offset: Int) = base.plusDays(offset.toLong())
    private fun at(offset: Int): Instant = d(offset).atStartOfDay(ZoneOffset.UTC).toInstant()

    private fun stat(name: String, days: Int, prev: Int = 0, sev: Double = 2.0, last: Int = 0, custom: Boolean = false) =
        GPSymptomStat(name, custom, days, prev, sev, at(last))

    // Symptom section

    @Test fun ranksByDaysThenSeverityThenRecency() {
        val stats = listOf(
            stat("A", 5, sev = 1.0, last = 1),
            stat("B", 10, sev = 1.0, last = 1), // most days
            stat("C", 5, sev = 3.0, last = 1), // ties A on days, higher severity
            stat("D", 5, sev = 1.0, last = 5), // ties A on days and severity, more recent
        )
        val table = GPSummaryBuilder.symptomTable(stats, checkInDaysThisPeriod = 20, checkInDaysPreviousPeriod = 0, previousWindowDayCount = 0)
        assertEquals(listOf("B", "C", "D", "A"), table.rows.map { it.name })
    }

    @Test fun denominatorsAreAlwaysShown() {
        val table = GPSummaryBuilder.symptomTable(listOf(stat("Trouble sleeping", 31, prev = 20)), 64, 48, 60)
        assertEquals("31 of 64 check-in days", table.rows.first().thisPeriod)
        assertEquals("20 of 48 check-in days", table.rows.first().previousPeriod)
    }

    @Test fun previousColumnHiddenWhenEarlierWindowUnderHalf() {
        // 20 of 60 earlier days is 33%, under the 50% bar.
        val table = GPSummaryBuilder.symptomTable(listOf(stat("A", 3, prev = 2)), 30, 20, 60)
        assertFalse(table.showsPreviousColumn)
        assertNull(table.rows.first().previousPeriod)
        assertEquals("Not enough earlier records to compare.", table.previousUnavailableNote)
    }

    @Test fun previousColumnShownAtExactlyHalf() {
        val table = GPSummaryBuilder.symptomTable(listOf(stat("A", 3, prev = 2)), 30, 30, 60)
        assertTrue(table.showsPreviousColumn)
        assertNull(table.previousUnavailableNote)
    }

    @Test fun topSixWithAlsoRecordedAlphabetical() {
        val stats = listOf(
            stat("Zed", 20), stat("Yan", 19), stat("Xer", 18),
            stat("Wme", 17), stat("Vim", 16), stat("Uno", 15),
            stat("Bee", 4), stat("Ant", 3), // outside the top 6
            stat("Low", 2), // low-frequency, still carried
        )
        val table = GPSummaryBuilder.symptomTable(stats, 30, 0, 0)
        assertEquals(6, table.rows.size)
        // Every kept symptom outside the top rows carries through, alphabetically.
        assertEquals("Also recorded: Ant, Bee, Low.", table.alsoRecorded)
    }

    @Test fun reducingRowsBumpsTheSixthIntoAlsoRecorded() {
        val stats = (1..6).map { stat("S$it", 20 - it) } // S1..S6, decreasing
        val table = GPSummaryBuilder.symptomTable(stats, 30, 0, 0, maxRows = 5)
        assertEquals(5, table.rows.size)
        assertEquals("Also recorded: S6.", table.alsoRecorded)
    }

    @Test fun emptyStateWhenNothingRecorded() {
        val table = GPSummaryBuilder.symptomTable(listOf(stat("A", 0)), 10, 0, 0)
        assertTrue(table.isEmpty)
        assertNull(table.alsoRecorded)
    }

    // Cycle block

    @Test fun cycleLengthRangeNeedsThreeStarts() {
        val two = GPSummaryBuilder.cycleBlock(listOf(d(0), d(28)), d(28), "Medium", GPBleeding.NO, null)
        assertEquals(2, two.periodsRecorded)
        assertEquals("not enough recorded periods to show a range", two.cycleLengthRange)

        val three = GPSummaryBuilder.cycleBlock(listOf(d(0), d(28), d(62)), d(62), "Medium", GPBleeding.YES, null)
        assertEquals(3, three.periodsRecorded)
        assertEquals("28 to 34 days", three.cycleLengthRange) // gaps 28 and 34
        assertEquals("Yes", three.intermenstrualBleeding)
        assertEquals("Medium", three.flow)
    }

    @Test fun cycleLengthEqualGapsReadAsSingleNumber() {
        val block = GPSummaryBuilder.cycleBlock(listOf(d(0), d(28), d(56)), d(56), null, GPBleeding.NOT_RECORDED, "   ")
        assertEquals("28 days", block.cycleLengthRange)
        assertNull("whitespace-only reason is treated as none", block.notApplicable)
        assertNull(block.flow)
    }

    // Sleep, energy and mood

    @Test fun sleepAndEnergyAndMoodLines() {
        assertEquals("41 of 64", GPSummaryBuilder.sleepLine(disruptedNights = 41, checkInNights = 64))

        assertEquals("average 4.2 of 10, from 58 entries", GPSummaryBuilder.energyLine(4.2, 58))
        assertEquals("average 5.0 of 10, from 1 entry", GPSummaryBuilder.energyLine(5.0, 1))
        assertNull(GPSummaryBuilder.energyLine(0.0, 0))

        assertEquals("Okay 20, Good 12, Low 5", GPSummaryBuilder.moodLine(listOf("Okay" to 20, "Good" to 12, "Low" to 5, "Great" to 1)))
        assertNull(GPSummaryBuilder.moodLine(emptyList()))
    }

    // Medication tables

    /** Deterministic "d MMMM yyyy" so date assertions don't depend on locale or zone. */
    private val styled: (Instant) -> String = { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ROOT).withZone(ZoneOffset.UTC).format(it) }

    private fun med(
        name: String, cat: GPMedCategory, dose: String? = "1 pump", freq: String? = "Daily",
        started: Instant? = null, changed: Instant? = null, stopped: Instant? = null,
    ) = GPMedInput(name, dose, freq, started, changed, stopped, cat)

    @Test fun mhtTableUsesStartedOrLastChangedAndStopped() {
        val meds = listOf(
            med("Oestrogel", GPMedCategory.MHT, dose = "2 pumps", started = at(0), changed = at(30)), // last changed wins
            med("Utrogestan", GPMedCategory.MHT, dose = "100mg", started = at(5)), // started only
            med("Testosterone", GPMedCategory.MHT, dose = "as directed", started = at(2), stopped = at(40)),
            med("Vitamin D", GPMedCategory.SUPPLEMENT), // filtered out of the MHT table
        )
        val table = GPSummaryBuilder.medTable(meds, GPMedCategory.MHT, dateStyle = styled)
        assertEquals(3, table.rows.size)
        assertEquals("2 pumps", table.rows[0].col2) // dose verbatim
        assertEquals(styled(at(30)), table.rows[0].col3) // last changed, not started
        assertEquals(styled(at(5)), table.rows[1].col3) // started only
        assertEquals("${styled(at(2))} · Stopped ${styled(at(40))}", table.rows[2].col3)
    }

    @Test fun otherAndSupplementTablesUseFrequencyAndNotRecorded() {
        val meds = listOf(
            med("Sertraline", GPMedCategory.OTHER_PRESCRIBED, dose = null, freq = "Once daily"),
            med("Magnesium", GPMedCategory.SUPPLEMENT, dose = "400mg", freq = null),
        )
        val other = GPSummaryBuilder.medTable(meds, GPMedCategory.OTHER_PRESCRIBED, dateStyle = styled)
        assertEquals("not recorded", other.rows.first().col2) // no dose entered
        assertEquals("Once daily", other.rows.first().col3) // frequency, not a date

        val supp = GPSummaryBuilder.medTable(meds, GPMedCategory.SUPPLEMENT, dateStyle = styled)
        assertEquals("400mg", supp.rows.first().col2)
        assertEquals("not recorded", supp.rows.first().col3) // no frequency entered
    }

    @Test fun medTableCapsAtTenWithFurtherItemsNote() {
        val table = GPSummaryBuilder.medTable((1..12).map { med("S$it", GPMedCategory.SUPPLEMENT) }, GPMedCategory.SUPPLEMENT, dateStyle = styled)
        assertEquals(10, table.rows.size)
        assertEquals("and 2 further items recorded in Keel", table.overflowNote)

        val eleven = GPSummaryBuilder.medTable((1..11).map { med("S$it", GPMedCategory.SUPPLEMENT) }, GPMedCategory.SUPPLEMENT, dateStyle = styled)
        assertEquals("and 1 further item recorded in Keel", eleven.overflowNote)
    }

    @Test fun treatmentChangesMostRecentFirstCappedAtEight() {
        val changes = listOf(
            GPTreatmentChange(at(10), "oestradiol gel", GPTreatmentChange.Kind.DOSE_CHANGED),
            GPTreatmentChange(at(30), "magnesium", GPTreatmentChange.Kind.STOPPED),
            GPTreatmentChange(at(20), "progesterone", GPTreatmentChange.Kind.DOSE_CHANGED),
        )
        assertEquals(
            listOf(
                "${styled(at(30))}: stopped magnesium",
                "${styled(at(20))}: changed progesterone dose",
                "${styled(at(10))}: changed oestradiol gel dose",
            ),
            GPSummaryBuilder.treatmentChanges(changes, dateStyle = styled),
        )
        val many = (1..10).map { GPTreatmentChange(at(it), "m$it", GPTreatmentChange.Kind.DOSE_CHANGED) }
        assertEquals(8, GPSummaryBuilder.treatmentChanges(many, dateStyle = styled).size)
    }

    // Sleep-symptom classification and inter-period bleeding

    @Test fun sleepDisruptionSymptomMatch() {
        assertTrue(GPSummaryBuilder.isSleepDisruptionSymptom("Trouble sleeping"))
        assertTrue(GPSummaryBuilder.isSleepDisruptionSymptom("Restless sleep"))
        assertTrue(GPSummaryBuilder.isSleepDisruptionSymptom("Insomnia"))
        assertTrue(GPSummaryBuilder.isSleepDisruptionSymptom("Can't sleep past 3am")) // custom
        assertFalse(GPSummaryBuilder.isSleepDisruptionSymptom("Hot flushes"))
        assertFalse(GPSummaryBuilder.isSleepDisruptionSymptom("Night sweats"))
    }

    @Test fun interPeriodBleeding() {
        assertEquals(GPBleeding.NOT_RECORDED, GPSummaryBuilder.interPeriodBleeding(emptyList(), emptyList()))
        // Period days but no spotting.
        assertEquals(GPBleeding.NO, GPSummaryBuilder.interPeriodBleeding(listOf(d(0), d(1), d(2)), emptyList()))
        // Spotting the day after a period run is not "between periods".
        assertEquals(GPBleeding.NO, GPSummaryBuilder.interPeriodBleeding(listOf(d(0), d(1), d(2)), listOf(d(3))))
        // A standalone spotting day, well away from any period, is "between periods".
        assertEquals(GPBleeding.YES, GPSummaryBuilder.interPeriodBleeding(listOf(d(0), d(1), d(2)), listOf(d(14))))
    }

    // Banned-verb guard

    @Test fun generatedCopyHasNoBannedVerb() {
        GPSummaryCopy.lintableStrings.forEach { assertFalse("banned verb in: $it", GPSummaryCopy.containsBannedVerb(it)) }
    }

    @Test fun bannedVerbDetection() {
        assertTrue(GPSummaryCopy.containsBannedVerb("Her sleep improved this month"))
        assertTrue(GPSummaryCopy.containsBannedVerb("This suggests a pattern"))
        assertFalse(GPSummaryCopy.containsBannedVerb("31 of 64 check-in days"))
        assertFalse(GPSummaryCopy.containsBannedVerb("recorded, logged, entered, noted"))
    }
}

/** The period presets and their windows (iOS `GPPeriod.window`, which GPSummaryServiceTests relies on). */
class GPPeriodTests {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun presetsCoverWholeDaysEndingToday() {
        assertEquals(28, GPPeriod.FourWeeks.window(today).dayCount)
        assertEquals(56, GPPeriod.EightWeeks.window(today).dayCount)
        assertEquals(84, GPPeriod.TwelveWeeks.window(today).dayCount)
        val six = GPPeriod.SixMonths.window(today)
        assertEquals(LocalDate.of(2026, 4, 22), six.start)
        assertEquals(today, six.end)
        assertEquals(listOf("4 weeks", "8 weeks", "12 weeks", "6 months"), GPPeriod.presets.map { it.label })
        assertEquals(GPPeriod.TwelveWeeks, GPPeriod.default)
    }

    @Test fun thePreviousWindowIsTheSameLengthJustBefore() {
        val w = GPPeriod.FourWeeks.window(today)
        assertEquals(today.minusDays(27), w.start)
        assertEquals(w.start.minusDays(1), w.previousEnd)
        assertEquals(w.start.minusDays(28), w.previousStart)
        assertTrue(w.contains(today) && w.contains(w.start) && !w.contains(w.previousEnd))
        assertTrue(w.previousContains(w.previousStart) && !w.previousContains(w.start))
    }

    @Test fun aCustomRangeIsOrderedAndAtLeastADay() {
        val w = GPPeriod.Custom(today, today.minusDays(9)).window(today)
        assertEquals(today.minusDays(9), w.start)
        assertEquals(10, w.dayCount)
        assertEquals(1, GPPeriod.Custom(today, today).window(today).dayCount)
    }
}

/** Port of the pure parts of KeelTests/HysterectomyTests.swift (the repository parts are in core/data). */
class HysterectomyTests {
    @Test fun theSixOptionsMatchTheSpec() {
        assertEquals("Have you had a hysterectomy?", Hysterectomy.QUESTION)
        assertEquals(
            listOf(
                "No",
                "Yes, both ovaries were kept",
                "Yes, one ovary was removed",
                "Yes, both ovaries were removed",
                "Yes, I'm not sure about my ovaries",
                "I'd rather not say",
            ),
            Hysterectomy.entries.map { it.label },
        )
    }

    @Test fun summaryLineMatchesHerAnswer() {
        assertEquals("yes, one ovary removed (as recorded by her)", Hysterectomy.summaryLine(Hysterectomy.YES_ONE_OVARY_REMOVED, null))
        assertEquals("yes, both ovaries removed, 2019 (as recorded by her)", Hysterectomy.summaryLine(Hysterectomy.YES_BOTH_OVARIES_REMOVED, 2019))
        assertEquals("yes, not sure about ovaries (as recorded by her)", Hysterectomy.summaryLine(Hysterectomy.YES_NOT_SURE_ABOUT_OVARIES, null))
        assertEquals("no (as recorded by her)", Hysterectomy.summaryLine(Hysterectomy.NO, 2019)) // no year for "No"
        assertNull(Hysterectomy.summaryLine(Hysterectomy.RATHER_NOT_SAY, null)) // never on the summary
        assertNull(Hysterectomy.summaryLine(null, null)) // unanswered
    }

    @Test fun yearMustBePlausible() {
        val now = LocalDate.of(2023, 11, 14)
        assertEquals(2019, Hysterectomy.validYear("2019", now))
        assertEquals(1998, Hysterectomy.validYear(" 1998 ", now))
        assertNull(Hysterectomy.validYear("", now))
        assertNull(Hysterectomy.validYear("20", now)) // still typing
        assertNull(Hysterectomy.validYear("2031", now)) // future
        assertNull(Hysterectomy.validYear("1850", now))
    }

    @Test fun rawValuesMatchIos() {
        assertEquals(
            listOf("no", "yesOvariesKept", "yesOneOvaryRemoved", "yesBothOvariesRemoved", "yesNotSureAboutOvaries", "ratherNotSay"),
            Hysterectomy.entries.map { it.raw },
        )
        assertEquals(Hysterectomy.YES_OVARIES_KEPT, Hysterectomy.fromRaw("yesOvariesKept"))
        assertNull(Hysterectomy.fromRaw(null))
    }
}

/** Port of KeelTests/AlcoholTests.swift `testLineFormatMatchesTheSymptomRows`. */
class GPAlcoholLineTests {
    @Test fun lineFormatMatchesTheSymptomRows() {
        assertEquals("recorded on 6 of 24 check-in days", GPSummaryBuilder.alcoholLine(recordedDays = 6, checkInDays = 24))
        assertEquals("recorded on 1 of 1 check-in day", GPSummaryBuilder.alcoholLine(recordedDays = 1, checkInDays = 1))
        assertNull(GPSummaryBuilder.alcoholLine(recordedDays = 0, checkInDays = 24)) // nothing recorded: no line
    }
}
