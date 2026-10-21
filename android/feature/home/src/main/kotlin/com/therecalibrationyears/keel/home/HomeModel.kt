package com.therecalibrationyears.keel.home

import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.LoggedSymptom
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.MedicationLogEntity
import com.therecalibrationyears.keel.domain.CalendarGrid
import com.therecalibrationyears.keel.domain.DayAmount
import com.therecalibrationyears.keel.domain.EnergyLevel
import com.therecalibrationyears.keel.domain.EnergySummary
import com.therecalibrationyears.keel.domain.MergedActivity
import com.therecalibrationyears.keel.domain.Mood
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything Home reads, as of one moment (port of `DashboardView`'s queries). */
data class HomeData(
    val checkIns: List<CheckInEntity> = emptyList(),
    val loggedSymptoms: List<LoggedSymptom> = emptyList(),
    /** Active medicines, oldest first. */
    val medications: List<MedicationEntity> = emptyList(),
    val medicationLogs: List<MedicationLogEntity> = emptyList(),
    val manualSleep: List<DayAmount> = emptyList(),
    val importedSleep: List<DayAmount> = emptyList(),
    val moodPackId: String? = null,
)

/** One check-in as Home shows it. */
data class HomeEntry(
    val id: String,
    val mood: Mood,
    val at: Instant,
    val energy: EnergyLevel?,
    val symptoms: List<LoggedSymptom>,
    val note: String?,
)

/** One day's bar: null when there's nothing for that day (an empty track, never a value). */
data class DayBar(val day: LocalDate, val value: Double?)

data class MedicineRow(val medication: MedicationEntity, val taken: Boolean)

/**
 * Home's rules, pure (port of the derived values in iOS `DashboardView`), so they're tested
 * without the screen. Days are her local days in [zone].
 */
class HomeModel(private val data: HomeData, val today: LocalDate, private val zone: ZoneId) {

    private fun Instant.day(): LocalDate = atZone(zone).toLocalDate()

    private val symptomsByCheckIn = data.loggedSymptoms.groupBy { it.checkInID }

    /** The last seven days, oldest first, ending today. */
    val last7: List<LocalDate> = CalendarGrid.last7(today)

    /** All of a day's entries, earliest first (the snapshot list). */
    fun entries(day: LocalDate): List<HomeEntry> = data.checkIns
        .filter { it.date.day() == day }
        .sortedBy { it.date }
        .map { HomeEntry(it.id, it.mood, it.date, it.energyLevel, symptomsByCheckIn[it.id].orEmpty(), it.notes?.ifBlank { null }) }

    /** The day's latest entry's mood, for the mood strip and calendar (check-ins are newest first). */
    fun latestMood(day: LocalDate): Mood? = data.checkIns.firstOrNull { it.date.day() == day }?.mood

    /** The day's average energy across the entries where she picked one. */
    private fun averageEnergy(day: LocalDate): Double? =
        data.checkIns.filter { it.date.day() == day && it.energyLevel != null }
            .takeIf { it.isNotEmpty() }?.map { it.energy }?.average()

    val energyBars: List<DayBar> get() = last7.map { DayBar(it, averageEnergy(it)) }

    /** The Energy card summary: the level she picked most, once there are three picks. */
    val energyTrailing: String
        get() = EnergySummary.trailing(
            last7.flatMap { day -> entries(day).mapNotNull { e -> e.energy?.let { EnergySummary.Pick(e.at, it) } } },
        )

    /** Her own sleep entry wins; otherwise the imported value. */
    fun sleepHours(day: LocalDate): Double? = MergedActivity.amount("sleep", day, data.manualSleep, data.importedSleep)

    val sleepBars: List<DayBar> get() = last7.map { DayBar(it, sleepHours(it)) }

    /** Average over the nights that have a value; 0 when none do. */
    val sleepAverage: Double get() = last7.mapNotNull(::sleepHours).takeIf { it.isNotEmpty() }?.average() ?: 0.0

    private val takenKeys: Set<Pair<String, LocalDate>> = data.medicationLogs
        .filter { it.taken && it.medicationID != null }
        .map { it.medicationID!! to it.date.day() }
        .toSet()

    /** Medicines in Home's log for a day: the ones she tracks plus any set to auto-log. */
    fun medicineRows(day: LocalDate): List<MedicineRow> =
        data.medications.filter { it.appearsInHomeLog }.map { MedicineRow(it, (it.id to day) in takenKeys) }

    /** Distinct days in the last 7 with at least one home-log medicine recorded taken. */
    val daysTakenLast7: Int
        get() {
            val tracked = data.medications.filter { it.appearsInHomeLog }.map { it.id }
            return last7.count { day -> tracked.any { (it to day) in takenKeys } }
        }

    /** The footer adds ", including auto-logged" when any active medicine auto-logs (iOS). */
    val anyAutoLogged: Boolean get() = data.medications.any { it.autoLogDoses }

    /** Future days can't hold a check-in: the selector never lands there. */
    fun isFuture(day: LocalDate): Boolean = day > today
}
