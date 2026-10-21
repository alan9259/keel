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
import android.util.Log
import com.therecalibrationyears.keel.domain.GPPeriod
import com.therecalibrationyears.keel.domain.GPSummaryInputs
import com.therecalibrationyears.keel.gpsummary.GPSummaryFonts
import com.therecalibrationyears.keel.gpsummary.GPSummaryPdfRenderer
import java.io.File

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

    /**
     * The iOS `-uitGPSummary` probe: a realistic 12-week picture (same seed as iOS, so the two
     * PDFs can be compared), rendered through the real service and renderer to
     * files/keel-gp-summary.pdf, then logged: path, size, overflow, and the metadata left in.
     * Pull it with `adb shell run-as <package> cat files/keel-gp-summary.pdf > gp.pdf`.
     */
    suspend fun gpSummaryProbe() {
        reset()
        val owner = container.identity.continueLocally()
        val zone = clock.zone()
        val now = clock.now()
        val today = now.atZone(zone).toLocalDate()
        val names = listOf("Trouble sleeping", "Hot flushes", "Anxious", "Brain fog", "Joint pain", "Night sweats", "Irritable", "Low mood")
        val picks = names.map { symptom(it) }
        val moods = listOf(Mood.OKAY, Mood.GOOD, Mood.LOW, Mood.DIFFICULT)
        for (i in 0 until 84 step 2) {
            val day = today.minusDays(i.toLong()).atStartOfDay(zone).toInstant()
            val alcohol = if (i % 6 == 0) (i / 6) % 3 else null // some days, zero included
            val c = CheckInEntity(id(), day, moods[i / 2 % 4].raw, 20 + (i % 5) * 20, alcoholCount = alcohol, ownerID = owner, createdAt = day, updatedAt = day)
            db.checkIns().upsert(c)
            picks.take((i / 2 % 5) + 1).forEach { sym ->
                db.checkIns().upsertLink(CheckInSymptomEntity(id(), c.id, sym.id, severity = (i % 3) + 1, ownerID = owner, createdAt = day, updatedAt = day))
            }
        }
        // Cycle: two period runs and a standalone spotting day.
        fun cycleDay(daysAgo: Long, level: FlowLevel) = CycleEntryEntity(
            id(), today.minusDays(daysAgo).atStartOfDay(zone).toInstant(), CycleEntryType.PERIOD_START.raw, level.raw,
            ownerID = owner, createdAt = now, updatedAt = now,
        )
        listOf(70L, 69L, 68L, 42L, 41L, 40L).forEach { db.cycle().upsert(cycleDay(it, FlowLevel.UNSPECIFIED)) }
        db.cycle().upsert(cycleDay(20, FlowLevel.SPOTTING))
        // Treatment, including a stopped MHT (shows "Stopped [date]") and a stopped supplement
        // (a "stopped X" change but no supplement row).
        fun med(name: String, dose: String, timing: String, kind: String, group: String? = null, active: Boolean = true,
                started: Instant? = null, changed: Instant? = null, stopped: Instant? = null) = MedicationEntity(
            id(), name, dosage = dose, timing = timing, kindRaw = kind, catalogGroupID = group, isActive = active,
            date = started, doseChangedAt = changed, stoppedAt = stopped, ownerID = owner, createdAt = now, updatedAt = now,
        )
        val days = { n: Long -> now.minus(Duration.ofDays(n)) }
        listOf(
            med("Oestrogel", "2 pumps", "Morning", "treatment", "oestrogen", started = days(200), changed = days(14)),
            med("Magnesium glycinate", "400mg", "Night", "supplement"),
            med("Sertraline", "50mg", "Morning", "treatment"),
            med("Estradot patch", "50mcg", "", "treatment", "oestrogen", active = false, stopped = days(21)),
            med("Evening primrose oil", "1000mg", "", "supplement", active = false, stopped = days(30)),
        ).forEach { db.medications().upsert(it) }
        container.users.updateBasicInfo(firstName = "Mischa", lastName = null, birthYear = 1977, mobile = null, email = null)

        val inputs = GPSummaryInputs(
            period = GPPeriod.TwelveWeeks, includeName = true, includeAge = true,
            priorities = listOf("Get on top of the night sweats", "Talk through mood dips", "Sleep"),
            impactAreas = listOf("Sleep", "Work or concentration", "Emotional wellbeing"), impactOverall = "Significant",
            questions = listOf("Should I adjust my MHT dose?", "Is a blood test worth doing?"),
        )
        val document = container.controller.gpSummaryActions.document(inputs)
        val renderer = GPSummaryPdfRenderer(document, GPSummaryFonts.load(container.appContext))
        val bytes = renderer.render()
        val file = File(container.appContext.filesDir, "keel-gp-summary.pdf").apply { writeBytes(bytes) }
        val text = String(bytes, Charsets.ISO_8859_1)
        fun value(key: String) = Regex("$key\\s*(\\([^)]*\\)|<[^>]*>)").find(text)?.groupValues?.get(1) ?: "absent"
        Log.i("KeelDebug", "KEEL_GPSUMMARY path=${file.path} bytes=${bytes.size} pageOneOverflow=${renderer.pageOneOverflowed} pageTwoOverflow=${renderer.pageTwoOverflowed}")
        Log.i("KeelDebug", "KEEL_GPSUMMARY producer='${value("/Producer")}' creator='${value("/Creator")}' author='${value("/Author")}' title='${value("/Title")}' created='${value("/CreationDate")}'")
        Log.i("KeelDebug", "KEEL_GPSUMMARY alcoholLine='${document.alcoholLine}' includeAlcohol=${document.includeAlcohol} name='${document.name}' age=${document.age}")
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
