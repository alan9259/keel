package com.therecalibrationyears.keel.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of KeelTests/LookingBackTests.swift (the `MonthSummary` tests): a fixed-wording summary
 * of the current month, no generated text. UTC, "now" 14 October 2026 10:00.
 */
class LookingBackTests {
    private val utc: ZoneId = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-10-14T10:00:00Z")
    private fun at(day: Int, hour: Int = 9, month: Int = 10) = LocalDateTime.of(2026, month, day, hour, 0).toInstant(ZoneOffset.UTC)
    private fun entry(date: Instant, symptoms: List<String> = emptyList(), notes: String? = null) =
        MonthSummary.Entry(UUID.randomUUID().toString(), date, symptoms, notes)

    @Test fun countsDaysSymptomsAndNotesForThisMonthOnly() {
        val summary = MonthSummary.build(
            listOf(
                entry(at(2), listOf("Hot flushes", "Headaches"), notes = "Rough night"),
                entry(at(2, hour = 18), listOf("Hot flushes")), // same day: counted once
                entry(at(9), listOf("Hot flushes"), notes = "  "), // blank note ignored
                entry(at(14), listOf("Brain fog"), notes = "Better today"),
                entry(at(28, month = 9), listOf("Hot flushes"), notes = "Last month"), // outside the month
            ),
            now, utc,
        )
        assertEquals(3, summary.daysCheckedIn)
        assertEquals("You checked in on 3 days this month.", summary.checkInLine)
        assertEquals(
            listOf(
                MonthSummary.SymptomCount("Hot flushes", 2),
                MonthSummary.SymptomCount("Brain fog", 1),
                MonthSummary.SymptomCount("Headaches", 1), // ties by name
            ),
            summary.topSymptoms,
        )
        assertEquals(listOf("Better today", "Rough night"), summary.notes.map { it.text }) // newest first
        assertEquals("October so far", summary.heading)
    }

    @Test fun emptyMonthHasHonestWording() {
        val summary = MonthSummary.build(emptyList(), now, utc)
        assertEquals(0, summary.daysCheckedIn)
        assertEquals("No check-ins yet this month.", summary.checkInLine)
        assertTrue(summary.topSymptoms.isEmpty())
        assertTrue(summary.notes.isEmpty())
    }

    @Test fun oneDayIsSingular() {
        assertEquals("You checked in on 1 day this month.", MonthSummary.build(listOf(entry(at(3))), now, utc).checkInLine)
        assertEquals("1 day", MonthSummary.days(1))
        assertEquals("4 days", MonthSummary.days(4))
    }

    @Test fun topSymptomsCappedAtFive() {
        val summary = MonthSummary.build(listOf(entry(at(5), listOf("A", "B", "C", "D", "E", "F", "G"))), now, utc)
        assertEquals(MonthSummary.TOP_LIMIT, summary.topSymptoms.size)
    }

    @Test fun monthBoundaryFirstAndLastDay() {
        val summary = MonthSummary.build(listOf(entry(at(1, hour = 0)), entry(at(31, hour = 23)), entry(at(1, hour = 0, month = 11))), now, utc)
        assertEquals(2, summary.daysCheckedIn)
    }

    /** Her month is her local month: 23:30 UTC on 31 Oct is 1 Nov in Sydney. */
    @Test fun theMonthFollowsHerZone() {
        val sydney = ZoneId.of("Australia/Sydney")
        val summary = MonthSummary.build(listOf(entry(Instant.parse("2026-10-31T23:30:00Z"))), Instant.parse("2026-11-02T00:00:00Z"), sydney)
        assertEquals("November so far", summary.heading)
        assertEquals(1, summary.daysCheckedIn)
    }

    /**
     * Adapted from testOtherPhoneCalendarsStillMeanTheGregorianMonth: java.time is always
     * Gregorian, and the heading stays English whatever her language.
     */
    @Test fun theHeadingIsTheEnglishGregorianMonth() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("he-IL-u-ca-hebrew"))
            assertEquals("September so far", MonthSummary.build(emptyList(), Instant.parse("2026-09-01T00:00:00Z"), utc).heading)
        } finally {
            java.util.Locale.setDefault(default)
        }
    }
}
