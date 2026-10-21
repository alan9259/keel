package com.therecalibrationyears.keel.medications

import com.therecalibrationyears.keel.domain.DoseSchedule
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.MedicationMethod
import com.therecalibrationyears.keel.domain.TreatmentCatalog
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The details form's rules (iOS `TreatmentDetailForm`, logic only). */
class TreatmentFormTests {
    private val today = LocalDate.of(2026, 10, 21)
    private fun draft(vararg slots: DoseSlot) =
        TreatmentDraft(name = "Magnesium", kind = TreatmentKind.SUPPLEMENT, schedule = DoseSchedule(slots = slots.toList().ifEmpty { listOf(DoseSlot()) }))

    @Test fun amountTextAndParsing() {
        assertEquals("", TreatmentForm.amountText(null))
        assertEquals("400", TreatmentForm.amountText(400.0))
        assertEquals("0.5", TreatmentForm.amountText(0.5))
        assertEquals(1.5, TreatmentForm.parseAmount("1,5")!!, 0.0) // a comma reads as a decimal point
        assertNull(TreatmentForm.parseAmount(""))
        assertNull(TreatmentForm.parseAmount("abc"))
    }

    @Test fun doseLabelFollowsTheKind() {
        assertEquals("Strength", TreatmentForm.doseLabel(TreatmentKind.TREATMENT))
        assertEquals("Dose", TreatmentForm.doseLabel(TreatmentKind.SUPPLEMENT))
    }

    @Test fun choosingACycleStartsItTodayOnce() {
        val cycle = TreatmentForm.setKind(draft(), DoseSchedule.Kind.CYCLE, today)
        assertEquals(today, cycle.schedule.anchor)
        // Back to weekly and to cycle again a day later: day 1 stays where she left it.
        val again = TreatmentForm.setKind(TreatmentForm.setKind(cycle, DoseSchedule.Kind.WEEKLY, today), DoseSchedule.Kind.CYCLE, today.plusDays(1))
        assertEquals(today, again.schedule.anchor)
    }

    @Test fun cycleLengthKeepsAtLeastOneDayOn() {
        val d = TreatmentForm.setCycleLength(draft(), 5) // pause was 7
        assertEquals(5, d.schedule.cycleLength)
        assertEquals(4, d.schedule.pauseDays)
        assertEquals(2, TreatmentForm.setCycleLength(draft(), 1).schedule.cycleLength)
        assertEquals(90, TreatmentForm.setCycleLength(draft(), 200).schedule.cycleLength)
        assertEquals(27, TreatmentForm.setPauseDays(draft(), 40).schedule.pauseDays)
    }

    @Test fun todayIsDaySetsTheAnchorBack() {
        val d = TreatmentForm.setCycleDayToday(TreatmentForm.setKind(draft(), DoseSchedule.Kind.CYCLE, today), 10, today)
        assertEquals(today.minusDays(9), d.schedule.anchor)
        assertEquals(10, TreatmentForm.cycleDayToday(d, today))
    }

    @Test fun aNewDoseGetsAFreshTimeAndEveryDay() {
        assertEquals(9, TreatmentForm.addSlot(draft(DoseSlot())).schedule.slots.last().hour)
        assertEquals(20, TreatmentForm.addSlot(draft(DoseSlot(hour = 8))).schedule.slots.last().hour)
        val afterEvening = TreatmentForm.addSlot(draft(DoseSlot(weekdays = setOf(1, 7), hour = 21))).schedule.slots.last()
        assertEquals(8, afterEvening.hour)
        assertEquals((1..7).toSet(), afterEvening.weekdays) // never inherits the last one's days
    }

    @Test fun theLastDoseCanNotBeRemoved() {
        val one = draft(DoseSlot(hour = 8))
        assertEquals(1, TreatmentForm.removeSlot(one, one.schedule.slots.single().id).schedule.slots.size)
        val two = TreatmentForm.addSlot(one)
        assertEquals(1, TreatmentForm.removeSlot(two, two.schedule.slots.first().id).schedule.slots.size)
    }

    @Test fun theLastDayStaysOn() {
        val d = draft(DoseSlot(weekdays = setOf(2)))
        val id = d.schedule.slots.single().id
        assertEquals(setOf(2), TreatmentForm.toggleWeekday(d, id, 2).schedule.slots.single().weekdays)
        assertEquals(setOf(2, 4), TreatmentForm.toggleWeekday(d, id, 4).schedule.slots.single().weekdays)
        val all = draft(DoseSlot())
        assertEquals((1..7).toSet() - 1, TreatmentForm.toggleWeekday(all, all.schedule.slots.single().id, 1).schedule.slots.single().weekdays)
    }

    @Test fun stoppingNeverStampsADate() {
        val stopped = TreatmentForm.setStillTaking(draft(), false)
        assertFalse(stopped.isActive)
        assertNull(stopped.stoppedAt)
        val dated = TreatmentForm.setStoppedAt(stopped, Instant.parse("2026-10-01T00:00:00Z"))
        val back = TreatmentForm.setStillTaking(dated, true)
        assertTrue(back.isActive)
        assertNull(back.stoppedAt) // reactivating clears the date she added
    }
}

/** The picker's rules (iOS `TreatmentPickerSheet`, logic only). */
class TreatmentPickerTests {
    private val catalog = TreatmentCatalog(
        version = 1,
        groups = listOf(
            TreatmentCatalog.Group("oestrogen", "treatment", "Oestrogen", "Oestrogen", defaultMethodRaw = "patch",
                items = listOf(TreatmentCatalog.Item("Oestrogen gel", methodRaw = "gel"), TreatmentCatalog.Item("Oestrogen patch"))),
            TreatmentCatalog.Group("oestrogen-other", "treatment", "Oestrogen", "Other forms",
                items = listOf(TreatmentCatalog.Item("Compounded oestrogen", compounded = true, offLabel = true))),
            TreatmentCatalog.Group("supplements", "supplement", "Supplements", "Common", items = listOf(TreatmentCatalog.Item("Magnesium"))),
        ),
    )

    @Test fun tabsAndSearchShapeTheList() {
        assertEquals(listOf("oestrogen", "oestrogen-other"), TreatmentPicker.visibleGroups(catalog, TreatmentKind.TREATMENT, "").map { it.id })
        assertEquals(listOf("supplements"), TreatmentPicker.visibleGroups(catalog, TreatmentKind.SUPPLEMENT, " ").map { it.id })
        assertEquals(listOf("oestrogen-other"), TreatmentPicker.visibleGroups(catalog, TreatmentKind.TREATMENT, "compounded").map { it.id })
    }

    @Test fun aSectionHeadingLeadsOnlyItsFirstGroup() {
        val visible = TreatmentPicker.visibleGroups(catalog, TreatmentKind.TREATMENT, "")
        assertTrue(TreatmentPicker.showsSectionHeading(visible, visible[0]))
        assertFalse(TreatmentPicker.showsSectionHeading(visible, visible[1]))
        // Searched down to the second group, it leads.
        val searched = TreatmentPicker.visibleGroups(catalog, TreatmentKind.TREATMENT, "compounded")
        assertTrue(TreatmentPicker.showsSectionHeading(searched, searched[0]))
    }

    @Test fun anItemCarriesItsMethodAndFlagsButNeverADose() {
        val group = catalog.groups[0]
        val gel = TreatmentPicker.draftFor(group.items[0], group, existing = null)
        assertEquals(MedicationMethod.GEL, gel.method)
        assertEquals("oestrogen", gel.catalogGroupID)
        assertNull(gel.doseAmount)
        assertNull(gel.doseUnit)
        assertEquals(MedicationMethod.PATCH, TreatmentPicker.draftFor(group.items[1], group, null).method) // the group's default
        val other = catalog.groups[1]
        val compounded = TreatmentPicker.draftFor(other.items[0], other, null)
        assertTrue(compounded.isCompounded && compounded.isOffLabel)
    }

    @Test fun somethingAlreadyOnHerListOpensWhatSheHas() {
        val mine = TreatmentDraft(name = "Oestrogen gel", kind = TreatmentKind.TREATMENT, note = "mine")
        assertEquals(mine, TreatmentPicker.draftFor(catalog.groups[0].items[0], catalog.groups[0], mine))
        assertEquals("Already on your list", TreatmentPicker.formTitle(alreadyTaking = true))
        assertEquals("Add details", TreatmentPicker.formTitle(alreadyTaking = false))
    }

    @Test fun herOwnNamesAreTrimmedAndBlankIsIgnored() {
        val group = catalog.groups[0]
        val custom = TreatmentPicker.draftForCustom("  Estradiol cream ", group, null)!!
        assertEquals("Estradiol cream", custom.name)
        assertEquals("oestrogen", custom.catalogGroupID)
        assertEquals(MedicationMethod.PATCH, custom.method)
        assertNull(TreatmentPicker.draftForCustom("   ", group, null))
        // A searched name is her own, with no group.
        val searched = TreatmentPicker.draftForSearch("Black cohosh", TreatmentKind.SUPPLEMENT, null)!!
        assertNull(searched.catalogGroupID)
        assertEquals(TreatmentKind.SUPPLEMENT, searched.kind)
        assertNull(TreatmentPicker.draftForSearch("", TreatmentKind.SUPPLEMENT, null))
    }
}
