package com.therecalibrationyears.keel.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of KeelTests/QuickChipRankingTests.swift (pure ranking). */
class QuickChipRankingTests {
    private fun c(id: String, name: String, def: Boolean) = QuickChipCandidate(id, name, def)

    @Test fun coldStartUsesCatalogOrderAndExcludesUnloggedNonDefaults() {
        val candidates = listOf(c("sleep", "Trouble sleeping", true), c("hot", "Hot flushes", true), c("custom", "Tinnitus", false))
        assertEquals(listOf("hot", "sleep"), QuickChipRanking.rankedIds(candidates, emptyMap(), 10))
    }

    @Test fun frequentCustomSymptomSurfacesToFront() {
        val candidates = listOf(c("hot", "Hot flushes", true), c("custom", "Tinnitus", false))
        assertEquals(listOf("custom", "hot"), QuickChipRanking.rankedIds(candidates, mapOf("custom" to 5), 10))
    }

    @Test fun mostLoggedRanksFirstDespiteCatalogOrder() {
        val candidates = listOf(c("a", "Hot flushes", true), c("b", "Night sweats", true))
        assertEquals(listOf("b", "a"), QuickChipRanking.rankedIds(candidates, mapOf("a" to 1, "b" to 4), 10))
    }

    @Test fun equalCountTieBreaksToDefaultChipFirst() {
        val candidates = listOf(c("nonDef", "Tinnitus", false), c("def", "Hot flushes", true))
        assertEquals(listOf("def", "nonDef"), QuickChipRanking.rankedIds(candidates, mapOf("def" to 2, "nonDef" to 2), 10))
    }

    @Test fun limitCapsTheRow() {
        val candidates = (0..4).map { c("$it", "Hot flushes", true) }
        assertEquals(3, QuickChipRanking.rankedIds(candidates, emptyMap(), 3).size)
    }
}

class SymptomCatalogTests {
    @Test fun catalogMatchesIos() {
        assertEquals(54, SymptomCatalog.builtIn.size)
        assertEquals(2, SymptomCatalog.VERSION)
        val defaults = SymptomCatalog.builtIn.filter { it.isDefault }.map { it.name }.toSet()
        assertEquals("every default chip has a place in the default order", defaults, SymptomCatalog.defaultOrder.toSet())
        assertEquals(SymptomCatalog.builtIn.size, SymptomCatalog.builtIn.map { it.name.lowercase() }.toSet().size)
    }

    /** Port of SensitiveGroupCopyTests (owner wording, 4 Oct 2026). */
    @Test fun intimacyIntroWording() {
        assertEquals(
            "These can be harder to talk about. Note only what feels relevant to you. They stay on your phone unless you choose to share them.",
            SymptomCategory.INTIMACY.intro,
        )
        assertTrue(SymptomCategory.INTIMACY.isSensitive)
        assertEquals(listOf(SymptomCategory.INTIMACY), SymptomCategory.entries.filter { it.isSensitive })
    }

    @Test fun herOwnGroupsLabelThemselvesAndSortLast() {
        assertEquals("Garden aches", SymptomCategory.labelForRaw("Garden aches"))
        assertEquals("Body & temperature", SymptomCategory.labelForRaw("body"))
        assertEquals(100, SymptomCategory.sortOrderForRaw("Garden aches"))
        assertEquals(listOf("sleep", "energy", "body", "aches", "cognition", "mood", "skin", "digestion", "intimacy"),
            SymptomCategory.entries.sortedBy { it.sortOrder }.map { it.raw })
    }
}

class CheckInRulesTests {
    private val sydney = ZoneId.of("Australia/Sydney")

    /** A past day's entry keeps the time she made it, on that day. */
    @Test fun entryIsStampedWithTodaysTimeOnTheChosenDay() {
        val now = Instant.parse("2026-10-21T03:42:10Z") // 14:42:10 in Sydney
        val stamp = CheckInRules.entryTimestamp(LocalDate.of(2026, 10, 18), now, sydney)
        assertEquals("2026-10-18T14:42:10+11:00[Australia/Sydney]", stamp.atZone(sydney).toString())
    }

    /** On the day clocks go forward (4 Oct 2026, 2:00 → 3:00), a time in the gap moves forward. */
    @Test fun aTimeThatDoesntExistThatDayMovesForward() {
        val now = Instant.parse("2026-10-20T15:30:00Z") // 02:30 in Sydney on the 21st
        val stamp = CheckInRules.entryTimestamp(LocalDate.of(2026, 10, 4), now, sydney)
        assertEquals("2026-10-04T03:30+11:00[Australia/Sydney]", stamp.atZone(sydney).toString())
    }

    @Test fun sleepStepsWithinBoundsAndReadsLikeIos() {
        assertEquals(8.0, CheckInRules.adjustSleep(null, 0.5), 0.0) // starts at 7.5
        assertEquals(0.5, CheckInRules.adjustSleep(0.5, -0.5), 0.0)
        assertEquals(14.0, CheckInRules.adjustSleep(14.0, 0.5), 0.0)
        assertEquals("8 hrs", CheckInRules.sleepLabel(8.0))
        assertEquals("7.5 hrs", CheckInRules.sleepLabel(7.5))
    }

    @Test fun alcoholNeverGoesBelowZero() {
        assertEquals(0, CheckInRules.adjustAlcohol(0, -1))
        assertEquals(1, CheckInRules.adjustAlcohol(null, 1))
    }
}
