package com.therecalibrationyears.keel

import com.therecalibrationyears.keel.data.db.ActivityLogEntity
import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.CheckInSymptomEntity
import com.therecalibrationyears.keel.data.db.CycleEntryEntity
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.MedicationLogEntity
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.domain.CycleEntryType
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.Mood
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Debug-only sample records for screenshots, mirroring the iOS harness seeds
 * (`-uitReset`, `-uitSeedCheckIn`, `-uitSeedMed`, `-uitSeedEnergyMix`). Exempt from the
 * no-fake-data rule because it never ships (src/debug).
 */
internal class DebugSeeds(private val container: AppContainer) {
    private val db = container.database
    private val clock = container.clock
    private fun id() = UUID.randomUUID().toString().uppercase()

    suspend fun reset() {
        val now = clock.now()
        db.checkIns().observeLive().first().forEach { db.checkIns().upsert(it.copy(deletedAt = now, updatedAt = now)) }
        // Removed from her list (iOS archives them), so nothing lingers under "No longer taking".
        db.medications().live().forEach { db.medications().upsert(it.copy(isActive = false, deletedAt = now, updatedAt = now)) }
        db.cycle().observeLive().first().forEach { db.cycle().upsert(it.copy(deletedAt = now, updatedAt = now)) }
    }

    /**
     * Regular-ish recent cycles (28, 29, 27 days) and a current period with varying flow
     * (iOS -uitSeedCycle), so the timeline shows a cycle in progress and an estimate.
     */
    suspend fun cycle() {
        val owner = container.identity.continueLocally()
        val now = clock.now()
        val today = now.atZone(clock.zone()).toLocalDate()
        fun entry(offset: Long, level: FlowLevel) = CycleEntryEntity(
            id(), today.plusDays(offset).atStartOfDay(clock.zone()).toInstant(), CycleEntryType.PERIOD_START.raw, level.raw,
            ownerID = owner, createdAt = now, updatedAt = now,
        )
        listOf(-84L, -56L, -27L).forEach { db.cycle().upsert(entry(it, FlowLevel.MEDIUM)) }
        listOf(FlowLevel.HEAVY, FlowLevel.MEDIUM, FlowLevel.LIGHT, FlowLevel.SPOTTING)
            .forEachIndexed { i, level -> db.cycle().upsert(entry(-3L + i, level)) }
    }

    /** One representative check-in (iOS -uitSeedCheckIn). */
    suspend fun checkIn() {
        val owner = container.identity.continueLocally()
        val now = clock.now()
        val c = CheckInEntity(id(), now, Mood.OKAY.raw, 62, "Slept poorly, a bit foggy today.", ownerID = owner, createdAt = now, updatedAt = now)
        db.checkIns().upsert(c)
        listOf("Brain fog" to 2, "Hot flushes" to 3, "Anxious" to 1).forEach { (name, severity) ->
            val symptom = symptom(name)
            db.checkIns().upsertLink(CheckInSymptomEntity(id(), c.id, symptom.id, severity, ownerID = owner, createdAt = now, updatedAt = now))
        }
    }

    /** Two supplements (iOS -uitSeedMed). */
    suspend fun medications() {
        val owner = container.identity.continueLocally()
        val now = clock.now()
        listOf("Magnesium" to "400mg", "Vitamin D" to "2000 IU").forEachIndexed { i, (name, dose) ->
            db.medications().upsert(
                MedicationEntity(id(), name, dosage = dose, timing = "8:00 am", ownerID = owner,
                    createdAt = now.plusMillis(i.toLong()), updatedAt = now),
            )
        }
    }

    /** Drained, Drained, Charged this week (iOS -uitSeedEnergyMix: the Home Energy label check). */
    suspend fun energyMix() {
        val owner = container.identity.continueLocally()
        val now = clock.now()
        listOf(1L to 20, 30L to 20, 60L to 100).forEach { (hoursAgo, energy) ->
            val at = now.minus(Duration.ofHours(hoursAgo))
            db.checkIns().upsert(CheckInEntity(id(), at, Mood.OKAY.raw, energy, ownerID = owner, createdAt = at, updatedAt = at))
        }
    }

    /** Android-only: a week of entries, sleep and ticks, to see every strip filled. */
    suspend fun week() {
        val owner = container.identity.continueLocally()
        val zone = clock.zone()
        val today = clock.now().atZone(zone).toLocalDate()
        val moods = listOf(Mood.LOW, Mood.OKAY, Mood.GOOD, Mood.DIFFICULT, Mood.OKAY, Mood.GREAT, Mood.GOOD)
        val energies = listOf(40, 60, 80, 20, 0, 100, 60)
        val sleep = listOf(6.5, 7.0, 8.0, 5.5, 7.5, 0.0, 7.25)
        val meds = db.medications().observeActive().first()
        (0..6).forEach { i ->
            val day = today.minusDays(6L - i)
            val at = day.atTime(LocalTime.of(9, 15)).atZone(zone).toInstant().coerceAtMost(clock.now())
            db.checkIns().upsert(CheckInEntity(id(), at, moods[i].raw, energies[i], ownerID = owner, createdAt = at, updatedAt = at))
            val midnight = day.atStartOfDay(zone).toInstant()
            if (sleep[i] > 0) {
                // Seeding again on a later day replaces that night rather than adding a second row.
                val existing = db.activityLogs().liveOn("sleep", midnight)
                db.activityLogs().upsert(ActivityLogEntity(existing?.id ?: id(), midnight, "sleep", sleep[i], ownerID = owner, createdAt = midnight, updatedAt = midnight))
            }
            if (i % 2 == 0) meds.firstOrNull()?.let {
                db.medications().upsertLog(MedicationLogEntity(id(), midnight, null, true, it.id, ownerID = owner, createdAt = midnight, updatedAt = midnight))
            }
        }
    }

    private suspend fun symptom(name: String): SymptomEntity =
        db.symptoms().allLive().firstOrNull { it.name == name }
            ?: clock.now().let { now ->
                SymptomEntity(id(), name, "body", ownerID = "", createdAt = now, updatedAt = now).also { db.symptoms().upsert(it) }
            }

    /** A medicine due in 2 minutes and the daily check-in in 3, each laid out as she'd set them. */
    suspend fun remindSoon() {
        val now = clock.now().atZone(clock.zone())
        val dose = now.plusMinutes(2)
        container.controller.medicationsActions.add(
            com.therecalibrationyears.keel.domain.TreatmentDraft(
                name = "Magnesium", kind = com.therecalibrationyears.keel.domain.TreatmentKind.SUPPLEMENT,
                schedule = com.therecalibrationyears.keel.domain.DoseSchedule(
                    slots = listOf(com.therecalibrationyears.keel.domain.DoseSlot(hour = dose.hour, minute = dose.minute)),
                ),
            ),
        )
        val checkIn = now.plusMinutes(3)
        val config = container.settings.reminderConfig.first()
        container.settings.setReminderConfig(config.copy(checkInHour = checkIn.hour, checkInMinute = checkIn.minute))
        container.controller.reminders.rescheduleLifestyleReminder(com.therecalibrationyears.keel.domain.ReminderKinds.DAILY_CHECK_IN)
    }
}
