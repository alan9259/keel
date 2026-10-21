package com.therecalibrationyears.keel.gpsummary

import androidx.compose.runtime.saveable.SaverScope
import com.therecalibrationyears.keel.domain.GPBleeding
import com.therecalibrationyears.keel.domain.GPCycleBlock
import com.therecalibrationyears.keel.domain.GPMedCategory
import com.therecalibrationyears.keel.domain.GPMedTable
import com.therecalibrationyears.keel.domain.GPPeriod
import com.therecalibrationyears.keel.domain.GPReviewMed
import com.therecalibrationyears.keel.domain.GPSummaryCopy
import com.therecalibrationyears.keel.domain.GPSummaryDocument
import com.therecalibrationyears.keel.domain.GPSummarySection
import com.therecalibrationyears.keel.domain.GPSymptomStat
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A resolved document with chosen symptoms and alcohol, everything else empty. */
internal fun sampleDocument(stats: List<GPSymptomStat> = emptyList(), checkInDays: Int = 0, alcoholLine: String? = null, day: LocalDate = LocalDate.of(2026, 10, 21)) =
    GPSummaryDocument(
        name = null, age = null, periodLabel = "29 July to 21 October 2026", checkInsLabel = "$checkInDays of 84 days",
        priorities = emptyList(), symptomStats = stats, checkInDaysThisPeriod = checkInDays, checkInDaysPreviousPeriod = 0,
        previousWindowDayCount = 84, defaultSymptomMaxRows = 6, impactLine = null,
        cycle = GPCycleBlock(null, 0, GPSummaryCopy.notEnoughPeriodsForRange, null, GPBleeding.NOT_RECORDED.label, null),
        mht = GPMedTable(emptyList(), null), otherMeds = GPMedTable(emptyList(), null), supplements = GPMedTable(emptyList(), null),
        treatmentChanges = emptyList(), sleepLine = "0 of $checkInDays", energyLine = null, moodLine = null, alcoholLine = alcoholLine,
        questions = emptyList(), generatedOn = day,
    )

internal fun stat(name: String, days: Int, prev: Int = 0) = GPSymptomStat(name, false, days, prev, 2.0, Instant.EPOCH)

/** The flow's rules (iOS `GPSummaryFlowModel`), on Wednesday 21 Oct 2026. */
class GPSummaryDraftTests {
    private val today = LocalDate.of(2026, 10, 21)
    private val fresh = GPSummaryDraft.start(today)

    /** Port of KeelTests/HealthSyncBehaviourTests.swift `GPSummaryLeaveTests`. */
    @Test fun homeConfirmsBeforeDiscardingADraftPastTheFirstStep() {
        assertFalse(GPSummaryDraft.homeNeedsConfirmation(GPSummaryStep.PERIOD))
        assertTrue(GPSummaryDraft.homeNeedsConfirmation(GPSummaryStep.REVIEW))
        assertTrue(GPSummaryDraft.homeNeedsConfirmation(GPSummaryStep.DETAILS))
        assertTrue(GPSummaryDraft.homeNeedsConfirmation(GPSummaryStep.PREVIEW))
    }

    @Test fun stepsMoveOneAtATimeAndStopAtTheEnds() {
        assertTrue(fresh.isFirst)
        assertEquals(fresh, fresh.back()) // nothing before the first step
        val last = fresh.next().next().next()
        assertEquals(GPSummaryStep.PREVIEW, last.step)
        assertTrue(last.isLast)
        assertEquals(last, last.next())
        assertEquals(GPSummaryStep.DETAILS, last.back().step)
    }

    @Test fun twelveWeeksByDefaultAndACustomRangeOfHerOwn() {
        assertEquals(GPPeriod.TwelveWeeks, fresh.inputs.period)
        assertEquals(today.minusDays(83), fresh.customStart)
        assertEquals(today, fresh.customEnd)
        assertFalse(fresh.isCustom)

        val custom = fresh.setCustomRange()
        assertEquals(GPPeriod.Custom(today.minusDays(83), today), custom.inputs.period)
        val moved = custom.setCustomRange(start = today.minusDays(10))
        assertEquals(GPPeriod.Custom(today.minusDays(10), today), moved.inputs.period)
        assertEquals(GPPeriod.FourWeeks, moved.selectPreset(GPPeriod.FourWeeks).inputs.period)
    }

    @Test fun reviewAndDetailTogglesAddAndRemove() {
        val d = fresh.toggleSymptom("Hot flushes").toggleMed("MED-1").toggleSection(GPSummarySection.MOOD)
            .toggleArea("Sleep").toggleArea("Emotional wellbeing").toggleOverall("Mild")
        assertFalse(d.isSymptomIncluded("Hot flushes"))
        assertFalse(d.isMedIncluded("MED-1"))
        assertFalse(d.isSectionIncluded(GPSummarySection.MOOD))
        assertEquals(listOf("Sleep", "Emotional wellbeing"), d.inputs.impactAreas) // in the order she picked
        assertEquals("Mild", d.inputs.impactOverall)

        val undone = d.toggleSymptom("Hot flushes").toggleMed("MED-1").toggleSection(GPSummarySection.MOOD).toggleArea("Sleep").toggleOverall("Mild")
        assertTrue(undone.isSymptomIncluded("Hot flushes") && undone.isMedIncluded("MED-1") && undone.isSectionIncluded(GPSummarySection.MOOD))
        assertEquals(listOf("Emotional wellbeing"), undone.inputs.impactAreas)
        assertEquals(null, undone.inputs.impactOverall) // tapping the chosen level again clears it
        assertEquals("Moderate", d.toggleOverall("Moderate").inputs.impactOverall) // one level at a time
    }

    @Test fun herWordsAreHeldToTheSpecLengthsAndCommittedAsTyped() {
        val long = "x".repeat(200)
        val d = fresh.setPriority(0, long).setQuestion(2, long).setImpactOther(long).setPriority(1, "  Sleep  ")
        assertEquals(100, d.priorityDrafts[0].length)
        assertEquals(120, d.questionDrafts[2].length)
        assertEquals(60, d.inputs.impactOther.length)
        assertEquals(listOf("x".repeat(100), "  Sleep  ", ""), d.committedInputs.priorities) // the service trims and drops blanks
        assertEquals(d.questionDrafts, d.committedInputs.questions)
    }

    @Test fun everythingSheChoseSurvivesBeingSavedAndRestored() {
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val drafts = listOf(
            fresh,
            fresh.next().next().selectPreset(GPPeriod.SixMonths).toggleSymptom("Brain fog").toggleMed("MED-1")
                .toggleSection(GPSummarySection.ALCOHOL).toggleArea("Sleep").toggleOverall("Significant").setImpactOther("Gardening")
                .setPriority(0, "Night sweats").setQuestion(1, "Blood test?").setIncludeName(true).setIncludeAge(true).setIncludeHysterectomy(true),
            fresh.setCustomRange(start = today.minusDays(30), end = today.minusDays(2)),
        )
        drafts.forEach { d ->
            val saved = with(GPSummaryDraft.Saver) { scope.save(d) }!!
            assertEquals(d, GPSummaryDraft.Saver.restore(saved))
        }
    }

    /** iOS lists symptoms logged only in the window before at "0 of N"; Android lists only this period's (plan D26). */
    @Test fun theReviewListsWhatSheRecordedInThePeriodMostDaysFirst() {
        val doc = sampleDocument(
            stats = listOf(stat("Joint pain", 2), stat("Hot flushes", 9), stat("Only before", 0, prev = 4)),
            checkInDays = 12, alcoholLine = "recorded on 3 of 12 check-in days",
        )
        val meds = listOf(GPReviewMed("A", "Oestrogel", GPMedCategory.MHT))
        val c = GPSummaryCandidates.from(doc, meds)
        assertEquals(listOf("Hot flushes", "Joint pain"), c.symptoms.map { it.name })
        assertEquals(12, c.checkInDays)
        assertEquals(meds, c.meds)
        assertTrue(c.hasAlcoholRecords)
        assertFalse(GPSummaryCandidates.from(sampleDocument(), emptyList()).hasAlcoholRecords)
    }

    @Test fun candidatesAreReadWithNothingRemoved() {
        val d = fresh.toggleSymptom("Hot flushes").toggleMed("MED-1").toggleSection(GPSummarySection.CYCLE)
        val base = GPSummaryCandidates.baseInputs(d.inputs)
        assertTrue(base.removedSymptomNames.isEmpty() && base.removedMedIDs.isEmpty())
        assertEquals(d.inputs.period, base.period)
    }
}
