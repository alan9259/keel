package com.therecalibrationyears.keel.data.backup

import com.therecalibrationyears.keel.data.db.ActivityLogEntity
import com.therecalibrationyears.keel.data.db.CheckInEntity
import com.therecalibrationyears.keel.data.db.CheckInSymptomEntity
import com.therecalibrationyears.keel.data.db.CycleEntryEntity
import com.therecalibrationyears.keel.data.db.HealthSampleEntity
import com.therecalibrationyears.keel.data.db.InsightEntity
import com.therecalibrationyears.keel.data.db.MedicationEntity
import com.therecalibrationyears.keel.data.db.MedicationLogEntity
import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.data.db.UserProfileEntity
import com.therecalibrationyears.keel.domain.CycleEntryType
import com.therecalibrationyears.keel.domain.DoseUnit
import com.therecalibrationyears.keel.domain.FlowLevel
import com.therecalibrationyears.keel.domain.InsightAccent
import com.therecalibrationyears.keel.domain.MedicationMethod
import com.therecalibrationyears.keel.domain.Mood
import com.therecalibrationyears.keel.domain.Pathway
import com.therecalibrationyears.keel.domain.SymptomCatalog
import com.therecalibrationyears.keel.domain.SyncStatus
import com.therecalibrationyears.keel.domain.TreatmentKind
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** The file isn't a Keel backup Keel can read (bad JSON, or a required field missing). */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A Keel backup archive (port of iOS `KeelBackup`, Keel/Services/Backup/KeelBackup.swift): every
 * record as a flat object, enums as their raw strings, links as ids. One format for both apps:
 * an iPhone backup restores here and this one restores there.
 *
 * Check-in symptom links keep their ids as written: a link whose check-in or symptom is missing
 * (an empty id) is skipped on restore, as iOS does.
 */
data class KeelBackup(
    val version: Int,
    val exportedAt: Instant,
    val appVersion: String,
    val profiles: List<UserProfileEntity>,
    val symptoms: List<SymptomEntity>,
    val checkIns: List<CheckInEntity>,
    val checkInSymptoms: List<CheckInSymptomEntity>,
    val cycleEntries: List<CycleEntryEntity>,
    val medications: List<MedicationEntity>,
    val medicationLogs: List<MedicationLogEntity>,
    val insights: List<InsightEntity>,
    val activityLogs: List<ActivityLogEntity>,
    val healthSamples: List<HealthSampleEntity>,
)

/**
 * Reads and writes the archive's JSON exactly as iOS does where it matters to the other app:
 * the same keys, dates in whole seconds (`2026-10-22T00:00:00Z`, as Swift's `.iso8601`, which
 * rejects fractions), ids in upper case, an absent key for an empty optional, keys sorted.
 * Reading is lenient: older archives' missing fields fall back to the iOS defaults.
 */
object BackupCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun encode(backup: KeelBackup): String = pretty.encodeToString(JsonElement.serializer(), sorted(toJson(backup)))

    /** [now] stands in for timestamps an older archive lacks (iOS uses the time of the restore). */
    fun decode(text: String, zone: ZoneId, now: Instant): KeelBackup {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BackupFormatException("not JSON", e) // not-copy: diagnostic
        }
        return try {
            fromJson(R(root), zone, now.truncatedTo(ChronoUnit.SECONDS))
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("unreadable record", e) // not-copy: diagnostic
        }
    }

    // MARK: Writing

    private fun toJson(b: KeelBackup) = obj {
        int("version", b.version)
        date("exportedAt", b.exportedAt)
        str("appVersion", b.appVersion)
        list("profiles", b.profiles) { profile(it) }
        list("symptoms", b.symptoms) { symptom(it) }
        list("checkIns", b.checkIns) { checkIn(it) }
        list("checkInSymptoms", b.checkInSymptoms) { link(it) }
        list("cycleEntries", b.cycleEntries) { cycle(it) }
        list("medications", b.medications) { medication(it) }
        list("medicationLogs", b.medicationLogs) { medicationLog(it) }
        list("insights", b.insights) { insight(it) }
        list("activityLogs", b.activityLogs) { activity(it) }
        // Companion chat and daily reflections (generated text) are no longer kept; iOS still
        // expects the keys.
        list("chatMessages", emptyList<Unit>()) { obj {} }
        list("dailySummaries", emptyList<Unit>()) { obj {} }
        list("healthSamples", b.healthSamples) { health(it) }
    }

    private fun W.envelope(ownerID: String, createdAt: Instant, updatedAt: Instant, deletedAt: Instant?, syncStatusRaw: String) {
        str("ownerID", ownerID); date("createdAt", createdAt); date("updatedAt", updatedAt)
        date("deletedAt", deletedAt); str("syncStatusRaw", syncStatusRaw)
    }

    private fun profile(m: UserProfileEntity) = obj {
        str("id", m.id); str("firstName", m.firstName); str("email", m.email); str("appleUserID", m.appleUserID)
        str("pathwayRaw", m.pathwayRaw); bool("healthKitAuthorized", m.healthConnected); date("trackingStartDate", m.trackingStartDate)
        str("lastName", m.lastName); int("birthYear", m.birthYear); str("mobile", m.mobile)
        str("periodsNotApplicableReason", m.periodsNotApplicableReason); str("hysterectomyRaw", m.hysterectomyRaw)
        int("hysterectomyYear", m.hysterectomyYear); str("region", m.region); str("localeID", m.localeID)
        str("timeZoneID", m.timeZoneID); str("appVersion", m.appVersion); str("deviceModel", m.deviceModel)
        str("osVersion", m.osVersion)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun symptom(m: SymptomEntity) = obj {
        str("id", m.id); str("name", m.name); str("categoryRaw", m.categoryRaw); bool("isCustom", m.isCustom)
        bool("isArchived", m.isArchived); bool("isDefaultChip", m.isDefaultChip)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun checkIn(m: CheckInEntity) = obj {
        str("id", m.id); date("date", m.date); str("moodRaw", m.moodRaw); int("energy", m.energy)
        str("notes", m.notes); int("alcoholCount", m.alcoholCount)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun link(m: CheckInSymptomEntity) = obj {
        str("id", m.id); str("checkInID", m.checkInID.ifEmpty { null }); str("symptomID", m.symptomID)
        int("severity", m.severity); str("sourceRaw", m.sourceRaw)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun cycle(m: CycleEntryEntity) = obj {
        str("id", m.id); date("date", m.date); str("typeRaw", m.typeRaw); str("flowLevelRaw", m.flowLevelRaw)
        str("sourceRaw", m.sourceRaw)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun medication(m: MedicationEntity) = obj {
        str("id", m.id); str("name", m.name); str("dosage", m.dosage); str("timing", m.timing)
        str("methodRaw", m.methodRaw); bool("isActive", m.isActive); double("doseAmount", m.doseAmount)
        str("doseUnitRaw", m.doseUnitRaw); str("timeOfDayRaw", m.timeOfDayRaw); str("kindRaw", m.kindRaw)
        str("catalogGroupID", m.catalogGroupID); str("scheduleKindRaw", m.scheduleKindRaw)
        int("weekdaysMask", m.weekdaysMask); int("cycleLength", m.cycleLength); int("cyclePauseDays", m.cyclePauseDays)
        date("cycleAnchor", m.cycleAnchor); date("doseTime", m.doseTime); str("doseTimesJSON", m.doseTimesJSON)
        str("frequencyRaw", m.frequencyRaw); date("date", m.date); date("doseChangedAt", m.doseChangedAt)
        str("note", m.note); bool("isOffLabel", m.isOffLabel); bool("isCompounded", m.isCompounded)
        // Not in the iOS archive yet (plan D1): extra keys, which iOS ignores, so nothing is lost here.
        bool("isTracked", m.isTracked); bool("autoLogDoses", m.autoLogDoses); date("stoppedAt", m.stoppedAt)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun medicationLog(m: MedicationLogEntity) = obj {
        str("id", m.id); date("date", m.date); str("slot", m.slot); bool("taken", m.taken); str("medicationID", m.medicationID)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun insight(m: InsightEntity) = obj {
        str("id", m.id); str("title", m.title); str("detail", m.detail); str("timeframe", m.timeframe)
        str("iconKey", m.iconKey); str("accentRaw", m.accentRaw); date("generatedAt", m.generatedAt)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun activity(m: ActivityLogEntity) = obj {
        str("id", m.id); date("date", m.date); str("activityID", m.activityID); double("amount", m.amount)
        str("sourceRaw", m.sourceRaw)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    private fun health(m: HealthSampleEntity) = obj {
        str("id", m.id); str("typeID", m.typeID); date("day", m.day); double("value", m.value); str("unit", m.unit)
        str("sourceRaw", m.sourceRaw)
        envelope(m.ownerID, m.createdAt, m.updatedAt, m.deletedAt, m.syncStatusRaw)
    }

    // MARK: Reading (the iOS DTOs' `model()` fallbacks)

    private const val MANUAL = "manual" // not-copy: DataSource raw value
    private const val IMPORTED = "healthKit" // not-copy: DataSource raw value
    private val sources = setOf(MANUAL, IMPORTED)

    private fun fromJson(r: R, zone: ZoneId, now: Instant) = KeelBackup(
        version = r.int("version"),
        exportedAt = r.date("exportedAt"),
        appVersion = r.strOrNull("appVersion").orEmpty(),
        profiles = r.list("profiles") { readProfile(it) },
        symptoms = r.list("symptoms") { readSymptom(it) },
        checkIns = r.list("checkIns") { readCheckIn(it) },
        checkInSymptoms = r.list("checkInSymptoms") { readLink(it) },
        cycleEntries = r.list("cycleEntries") { readCycle(it) },
        medications = r.list("medications") { readMedication(it) },
        medicationLogs = r.list("medicationLogs") { readMedicationLog(it) },
        insights = r.list("insights") { readInsight(it) },
        activityLogs = r.list("activityLogs") { readActivity(it, now) },
        // Older archives may carry chatMessages and dailySummaries; neither is restored.
        healthSamples = r.list("healthSamples") { readHealth(it, zone) },
    )

    private fun readProfile(r: R) = UserProfileEntity(
        id = r.id("id"), firstName = r.str("firstName"), email = r.strOrNull("email"), appleUserID = r.strOrNull("appleUserID"),
        pathwayRaw = r.strOrNull("pathwayRaw")?.takeIf { Pathway.fromRaw(it) != null },
        healthConnected = r.bool("healthKitAuthorized"), trackingStartDate = r.date("trackingStartDate"),
        lastName = r.strOrNull("lastName"), birthYear = r.intOrNull("birthYear"), mobile = r.strOrNull("mobile"),
        periodsNotApplicableReason = r.strOrNull("periodsNotApplicableReason"), hysterectomyRaw = r.strOrNull("hysterectomyRaw"),
        hysterectomyYear = r.intOrNull("hysterectomyYear"), region = r.strOrNull("region"), localeID = r.strOrNull("localeID"),
        timeZoneID = r.strOrNull("timeZoneID"), appVersion = r.strOrNull("appVersion"), deviceModel = r.strOrNull("deviceModel"),
        osVersion = r.strOrNull("osVersion"),
        ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
        deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
    )

    private fun readSymptom(r: R): SymptomEntity {
        val name = r.str("name")
        return SymptomEntity(
            id = r.id("id"), name = name,
            categoryRaw = r.str("categoryRaw"), // custom categories verbatim
            isCustom = r.bool("isCustom"), isArchived = r.bool("isArchived"),
            // Absent in archives written before the default chips existed.
            isDefaultChip = r.boolOrNull("isDefaultChip") ?: (name in SymptomCatalog.defaultOrder),
            ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
            deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
        )
    }

    private fun readCheckIn(r: R) = CheckInEntity(
        id = r.id("id"), date = r.date("date"), moodRaw = Mood.fromRaw(r.str("moodRaw")).raw, energy = r.int("energy"),
        notes = r.strOrNull("notes"), alcoholCount = r.intOrNull("alcoholCount"),
        ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
        deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
    )

    private fun readLink(r: R) = CheckInSymptomEntity(
        id = r.id("id"), checkInID = r.idOrNull("checkInID").orEmpty(), symptomID = r.idOrNull("symptomID"),
        severity = r.intOrNull("severity") ?: 1, // older archives, before severity
        sourceRaw = r.strOrNull("sourceRaw")?.takeIf { it in sources } ?: MANUAL,
        ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
        deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
    )

    private fun readCycle(r: R) = CycleEntryEntity(
        id = r.id("id"), date = r.date("date"), typeRaw = CycleEntryType.fromRaw(r.str("typeRaw")).raw,
        flowLevelRaw = r.strOrNull("flowLevelRaw")?.takeIf { raw -> FlowLevel.entries.any { it.raw == raw } },
        sourceRaw = r.strOrNull("sourceRaw")?.takeIf { it in sources } ?: MANUAL,
        ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
        deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
    )

    private fun readMedication(r: R): MedicationEntity {
        val scheduleKind = r.strOrNull("scheduleKindRaw")
        val base = MedicationEntity(
            id = r.id("id"), name = r.str("name"), dosage = r.str("dosage"), timing = r.str("timing"),
            methodRaw = r.strOrNull("methodRaw")?.takeIf { MedicationMethod.fromRaw(it) != null },
            isActive = r.bool("isActive"),
            doseAmount = r.doubleOrNull("doseAmount"),
            doseUnitRaw = r.strOrNull("doseUnitRaw")?.takeIf { DoseUnit.fromRaw(it) != null },
            timeOfDayRaw = r.strOrNull("timeOfDayRaw"),
            kindRaw = TreatmentKind.fromRaw(r.strOrNull("kindRaw")).raw,
            catalogGroupID = r.strOrNull("catalogGroupID"),
            frequencyRaw = r.strOrNull("frequencyRaw"),
            // Older archives called it `startDate`.
            date = r.dateOrNull("date") ?: r.dateOrNull("startDate"),
            doseChangedAt = r.dateOrNull("doseChangedAt"), note = r.strOrNull("note"),
            isOffLabel = r.boolOrNull("isOffLabel") ?: false, isCompounded = r.boolOrNull("isCompounded") ?: false,
            // Android's extra keys (D1); an iPhone backup has none, so the iOS defaults apply.
            isTracked = r.boolOrNull("isTracked") ?: true, autoLogDoses = r.boolOrNull("autoLogDoses") ?: false,
            stoppedAt = r.dateOrNull("stoppedAt"),
            ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
            deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
        )
        // The schedule columns count only alongside a schedule kind (an older archive without
        // one migrates from `frequencyRaw` at launch).
        if (scheduleKind == null) return base
        return base.copy(
            scheduleKindRaw = scheduleKind,
            weekdaysMask = r.intOrNull("weekdaysMask") ?: 0,
            cycleLength = r.intOrNull("cycleLength") ?: 28,
            cyclePauseDays = r.intOrNull("cyclePauseDays") ?: 7,
            cycleAnchor = r.dateOrNull("cycleAnchor"),
            doseTime = r.dateOrNull("doseTime"),
            doseTimesJSON = r.strOrNull("doseTimesJSON"),
        )
    }

    private fun readMedicationLog(r: R) = MedicationLogEntity(
        id = r.id("id"), date = r.date("date"), slot = r.strOrNull("slot")?.uppercase(), taken = r.bool("taken"),
        medicationID = r.idOrNull("medicationID"),
        ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
        deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
    )

    private fun readInsight(r: R) = InsightEntity(
        id = r.id("id"), title = r.str("title"), detail = r.str("detail"), timeframe = r.str("timeframe"),
        iconKey = r.str("iconKey"), accentRaw = InsightAccent.fromRaw(r.str("accentRaw")).raw, generatedAt = r.date("generatedAt"),
        ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
        deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
    )

    /** Activity logs predate the sync envelope, so every envelope field may be missing. */
    private fun readActivity(r: R, now: Instant): ActivityLogEntity {
        return ActivityLogEntity(
            id = r.id("id"), date = r.date("date"), activityID = r.str("activityID"), amount = r.double("amount"),
            sourceRaw = r.strOrNull("sourceRaw")?.takeIf { it in sources } ?: MANUAL,
            ownerID = r.strOrNull("ownerID").orEmpty(), createdAt = r.dateOrNull("createdAt") ?: now,
            updatedAt = r.dateOrNull("updatedAt") ?: now, deletedAt = r.dateOrNull("deletedAt"),
            syncStatusRaw = r.strOrNull("syncStatusRaw") ?: SyncStatus.PENDING_UPLOAD.raw,
        )
    }

    /** As iOS's `HealthSample` init: the day is the start of that day where she is now. */
    private fun readHealth(r: R, zone: ZoneId) = HealthSampleEntity(
        id = r.id("id"), typeID = r.str("typeID"),
        day = r.date("day").atZone(zone).toLocalDate().atStartOfDay(zone).toInstant(),
        value = r.double("value"), unit = r.str("unit"),
        sourceRaw = r.str("sourceRaw").takeIf { it in sources } ?: IMPORTED,
        ownerID = r.str("ownerID"), createdAt = r.date("createdAt"), updatedAt = r.date("updatedAt"),
        deletedAt = r.dateOrNull("deletedAt"), syncStatusRaw = r.str("syncStatusRaw"),
    )

    // MARK: JSON helpers

    /** Swift's `.iso8601`: whole seconds, UTC, `Z`. */
    internal fun formatDate(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS))

    /** Lenient: fractions and offsets are accepted, whatever wrote the file. */
    internal fun parseDate(text: String): Instant =
        runCatching { Instant.parse(text) }.getOrElse { OffsetDateTime.parse(text).toInstant() }

    /** An object builder that leaves out empty optionals, as Swift's synthesised `Codable` does. */
    private class W {
        val map = LinkedHashMap<String, JsonElement>()
        fun str(key: String, value: String?) { if (value != null) map[key] = JsonPrimitive(value) }
        fun int(key: String, value: Int?) { if (value != null) map[key] = JsonPrimitive(value) }
        fun bool(key: String, value: Boolean?) { if (value != null) map[key] = JsonPrimitive(value) }
        fun date(key: String, value: Instant?) { if (value != null) map[key] = JsonPrimitive(formatDate(value)) }

        /** A whole number is written without a fraction (60, not 60.0), as Swift writes a Double. */
        fun double(key: String, value: Double?) {
            if (value == null) return
            map[key] = if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
        }

        fun <T> list(key: String, items: List<T>, each: (T) -> JsonObject) { map[key] = JsonArray(items.map(each)) }
    }

    private fun obj(build: W.() -> Unit) = JsonObject(W().apply(build).map)

    /** Keys sorted at every level (Swift's `.sortedKeys`), so the same records give the same file. */
    private fun sorted(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.entries.sortedBy { it.key }.associateTo(LinkedHashMap()) { it.key to sorted(it.value) })
        is JsonArray -> JsonArray(e.map(::sorted))
        else -> e
    }

    /** A reader over one object; a required field that is missing or of the wrong type fails the read. */
    private class R(val o: JsonObject) {
        private fun prim(key: String): JsonPrimitive? = (o[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
        private fun missing(key: String): Nothing = throw BackupFormatException("missing $key") // not-copy: diagnostic

        fun strOrNull(key: String): String? = prim(key)?.takeIf { it.isString }?.content
        fun str(key: String): String = strOrNull(key) ?: missing(key)
        fun idOrNull(key: String): String? = strOrNull(key)?.uppercase()
        fun id(key: String): String = idOrNull(key) ?: missing(key)
        fun intOrNull(key: String): Int? = prim(key)?.intOrNull
        fun int(key: String): Int = intOrNull(key) ?: missing(key)
        fun doubleOrNull(key: String): Double? = prim(key)?.doubleOrNull
        fun double(key: String): Double = doubleOrNull(key) ?: missing(key)
        fun boolOrNull(key: String): Boolean? = prim(key)?.booleanOrNull
        fun bool(key: String): Boolean = boolOrNull(key) ?: missing(key)
        fun dateOrNull(key: String): Instant? = strOrNull(key)?.let(::parseDate)
        fun date(key: String): Instant = dateOrNull(key) ?: missing(key)
        fun <T> list(key: String, each: (R) -> T): List<T> = (o[key] as? JsonArray)?.map { each(R(it.jsonObject)) } ?: emptyList()
    }
}
