import Foundation

/// The on-disk archive format for a Keel backup. Every model becomes a flat,
/// Codable DTO with enums stored as String rawValues and relationships stored as
/// UUID foreign keys — the same shape the sync layer uses, so this file is
/// portable across CloudKit / Postgres / plain JSON.
struct KeelBackup: Codable {
    var version: Int
    var exportedAt: Date
    var appVersion: String

    var profiles: [ProfileDTO]
    var symptoms: [SymptomDTO]
    var checkIns: [CheckInDTO]
    var checkInSymptoms: [CheckInSymptomDTO]
    var cycleEntries: [CycleEntryDTO]
    var medications: [MedicationDTO]
    var medicationLogs: [MedicationLogDTO]
    var insights: [InsightDTO]
    var activityLogs: [ActivityLogDTO]
    var chatMessages: [ChatMessageDTO]
    var dailySummaries: [DailySummaryDTO]
    var healthSamples: [HealthSampleDTO]
}

struct HealthSampleDTO: Codable {
    var id: UUID
    var typeID: String
    var day: Date
    var value: Double
    var unit: String
    var sourceRaw: String
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: HealthSample) {
        id = m.id; typeID = m.typeID; day = m.day; value = m.value; unit = m.unit
        sourceRaw = m.sourceRaw
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> HealthSample {
        let m = HealthSample(
            id: id, typeID: typeID, day: day, value: value, unit: unit,
            source: DataSource(rawValue: sourceRaw) ?? .healthKit,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt)
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct DailySummaryDTO: Codable {
    var id: UUID
    var day: Date
    var text: String
    var sourceRaw: String
    var signalsJSON: String?
    var generatedAt: Date
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: DailySummary) {
        id = m.id; day = m.day; text = m.text; sourceRaw = m.sourceRaw
        signalsJSON = m.signalsJSON; generatedAt = m.generatedAt
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> DailySummary {
        let m = DailySummary(
            id: id, day: day, text: text,
            source: DailySummarySource(rawValue: sourceRaw) ?? .deterministic,
            signalsJSON: signalsJSON, generatedAt: generatedAt,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt)
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

// MARK: - DTOs

struct ProfileDTO: Codable {
    var id: UUID
    var firstName: String
    var email: String?
    var appleUserID: String?
    var pathwayRaw: String?
    var healthKitAuthorized: Bool
    var trackingStartDate: Date
    var region: String?
    var localeID: String?
    var timeZoneID: String?
    var appVersion: String?
    var deviceModel: String?
    var osVersion: String?
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: UserProfile) {
        id = m.id; firstName = m.firstName; email = m.email; appleUserID = m.appleUserID
        pathwayRaw = m.pathwayRaw; healthKitAuthorized = m.healthKitAuthorized
        trackingStartDate = m.trackingStartDate
        region = m.region; localeID = m.localeID; timeZoneID = m.timeZoneID
        appVersion = m.appVersion; deviceModel = m.deviceModel; osVersion = m.osVersion
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> UserProfile {
        let m = UserProfile(
            id: id, firstName: firstName, email: email, appleUserID: appleUserID,
            pathway: pathwayRaw.flatMap(Pathway.init(rawValue:)),
            healthKitAuthorized: healthKitAuthorized, trackingStartDate: trackingStartDate,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt
        )
        m.region = region; m.localeID = localeID; m.timeZoneID = timeZoneID
        m.appVersion = appVersion; m.deviceModel = deviceModel; m.osVersion = osVersion
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct SymptomDTO: Codable {
    var id: UUID
    var name: String
    var categoryRaw: String
    var isCustom: Bool
    var isArchived: Bool
    /// Absent in archives written before the default-chip set existed.
    var isDefaultChip: Bool?
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: Symptom) {
        id = m.id; name = m.name; categoryRaw = m.categoryRaw
        isCustom = m.isCustom; isArchived = m.isArchived; isDefaultChip = m.isDefaultChip
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> Symptom {
        let m = Symptom(
            id: id, name: name, category: .body,
            isCustom: isCustom, isArchived: isArchived,
            isDefaultChip: isDefaultChip ?? SymptomCatalog.defaultOrder.contains(name),
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt
        )
        m.categoryRaw = categoryRaw   // preserve custom categories verbatim
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct CheckInDTO: Codable {
    var id: UUID
    var date: Date
    var moodRaw: String
    var energy: Int
    var notes: String?
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: CheckIn) {
        id = m.id; date = m.date; moodRaw = m.moodRaw; energy = m.energy; notes = m.notes
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> CheckIn {
        let m = CheckIn(
            id: id, date: date, mood: Mood(rawValue: moodRaw) ?? .okay,
            energy: energy, notes: notes,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt
        )
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct CheckInSymptomDTO: Codable {
    var id: UUID
    var checkInID: UUID?
    var symptomID: UUID?
    var severity: Int?   // optional so older archives (pre-severity) still decode
    var sourceRaw: String?  // optional so older archives (pre-source) still decode
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: CheckInSymptom) {
        id = m.id; checkInID = m.checkInID; symptomID = m.symptomID; severity = m.severity
        sourceRaw = m.sourceRaw
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model(checkIn: CheckIn, symptom: Symptom) -> CheckInSymptom {
        let m = CheckInSymptom(
            id: id, checkIn: checkIn, symptom: symptom, severity: severity ?? 1,
            source: DataSource(rawValue: sourceRaw ?? "") ?? .manual,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt
        )
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct CycleEntryDTO: Codable {
    var id: UUID
    var date: Date
    var typeRaw: String
    var flowLevelRaw: String? // optional so older archives (pre-level) still decode
    var sourceRaw: String?  // optional so older archives (pre-source) still decode
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: CycleEntry) {
        id = m.id; date = m.date; typeRaw = m.typeRaw
        flowLevelRaw = m.flowLevelRaw; sourceRaw = m.sourceRaw
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> CycleEntry {
        let m = CycleEntry(
            id: id, date: date, type: CycleEntryType(rawValue: typeRaw) ?? .flow,
            flowLevel: flowLevelRaw.flatMap(FlowLevel.init(rawValue:)),
            source: DataSource(rawValue: sourceRaw ?? "") ?? .manual,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt
        )
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct MedicationDTO: Codable {
    var id: UUID
    var name: String
    var dosage: String
    var timing: String
    var methodRaw: String?
    var isActive: Bool
    /// All absent in archives written before treatments were tracked in detail.
    var doseAmount: Double?
    var doseUnitRaw: String?
    var timeOfDayRaw: String?
    var kindRaw: String?
    var catalogGroupID: String?
    var scheduleKindRaw: String?
    var weekdaysMask: Int?
    var cycleLength: Int?
    var cyclePauseDays: Int?
    var cycleAnchor: Date?
    var doseTime: Date?
    var doseTimesJSON: String?
    /// Superseded by the schedule; kept so an older archive can still migrate.
    var frequencyRaw: String?
    var date: Date?
    /// Older archives called it `startDate`; read it so nothing is lost.
    var startDate: Date?
    var doseChangedAt: Date?
    var note: String?
    var isOffLabel: Bool?
    var isCompounded: Bool?
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: Medication) {
        id = m.id; name = m.name; dosage = m.dosage; timing = m.timing
        methodRaw = m.methodRaw; isActive = m.isActive
        doseAmount = m.doseAmount; doseUnitRaw = m.doseUnitRaw; timeOfDayRaw = m.timeOfDayRaw
        kindRaw = m.kindRaw; catalogGroupID = m.catalogGroupID; frequencyRaw = m.frequencyRaw
        scheduleKindRaw = m.scheduleKindRaw; weekdaysMask = m.weekdaysMask
        cycleLength = m.cycleLength; cyclePauseDays = m.cyclePauseDays
        cycleAnchor = m.cycleAnchor; doseTime = m.doseTime; doseTimesJSON = m.doseTimesJSON
        date = m.date; startDate = nil; doseChangedAt = m.doseChangedAt; note = m.note
        isOffLabel = m.isOffLabel; isCompounded = m.isCompounded
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> Medication {
        let m = Medication(
            id: id, name: name, dosage: dosage,
            doseAmount: doseAmount,
            doseUnit: doseUnitRaw.flatMap(DoseUnit.init(rawValue:)),
            timing: timing,
            method: methodRaw.flatMap(MedicationMethod.init(rawValue:)), isActive: isActive,
            kind: kindRaw.flatMap(TreatmentKind.init(rawValue:)) ?? .supplement,
            catalogGroupID: catalogGroupID,
            date: date ?? startDate, doseChangedAt: doseChangedAt, note: note,
            isOffLabel: isOffLabel ?? false, isCompounded: isCompounded ?? false,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt
        )
        m.timeOfDayRaw = timeOfDayRaw
        m.frequencyRaw = frequencyRaw
        if let scheduleKindRaw {
            m.scheduleKindRaw = scheduleKindRaw
            m.weekdaysMask = weekdaysMask ?? 0
            m.cycleLength = cycleLength ?? 28
            m.cyclePauseDays = cyclePauseDays ?? 7
            m.cycleAnchor = cycleAnchor
            m.doseTime = doseTime
            m.doseTimesJSON = doseTimesJSON
        }
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct MedicationLogDTO: Codable {
    var id: UUID
    var date: Date
    /// Which dose of the day; absent in archives written before there could be
    /// more than one.
    var slot: String?
    var taken: Bool
    var medicationID: UUID?
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: MedicationLog) {
        id = m.id; date = m.date; slot = m.slot; taken = m.taken; medicationID = m.medicationID
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model(medication: Medication?) -> MedicationLog {
        let m = MedicationLog(
            id: id, date: date, slot: slot, taken: taken, medication: medication,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt
        )
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct InsightDTO: Codable {
    var id: UUID
    var title: String
    var detail: String
    var timeframe: String
    var iconKey: String
    var accentRaw: String
    var generatedAt: Date
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(_ m: Insight) {
        id = m.id; title = m.title; detail = m.detail; timeframe = m.timeframe
        iconKey = m.iconKey; accentRaw = m.accentRaw; generatedAt = m.generatedAt
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> Insight {
        let m = Insight(
            id: id, title: title, detail: detail, timeframe: timeframe, iconKey: iconKey,
            accent: InsightAccent(rawValue: accentRaw) ?? .terracotta, generatedAt: generatedAt,
            ownerID: ownerID, createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt
        )
        m.syncStatusRaw = syncStatusRaw
        return m
    }
}

struct ActivityLogDTO: Codable {
    var id: UUID
    var date: Date
    var activityID: String
    var amount: Double
    /// Optional so pre-source archives still decode (they restore as `.manual`).
    var sourceRaw: String?
    // Syncable envelope (optional so pre-sync archives still decode).
    var ownerID: String?
    var createdAt: Date?
    var updatedAt: Date?
    var deletedAt: Date?
    var syncStatusRaw: String?

    init(_ m: ActivityLog) {
        id = m.id; date = m.date; activityID = m.activityID; amount = m.amount
        sourceRaw = m.sourceRaw
        ownerID = m.ownerID; createdAt = m.createdAt; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> ActivityLog {
        let m = ActivityLog(
            id: id, date: date, activityID: activityID, amount: amount,
            source: sourceRaw.flatMap(DataSource.init(rawValue:)) ?? .manual,
            ownerID: ownerID ?? "", createdAt: createdAt ?? .now,
            updatedAt: updatedAt ?? .now, deletedAt: deletedAt
        )
        m.syncStatusRaw = syncStatusRaw ?? SyncStatus.pendingUpload.rawValue
        return m
    }
}

struct ChatMessageDTO: Codable {
    var id: UUID
    var roleRaw: String
    var text: String
    var createdAt: Date
    // Syncable envelope (optional so pre-sync archives still decode).
    var ownerID: String?
    var updatedAt: Date?
    var deletedAt: Date?
    var syncStatusRaw: String?

    init(_ m: ChatMessage) {
        id = m.id; roleRaw = m.roleRaw; text = m.text; createdAt = m.createdAt
        ownerID = m.ownerID; updatedAt = m.updatedAt
        deletedAt = m.deletedAt; syncStatusRaw = m.syncStatusRaw
    }

    func model() -> ChatMessage {
        let m = ChatMessage(
            id: id, role: ChatRole(rawValue: roleRaw) ?? .assistant, text: text,
            ownerID: ownerID ?? "", createdAt: createdAt,
            updatedAt: updatedAt ?? createdAt, deletedAt: deletedAt
        )
        m.syncStatusRaw = syncStatusRaw ?? SyncStatus.pendingUpload.rawValue
        return m
    }
}
