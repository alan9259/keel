package com.therecalibrationyears.keel.medications

import com.therecalibrationyears.keel.domain.DoseSchedule
import com.therecalibrationyears.keel.domain.DoseSlot
import com.therecalibrationyears.keel.domain.TreatmentCatalog
import com.therecalibrationyears.keel.domain.TreatmentDraft
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.time.Instant
import java.time.LocalDate

/**
 * The details form's rules (port of the logic in iOS `TreatmentDetailForm`), kept pure so
 * they're testable without a screen. Each returns the changed draft.
 */
object TreatmentForm {
    /** "Strength" for a prescription, "Dose" for a supplement. */
    fun doseLabel(kind: TreatmentKind): String = if (kind == TreatmentKind.TREATMENT) "Strength" else "Dose"

    /** The amount as she'd read it back: an empty field means "not set", never zero. */
    fun amountText(amount: Double?): String = when {
        amount == null -> ""
        amount == Math.rint(amount) -> amount.toLong().toString()
        else -> amount.toString()
    }

    /** What she typed, with a comma read as a decimal point; anything else is "not set". */
    fun parseAmount(text: String): Double? = text.replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /** Picking a cycle starts it today unless it already has a day 1. */
    fun setKind(draft: TreatmentDraft, kind: DoseSchedule.Kind, today: LocalDate): TreatmentDraft {
        val s = draft.schedule
        return draft.copy(schedule = s.copy(kind = kind, anchor = if (kind == DoseSchedule.Kind.CYCLE && s.anchor == null) today else s.anchor))
    }

    /** The pause always leaves at least one day on. */
    fun setCycleLength(draft: TreatmentDraft, length: Int): TreatmentDraft {
        val n = length.coerceIn(CYCLE_LENGTHS)
        return draft.copy(schedule = draft.schedule.copy(cycleLength = n, pauseDays = minOf(draft.schedule.pauseDays, n - 1)))
    }

    fun setPauseDays(draft: TreatmentDraft, days: Int): TreatmentDraft =
        draft.copy(schedule = draft.schedule.copy(pauseDays = days.coerceIn(0, draft.schedule.cycleLength - 1)))

    /** Which day of the cycle today is, so she can set one up part way through. */
    fun cycleDayToday(draft: TreatmentDraft, today: LocalDate): Int = draft.schedule.cycleDay(today) ?: 1

    fun setCycleDayToday(draft: TreatmentDraft, day: Int, today: LocalDate): TreatmentDraft =
        draft.copy(schedule = draft.schedule.copy(anchor = today.minusDays((day - 1).toLong())))

    /**
     * A new dose starts every day at a fresh time rather than copying the last one's days, so
     * nothing is inherited without her seeing it: 8 pm after a morning dose, 8 am after an
     * evening one, 9 am when there's no time yet.
     */
    fun addSlot(draft: TreatmentDraft): TreatmentDraft {
        val lastTimed = draft.schedule.sortedSlots.lastOrNull { it.hasTime }
        val hour = lastTimed?.let { if ((it.hour ?: 9) >= 12) 8 else 20 } ?: 9
        return draft.copy(schedule = draft.schedule.copy(slots = draft.schedule.slots + DoseSlot(hour = hour, minute = 0)))
    }

    fun removeSlot(draft: TreatmentDraft, id: String): TreatmentDraft =
        if (draft.schedule.slots.size <= 1) draft
        else draft.copy(schedule = draft.schedule.copy(slots = draft.schedule.slots.filterNot { it.id == id }))

    fun updateSlot(draft: TreatmentDraft, id: String, change: (DoseSlot) -> DoseSlot): TreatmentDraft =
        draft.copy(schedule = draft.schedule.copy(slots = draft.schedule.slots.map { if (it.id == id) change(it) else it }))

    /**
     * Turn a day on or off for one dose. The last remaining day stays put: a dose with no
     * days would never come round, which is never what she meant.
     */
    fun toggleWeekday(draft: TreatmentDraft, id: String, weekday: Int): TreatmentDraft = updateSlot(draft, id) { slot ->
        val days = slot.weekdays.ifEmpty { DoseSlot.ALL_DAYS }
        when {
            weekday !in days -> slot.copy(weekdays = days + weekday)
            days.size > 1 -> slot.copy(weekdays = days - weekday)
            else -> slot
        }
    }

    /** Stopping never stamps a date; reactivating clears any date she added. */
    fun setStillTaking(draft: TreatmentDraft, stillTaking: Boolean): TreatmentDraft =
        draft.copy(isActive = stillTaking, stoppedAt = if (stillTaking) null else draft.stoppedAt)

    fun setStoppedAt(draft: TreatmentDraft, at: Instant?): TreatmentDraft = draft.copy(stoppedAt = at)

    val CYCLE_LENGTHS = 2..90
}

/**
 * The picker's rules (port of the logic in iOS `TreatmentPickerSheet`): what a tap on the
 * list starts, and how the search shapes it. Nothing is recommended, ranked or badged.
 */
object TreatmentPicker {
    /** The list for one tab, filtered to the search; groups with no match drop out. */
    fun visibleGroups(catalog: TreatmentCatalog, kind: TreatmentKind, query: String): List<TreatmentCatalog.Group> =
        TreatmentCatalog.filter(catalog.groups(kind), query)

    /** A section heading leads only the first group that carries it ("Oestrogen" once). */
    fun showsSectionHeading(visible: List<TreatmentCatalog.Group>, group: TreatmentCatalog.Group): Boolean =
        visible.firstOrNull { it.section == group.section }?.id == group.id

    /** Something from the list. Already on her list: open what she has rather than a second copy. */
    fun draftFor(item: TreatmentCatalog.Item, group: TreatmentCatalog.Group, existing: TreatmentDraft?): TreatmentDraft =
        existing ?: TreatmentDraft(
            name = item.name,
            kind = group.kind,
            catalogGroupID = group.id,
            method = item.method ?: group.defaultMethod,
            isOffLabel = item.isOffLabel,
            isCompounded = item.isCompounded,
        )

    /** Her own name, typed under a group. */
    fun draftForCustom(name: String, group: TreatmentCatalog.Group, existing: TreatmentDraft?): TreatmentDraft? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return existing ?: TreatmentDraft(name = trimmed, kind = group.kind, catalogGroupID = group.id, method = group.defaultMethod)
    }

    /** A searched name: her own, with no catalog group rather than mis-filed under the first one. */
    fun draftForSearch(query: String, kind: TreatmentKind, existing: TreatmentDraft?): TreatmentDraft? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        return existing ?: TreatmentDraft(name = trimmed, kind = kind)
    }

    /** "Add details", or "Already on your list" when it opens what she has. */
    fun formTitle(alreadyTaking: Boolean): String = if (alreadyTaking) "Already on your list" else "Add details"
}
