package com.therecalibrationyears.keel.domain

import com.therecalibrationyears.keel.domain.parity.roundHalfAwayFromZero
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What Health Connect gave Keel, in one plain shape (port of iOS `HealthSnapshot`), so the
 * import and its tests never touch Health Connect types. Days are her local days.
 */
data class HealthSnapshot(
    /** Hours asleep, keyed by the day the night's sleep ended. */
    val sleepByDay: Map<LocalDate, Double> = emptyMap(),
    /** Daily amounts for Keel's activity log, by activity id ("steps", "exercise") then day. */
    val activityAmounts: Map<String, Map<LocalDate, Double>> = emptyMap(),
    /** Readings with no Keel home of their own (vitals, energy, distance, flights). */
    val vitals: List<VitalSeries> = emptyList(),
    /** Period days, at the heaviness recorded. */
    val menstrualFlow: Map<LocalDate, FlowLevel> = emptyMap(),
    /**
     * The first day [menstrualFlow] covers. When set, imported period days from then on that
     * Health Connect no longer has are removed. Null means "don't reconcile": periods are off,
     * or Keel can't read them yet, so an empty read can't be taken to mean none.
     */
    val flowWindowStart: LocalDate? = null,
) {
    data class VitalSeries(val typeId: String, val unit: String, val byDay: Map<LocalDate, Double>)

    /** Nothing at all came back (not shared, or access not through yet): the sync then keeps trying. */
    val isEmpty: Boolean
        get() = sleepByDay.isEmpty() && activityAmounts.values.all { it.isEmpty() } && vitals.all { it.byDay.isEmpty() } &&
            menstrualFlow.isEmpty()
}

/** Imported values keep one decimal place, rounded as Swift's `.rounded()` (half away from zero). */
fun oneDecimal(value: Double): Double = roundHalfAwayFromZero(value * 10) / 10

/**
 * Sleep from Health Connect's sessions (port of the iOS sleep read, plan §3.3). Hours actually
 * asleep per night: the union of the night's intervals, never the sum of their durations
 * (overlapping stages and a second app covering the same night would double count).
 */
object HealthSleep {
    /** A stage within a session: asleep (light, deep, REM, plain sleeping), in bed, or anything else (awake). */
    enum class Stage { ASLEEP, IN_BED, OTHER }

    data class Segment(val start: Instant, val end: Instant, val stage: Stage)
    data class Session(val start: Instant, val end: Instant, val stages: List<Segment> = emptyList())

    /**
     * Hours asleep per day. Each session's time counts toward the day its session ended, in
     * her time zone (plan D8: a stage that ends after midnight still belongs to that night):
     *  - no stages: the whole session is sleep;
     *  - with asleep stages: only those;
     *  - stages but none asleep: the session's span, as in-bed time.
     * In-bed time stands in only for a day with no asleep time at all (as iOS).
     */
    fun hoursByDay(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Double> {
        val asleep = mutableMapOf<LocalDate, MutableList<Pair<Instant, Instant>>>()
        val inBed = mutableMapOf<LocalDate, MutableList<Pair<Instant, Instant>>>()
        for (session in sessions) {
            if (!session.end.isAfter(session.start)) continue
            val day = session.end.atZone(zone).toLocalDate()
            val sleeping = session.stages.filter { it.stage == Stage.ASLEEP && it.end.isAfter(it.start) }
            when {
                session.stages.isEmpty() -> asleep.getOrPut(day) { mutableListOf() } += session.start to session.end
                sleeping.isNotEmpty() -> asleep.getOrPut(day) { mutableListOf() } += sleeping.map { it.start to it.end }
                else -> inBed.getOrPut(day) { mutableListOf() } += session.start to session.end
            }
        }
        val out = mutableMapOf<LocalDate, Double>()
        inBed.forEach { (day, ranges) -> out[day] = mergedHours(ranges) }
        asleep.forEach { (day, ranges) -> out[day] = mergedHours(ranges) }
        return out
    }

    /** Total hours covered by the union of [ranges]: overlapping time counts once. */
    fun mergedHours(ranges: List<Pair<Instant, Instant>>): Double {
        var total = Duration.ZERO
        var open: Pair<Instant, Instant>? = null
        for (range in ranges.sortedBy { it.first }) {
            val current = open
            if (current != null && !range.first.isAfter(current.second)) {
                if (range.second.isAfter(current.second)) open = current.first to range.second
            } else {
                if (current != null) total += Duration.between(current.first, current.second)
                open = range
            }
        }
        open?.let { total += Duration.between(it.first, it.second) }
        return total.toMillis() / 3_600_000.0
    }
}

/** Period days from Health Connect (port of iOS `flowByDay`, plan §3.4). */
object HealthFlow {
    /**
     * One level per day, the heaviest winning where a day has several (the first on a tie, as
     * iOS). Days a period record covers without any flow record count as period days with no
     * level given (plan D5). Spotting is never imported: Health Connect has no such level.
     */
    fun byDay(flow: List<Pair<LocalDate, FlowLevel>>, periods: List<ClosedRange<LocalDate>> = emptyList()): Map<LocalDate, FlowLevel> {
        val out = linkedMapOf<LocalDate, FlowLevel>()
        for ((day, level) in flow) {
            val existing = out[day]
            if (existing != null && existing.intensity >= level.intensity) continue
            out[day] = level
        }
        for (span in periods) {
            var day = span.start
            while (day <= span.endInclusive) {
                out.putIfAbsent(day, FlowLevel.UNSPECIFIED)
                day = day.plusDays(1)
            }
        }
        return out
    }

    /**
     * Where an empty read can be trusted to mean "no period days" (plan §3.4): a year back with
     * the history permission; without it Health Connect only shares from 30 days before Keel
     * was first allowed to read periods, so no earlier than that.
     */
    fun windowStart(today: LocalDate, historyGranted: Boolean, firstMenstruationGrant: LocalDate?): LocalDate {
        val yearBack = today.minusDays(HealthSync.IMPORT_DAYS.toLong())
        if (historyGranted || firstMenstruationGrant == null) return yearBack
        return maxOf(yearBack, firstMenstruationGrant.minusDays(30))
    }
}

/**
 * The Health Connect items Keel can import, each switchable on its own (port of iOS
 * `HealthSyncCatalog`): the single source for the Health Connect screen's switches and for
 * dropping switched-off items from a fresh read. Ids are iOS's (`keel.disabledHealthItems`).
 */
object HealthSyncCatalog {
    data class Item(
        val id: String,
        val label: String,
        val desc: String,
        val activityIds: List<String> = emptyList(),
        val vitalTypeIds: List<String> = emptyList(),
        val includesFlow: Boolean = false,
    )

    /** Movement first, then rest, then vitals (the iOS order). */
    val all = listOf(
        Item("steps", "Steps", "Daily step count", activityIds = listOf("steps")),
        Item("exercise", "Exercise minutes", "Time in exercise sessions", activityIds = listOf("exercise")),
        Item("activeEnergy", "Active energy", "Active calories burned", vitalTypeIds = listOf("activeEnergy")),
        Item("distance", "Distance", "Distance covered", vitalTypeIds = listOf("distance")),
        Item("flights", "Flights climbed", "Flights of stairs", vitalTypeIds = listOf("flights")),
        Item("sleep", "Sleep", "Hours actually asleep each night", activityIds = listOf("sleep")),
        Item(
            "heartVitals", "Heart & vitals", "Heart rate, resting HR, HRV, respiratory rate, blood oxygen, blood pressure",
            vitalTypeIds = listOf(
                "heartRate", "restingHeartRate", "hrvRmssd", "respiratoryRate", "oxygenSaturation",
                "bloodPressureSystolic", "bloodPressureDiastolic",
            ),
        ),
        Item(
            "bodyTemperature", "Body temperature", "Body temperature and overnight skin temperature",
            vitalTypeIds = listOf("bodyTemperature", "wristTemperature"),
        ),
        Item("bodyWeight", "Body weight", "Weight over time", vitalTypeIds = listOf("bodyMass")),
        Item("periods", "Periods", "Menstrual flow, shown on your Cycle screen", includesFlow = true),
    )

    /**
     * A fresh read without the items she has switched off, so nothing new comes in for them
     * (what was imported earlier stays until she removes it).
     */
    fun filter(snapshot: HealthSnapshot, disabled: Set<String>): HealthSnapshot {
        if (disabled.isEmpty()) return snapshot
        val off = all.filter { it.id in disabled }
        val offActivity = off.flatMap { it.activityIds }.toSet()
        val offVitals = off.flatMap { it.vitalTypeIds }.toSet()
        val periodsOff = off.any { it.includesFlow }
        return snapshot.copy(
            sleepByDay = if ("sleep" in offActivity) emptyMap() else snapshot.sleepByDay,
            activityAmounts = snapshot.activityAmounts - offActivity,
            vitals = snapshot.vitals.filterNot { it.typeId in offVitals },
            menstrualFlow = if (periodsOff) emptyMap() else snapshot.menstrualFlow,
            flowWindowStart = if (periodsOff) null else snapshot.flowWindowStart,
        )
    }
}

/**
 * Whether asking would show Health Connect's permission screen (iOS `HealthRequestStatus`):
 * the "has she been asked about everything Keel reads?" meaning, never whether she agreed.
 */
enum class HealthRequestStatus { SHOULD_REQUEST, UNNECESSARY, UNKNOWN }

/** What to offer about Health Connect when she opens Keel (iOS `HealthOffer`). */
enum class HealthOffer { NONE, CONNECT, RECONNECT }

/** The sync and offer decisions (port of the pure parts of iOS `AppEnvironment`). */
object HealthSync {
    /** A year back: Activities and Cycle show a year. */
    const val IMPORT_DAYS = 365
    val MIN_INTERVAL: Duration = Duration.ofMinutes(30)

    /**
     * A sync runs only once she has connected (so nothing asks before her Connect tap); a
     * forced one (Sync now, a switch turned back on, connecting) skips the 30-minute wait.
     */
    fun shouldRun(now: Instant, lastSyncedAt: Instant?, force: Boolean, connected: Boolean, minInterval: Duration = MIN_INTERVAL): Boolean {
        if (!connected) return false
        if (force || lastSyncedAt == null) return true
        return Duration.between(lastSyncedAt, now) >= minInterval
    }

    /**
     * Only after onboarding (which has its own step) and only when Health Connect would really
     * ask. Each offer once: Connect until she answers it, Update until she answers it for the
     * current set of reads.
     */
    fun offer(status: HealthRequestStatus, hasOnboarded: Boolean, connected: Boolean, connectAnswered: Boolean, reconnectAnswered: Boolean): HealthOffer {
        if (!hasOnboarded || status != HealthRequestStatus.SHOULD_REQUEST) return HealthOffer.NONE
        if (connected) return if (reconnectAnswered) HealthOffer.NONE else HealthOffer.RECONNECT
        return if (connectAnswered) HealthOffer.NONE else HealthOffer.CONNECT
    }

    /**
     * The status on Android (plan §3.6): Health Connect gives no "would it ask" answer, so Keel
     * keeps the set of permissions it has asked for. Not available here: unknown. Asked about
     * everything it reads now: unnecessary. A read added in a later version: should request.
     */
    fun requestStatus(available: Boolean, asked: Set<String>, requestable: Set<String>): HealthRequestStatus = when {
        !available -> HealthRequestStatus.UNKNOWN
        asked.containsAll(requestable) -> HealthRequestStatus.UNNECESSARY
        else -> HealthRequestStatus.SHOULD_REQUEST
    }
}

/** Whether Health Connect can be used on this phone (plan §3.5). */
enum class HealthAvailability {
    AVAILABLE,
    /** Android 9 to 13 without the Health Connect app, or an outdated one: Play can install it. */
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

/**
 * What Keel needs from Health Connect (port of iOS `HealthDataSource`), behind an interface so
 * the connect and sync rules are tested with a fake. Asking for permission happens on screen
 * (Health Connect's own page); this side reports what it can and what was granted.
 */
interface HealthDataSource {
    suspend fun availability(): HealthAvailability
    /** Every permission Keel would ask for on this phone (features it lacks left out). */
    suspend fun requestablePermissions(): Set<String>
    suspend fun grantedPermissions(): Set<String>
    /** Whether the history permission is granted (a year back, rather than 30 days before the first grant). */
    suspend fun readsHistory(): Boolean
    /** Everything Keel reads, over the last [lastDays], from what she has allowed. */
    suspend fun snapshot(lastDays: Int, flowWindowStart: LocalDate?): HealthSnapshot
    /** Disconnect (plan D4): Keel gives back every permission. */
    suspend fun revokeAll()
}
