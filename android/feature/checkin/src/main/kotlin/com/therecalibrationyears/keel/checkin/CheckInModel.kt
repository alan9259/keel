package com.therecalibrationyears.keel.checkin

import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.SymptomSeverity
import java.time.LocalDate

/** An entry to open: the mood from the slide, the day it belongs to, and the entry if editing. */
data class CheckInRequest(val mood: Mood, val day: LocalDate, val editingId: String? = null)

/** What she has filled in on the check-in so far (port of `CheckInModal`'s state). */
data class CheckInDraft(
    val energy: EnergyLevel? = null,
    val notes: String = "",
    /** Hours for this night; editable unless imported from Health Connect. */
    val sleepHours: Double? = null,
    val sleepFromHealth: Boolean = false,
    /** Null means not recorded; zero is a real answer. */
    val alcohol: Int? = null,
    /** Symptom id to severity (1..3); absent means unselected. */
    val selected: Map<String, Int> = emptyMap(),
) {
    /** One tap more on a symptom: mild, moderate, severe, then off. */
    fun toggled(symptomId: String): CheckInDraft {
        val next = SymptomSeverity.nextLevel(selected[symptomId] ?: 0)
        return copy(selected = if (next == 0) selected - symptomId else selected + (symptomId to next))
    }
}

/**
 * The chips shown on the check-in itself (iOS `quickChips`): the default chips in the order
 * resolved when the screen opened (so the row doesn't reshuffle under her), then anything she
 * picked in "more", so every selection can be seen and adjusted without reopening it.
 */
fun quickChips(active: List<SymptomEntity>, defaultOrder: List<String>, selected: Map<String, Int>): List<SymptomEntity> {
    val byId = active.associateBy { it.id }
    val defaults = defaultOrder.mapNotNull(byId::get)
    val defaultIds = defaultOrder.toSet()
    return defaults + active.filter { it.id in selected && it.id !in defaultIds }
}
