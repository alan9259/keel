import Foundation
import SwiftData

/// Record/table names. These are used verbatim as CloudKit record types today
/// and as Postgres table names after the Supabase migration.
enum RecordType {
    static let profile = "profiles"
    static let checkIn = "check_ins"
    static let symptom = "symptoms"
    static let checkInSymptom = "check_in_symptoms"
    static let cycleEntry = "cycle_entries"
    static let medication = "medications"
    static let medicationLog = "medication_logs"
    static let insight = "insights"
    static let activityLog = "activity_logs"
    static let chatMessage = "chat_messages"
    static let dailySummary = "daily_summaries"
    static let healthSample = "health_samples"

    static let all = [
        profile, checkIn, symptom, checkInSymptom,
        cycleEntry, medication, medicationLog, insight,
        activityLog, chatMessage, dailySummary, healthSample,
    ]
}

// MARK: - Encoding (model → RemoteRecord)

/// A model that can serialize its entity-specific columns. The sync envelope
/// (id/owner/timestamps/deletedAt) is added centrally by `makeRemoteRecord`.
protocol RemoteMappable: Syncable {
    static var recordType: String { get }
    func remoteFields() -> [String: RemoteValue]
}

func makeRemoteRecord<T: RemoteMappable>(_ model: T) -> RemoteRecord {
    RemoteRecord(
        recordType: T.recordType,
        id: model.id,
        ownerID: model.ownerID,
        createdAt: model.createdAt,
        updatedAt: model.updatedAt,
        deletedAt: model.deletedAt,
        fields: model.remoteFields()
    )
}

extension UserProfile: RemoteMappable {
    static var recordType: String { RecordType.profile }
    func remoteFields() -> [String: RemoteValue] {
        var f: [String: RemoteValue] = [
            "firstName": .string(firstName),
            "healthKitAuthorized": .bool(healthKitAuthorized),
            "trackingStartDate": .date(trackingStartDate),
        ]
        if let email { f["email"] = .string(email) }
        if let appleUserID { f["appleUserID"] = .string(appleUserID) }
        if let pathwayRaw { f["pathwayRaw"] = .string(pathwayRaw) }
        if let region { f["region"] = .string(region) }
        if let localeID { f["localeID"] = .string(localeID) }
        if let timeZoneID { f["timeZoneID"] = .string(timeZoneID) }
        if let appVersion { f["appVersion"] = .string(appVersion) }
        if let deviceModel { f["deviceModel"] = .string(deviceModel) }
        if let osVersion { f["osVersion"] = .string(osVersion) }
        return f
    }
}

extension CheckIn: RemoteMappable {
    static var recordType: String { RecordType.checkIn }
    func remoteFields() -> [String: RemoteValue] {
        var f: [String: RemoteValue] = [
            "date": .date(date),
            "moodRaw": .string(moodRaw),
            "energy": .int(energy),
        ]
        if let notes { f["notes"] = .string(notes) }
        return f
    }
}

extension Symptom: RemoteMappable {
    static var recordType: String { RecordType.symptom }
    func remoteFields() -> [String: RemoteValue] {
        [
            "name": .string(name),
            "categoryRaw": .string(categoryRaw),
            "isCustom": .bool(isCustom),
            "isArchived": .bool(isArchived),
            "isDefaultChip": .bool(isDefaultChip),
        ]
    }
}

extension CheckInSymptom: RemoteMappable {
    static var recordType: String { RecordType.checkInSymptom }
    func remoteFields() -> [String: RemoteValue] {
        var f: [String: RemoteValue] = ["severity": .int(severity), "sourceRaw": .string(sourceRaw)]
        if let checkInID { f["checkInID"] = .uuid(checkInID) }
        if let symptomID { f["symptomID"] = .uuid(symptomID) }
        return f
    }
}

extension CycleEntry: RemoteMappable {
    static var recordType: String { RecordType.cycleEntry }
    func remoteFields() -> [String: RemoteValue] {
        ["date": .date(date), "typeRaw": .string(typeRaw), "sourceRaw": .string(sourceRaw)]
    }
}

extension Medication: RemoteMappable {
    static var recordType: String { RecordType.medication }
    func remoteFields() -> [String: RemoteValue] {
        var f: [String: RemoteValue] = [
            "name": .string(name),
            "dosage": .string(dosage),
            "timing": .string(timing),
            "isActive": .bool(isActive),
            "kindRaw": .string(kindRaw),
            "isOffLabel": .bool(isOffLabel),
            "isCompounded": .bool(isCompounded),
        ]
        if let methodRaw { f["methodRaw"] = .string(methodRaw) }
        if let doseAmount { f["doseAmount"] = .double(doseAmount) }
        if let doseUnitRaw { f["doseUnitRaw"] = .string(doseUnitRaw) }
        if let timeOfDayRaw { f["timeOfDayRaw"] = .string(timeOfDayRaw) }
        if let catalogGroupID { f["catalogGroupID"] = .string(catalogGroupID) }
        if let frequencyRaw { f["frequencyRaw"] = .string(frequencyRaw) }
        if let scheduleKindRaw {
            f["scheduleKindRaw"] = .string(scheduleKindRaw)
            f["weekdaysMask"] = .int(weekdaysMask)
            f["cycleLength"] = .int(cycleLength)
            f["cyclePauseDays"] = .int(cyclePauseDays)
        }
        if let cycleAnchor { f["cycleAnchor"] = .date(cycleAnchor) }
        if let doseTime { f["doseTime"] = .date(doseTime) }
        if let doseTimesJSON { f["doseTimesJSON"] = .string(doseTimesJSON) }
        if let date { f["date"] = .date(date) }
        if let doseChangedAt { f["doseChangedAt"] = .date(doseChangedAt) }
        if let note { f["note"] = .string(note) }
        return f
    }
}

extension MedicationLog: RemoteMappable {
    static var recordType: String { RecordType.medicationLog }
    func remoteFields() -> [String: RemoteValue] {
        var f: [String: RemoteValue] = [
            "date": .date(date),
            "taken": .bool(taken),
        ]
        if let medicationID { f["medicationID"] = .uuid(medicationID) }
        if let slot { f["slot"] = .string(slot) }
        return f
    }
}

extension Insight: RemoteMappable {
    static var recordType: String { RecordType.insight }
    func remoteFields() -> [String: RemoteValue] {
        [
            "title": .string(title),
            "detail": .string(detail),
            "timeframe": .string(timeframe),
            "iconKey": .string(iconKey),
            "accentRaw": .string(accentRaw),
            "generatedAt": .date(generatedAt),
        ]
    }
}

extension ActivityLog: RemoteMappable {
    static var recordType: String { RecordType.activityLog }
    func remoteFields() -> [String: RemoteValue] {
        [
            "date": .date(date),
            "activityID": .string(activityID),
            "amount": .double(amount),
        ]
    }
}

extension ChatMessage: RemoteMappable {
    static var recordType: String { RecordType.chatMessage }
    func remoteFields() -> [String: RemoteValue] {
        ["roleRaw": .string(roleRaw), "text": .string(text)]
    }
}

extension DailySummary: RemoteMappable {
    static var recordType: String { RecordType.dailySummary }
    func remoteFields() -> [String: RemoteValue] {
        var f: [String: RemoteValue] = [
            "day": .date(day),
            "text": .string(text),
            "sourceRaw": .string(sourceRaw),
            "generatedAt": .date(generatedAt),
        ]
        if let signalsJSON { f["signalsJSON"] = .string(signalsJSON) }
        return f
    }
}

extension HealthSample: RemoteMappable {
    static var recordType: String { RecordType.healthSample }
    func remoteFields() -> [String: RemoteValue] {
        [
            "typeID": .string(typeID),
            "day": .date(day),
            "value": .double(value),
            "unit": .string(unit),
            "sourceRaw": .string(sourceRaw),
        ]
    }
}

// MARK: - Decoding (RemoteRecord → model upsert)

/// Applies pulled records into SwiftData: find-or-create by `id`, resolve
/// relationships by foreign-key UUID, and use last-write-wins on `updatedAt`.
@MainActor
struct RemoteApplier {
    let context: ModelContext

    func apply(_ records: [RemoteRecord]) {
        // Apply in dependency order so foreign keys resolve.
        let order = RecordType.all
        for record in records.sorted(by: { order.firstIndex(of: $0.recordType) ?? 0 < order.firstIndex(of: $1.recordType) ?? 0 }) {
            apply(record)
        }
        try? context.save()
    }

    private func apply(_ r: RemoteRecord) {
        switch r.recordType {
        case RecordType.profile: applyProfile(r)
        case RecordType.checkIn: applyCheckIn(r)
        case RecordType.symptom: applySymptom(r)
        case RecordType.checkInSymptom: applyCheckInSymptom(r)
        case RecordType.cycleEntry: applyCycleEntry(r)
        case RecordType.medication: applyMedication(r)
        case RecordType.medicationLog: applyMedicationLog(r)
        case RecordType.insight: applyInsight(r)
        case RecordType.activityLog: applyActivityLog(r)
        case RecordType.chatMessage: applyChatMessage(r)
        case RecordType.dailySummary: applyDailySummary(r)
        case RecordType.healthSample: applyHealthSample(r)
        default: break
        }
    }

    private func applyEnvelope(_ r: RemoteRecord, to model: some Syncable) {
        model.ownerID = r.ownerID
        model.updatedAt = r.updatedAt
        model.deletedAt = r.deletedAt
        model.syncStatus = .synced
    }

    private func isStale(_ local: some Syncable, _ r: RemoteRecord) -> Bool {
        r.updatedAt <= local.updatedAt
    }

    private func fetchByID<T: PersistentModel>(_ descriptor: FetchDescriptor<T>) -> T? {
        try? context.fetch(descriptor).first
    }

    private func applyProfile(_ r: RemoteRecord) {
        let id = r.id
        if let existing = fetchByID(FetchDescriptor<UserProfile>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            existing.firstName = r.fields["firstName"]?.asString ?? existing.firstName
            existing.email = r.fields["email"]?.asString
            existing.appleUserID = r.fields["appleUserID"]?.asString
            existing.pathwayRaw = r.fields["pathwayRaw"]?.asString
            existing.healthKitAuthorized = r.fields["healthKitAuthorized"]?.asBool ?? existing.healthKitAuthorized
            if let d = r.fields["trackingStartDate"]?.asDate { existing.trackingStartDate = d }
            applyProfileContext(r, to: existing)
            applyEnvelope(r, to: existing)
        } else {
            let p = UserProfile(
                id: id,
                firstName: r.fields["firstName"]?.asString ?? "",
                email: r.fields["email"]?.asString,
                appleUserID: r.fields["appleUserID"]?.asString,
                healthKitAuthorized: r.fields["healthKitAuthorized"]?.asBool ?? false,
                trackingStartDate: r.fields["trackingStartDate"]?.asDate ?? .now,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            p.pathwayRaw = r.fields["pathwayRaw"]?.asString
            applyProfileContext(r, to: p)
            context.insert(p)
        }
    }

    /// Copy the non-identifying environment context from a remote record.
    private func applyProfileContext(_ r: RemoteRecord, to profile: UserProfile) {
        profile.region = r.fields["region"]?.asString
        profile.localeID = r.fields["localeID"]?.asString
        profile.timeZoneID = r.fields["timeZoneID"]?.asString
        profile.appVersion = r.fields["appVersion"]?.asString
        profile.deviceModel = r.fields["deviceModel"]?.asString
        profile.osVersion = r.fields["osVersion"]?.asString
    }

    private func applyCheckIn(_ r: RemoteRecord) {
        let id = r.id
        let mood = Mood(rawValue: r.fields["moodRaw"]?.asString ?? "") ?? .okay
        if let existing = fetchByID(FetchDescriptor<CheckIn>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            if let d = r.fields["date"]?.asDate { existing.date = d }
            existing.moodRaw = mood.rawValue
            existing.energy = r.fields["energy"]?.asInt ?? existing.energy
            existing.notes = r.fields["notes"]?.asString
            applyEnvelope(r, to: existing)
        } else {
            let c = CheckIn(
                id: id, date: r.fields["date"]?.asDate ?? r.createdAt, mood: mood,
                energy: r.fields["energy"]?.asInt ?? 50, notes: r.fields["notes"]?.asString,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(c)
        }
    }

    private func applySymptom(_ r: RemoteRecord) {
        let id = r.id
        // Preserve the raw category string verbatim so user-created categories
        // survive a round-trip (don't collapse unknown values to `.body`).
        let categoryRaw = r.fields["categoryRaw"]?.asString ?? SymptomCategory.body.rawValue
        if let existing = fetchByID(FetchDescriptor<Symptom>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            existing.name = r.fields["name"]?.asString ?? existing.name
            existing.categoryRaw = categoryRaw
            existing.isCustom = r.fields["isCustom"]?.asBool ?? existing.isCustom
            existing.isArchived = r.fields["isArchived"]?.asBool ?? existing.isArchived
            existing.isDefaultChip = r.fields["isDefaultChip"]?.asBool ?? existing.isDefaultChip
            applyEnvelope(r, to: existing)
        } else {
            let s = Symptom(
                id: id, name: r.fields["name"]?.asString ?? "", category: .body,
                isCustom: r.fields["isCustom"]?.asBool ?? true,
                isArchived: r.fields["isArchived"]?.asBool ?? false,
                isDefaultChip: r.fields["isDefaultChip"]?.asBool ?? false,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            s.categoryRaw = categoryRaw
            context.insert(s)
        }
    }

    private func applyCheckInSymptom(_ r: RemoteRecord) {
        let id = r.id
        let checkIn = r.fields["checkInID"]?.asUUID.flatMap { cid in
            fetchByID(FetchDescriptor<CheckIn>(predicate: #Predicate { $0.id == cid }))
        }
        let symptom = r.fields["symptomID"]?.asUUID.flatMap { sid in
            fetchByID(FetchDescriptor<Symptom>(predicate: #Predicate { $0.id == sid }))
        }
        let severity = r.fields["severity"]?.asInt ?? 1
        let source = DataSource(rawValue: r.fields["sourceRaw"]?.asString ?? "") ?? .manual
        if let existing = fetchByID(FetchDescriptor<CheckInSymptom>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            existing.checkIn = checkIn
            existing.symptom = symptom
            existing.severity = severity
            existing.source = source
            applyEnvelope(r, to: existing)
        } else {
            let link = CheckInSymptom(
                id: id, checkIn: checkIn, symptom: symptom, severity: severity, source: source,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(link)
        }
    }

    private func applyCycleEntry(_ r: RemoteRecord) {
        let id = r.id
        let type = CycleEntryType(rawValue: r.fields["typeRaw"]?.asString ?? "") ?? .flow
        let source = DataSource(rawValue: r.fields["sourceRaw"]?.asString ?? "") ?? .manual
        if let existing = fetchByID(FetchDescriptor<CycleEntry>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            if let d = r.fields["date"]?.asDate { existing.date = d }
            existing.typeRaw = type.rawValue
            existing.source = source
            applyEnvelope(r, to: existing)
        } else {
            let e = CycleEntry(
                id: id, date: r.fields["date"]?.asDate ?? r.createdAt, type: type, source: source,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(e)
        }
    }

    private func applyMedication(_ r: RemoteRecord) {
        let id = r.id
        if let existing = fetchByID(FetchDescriptor<Medication>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            existing.name = r.fields["name"]?.asString ?? existing.name
            existing.dosage = r.fields["dosage"]?.asString ?? existing.dosage
            existing.timing = r.fields["timing"]?.asString ?? existing.timing
            existing.methodRaw = r.fields["methodRaw"]?.asString
            existing.doseAmount = r.fields["doseAmount"]?.asDouble
            existing.doseUnitRaw = r.fields["doseUnitRaw"]?.asString
            existing.timeOfDayRaw = r.fields["timeOfDayRaw"]?.asString
            existing.isActive = r.fields["isActive"]?.asBool ?? existing.isActive
            existing.kindRaw = r.fields["kindRaw"]?.asString ?? existing.kindRaw
            existing.catalogGroupID = r.fields["catalogGroupID"]?.asString
            existing.frequencyRaw = r.fields["frequencyRaw"]?.asString
            existing.scheduleKindRaw = r.fields["scheduleKindRaw"]?.asString
            existing.weekdaysMask = r.fields["weekdaysMask"]?.asInt ?? existing.weekdaysMask
            existing.cycleLength = r.fields["cycleLength"]?.asInt ?? existing.cycleLength
            existing.cyclePauseDays = r.fields["cyclePauseDays"]?.asInt ?? existing.cyclePauseDays
            existing.cycleAnchor = r.fields["cycleAnchor"]?.asDate
            existing.doseTime = r.fields["doseTime"]?.asDate
            existing.doseTimesJSON = r.fields["doseTimesJSON"]?.asString
            existing.date = r.fields["date"]?.asDate ?? r.fields["startDate"]?.asDate
            existing.doseChangedAt = r.fields["doseChangedAt"]?.asDate
            existing.note = r.fields["note"]?.asString
            existing.isOffLabel = r.fields["isOffLabel"]?.asBool ?? existing.isOffLabel
            existing.isCompounded = r.fields["isCompounded"]?.asBool ?? existing.isCompounded
            applyEnvelope(r, to: existing)
        } else {
            let m = Medication(
                id: id, name: r.fields["name"]?.asString ?? "",
                dosage: r.fields["dosage"]?.asString ?? "",
                timing: r.fields["timing"]?.asString ?? "",
                isActive: r.fields["isActive"]?.asBool ?? true,
                catalogGroupID: r.fields["catalogGroupID"]?.asString,
                date: r.fields["date"]?.asDate ?? r.fields["startDate"]?.asDate,
                doseChangedAt: r.fields["doseChangedAt"]?.asDate,
                note: r.fields["note"]?.asString,
                isOffLabel: r.fields["isOffLabel"]?.asBool ?? false,
                isCompounded: r.fields["isCompounded"]?.asBool ?? false,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            m.methodRaw = r.fields["methodRaw"]?.asString
            m.kindRaw = r.fields["kindRaw"]?.asString ?? TreatmentKind.supplement.rawValue
            m.frequencyRaw = r.fields["frequencyRaw"]?.asString
            m.scheduleKindRaw = r.fields["scheduleKindRaw"]?.asString
            m.weekdaysMask = r.fields["weekdaysMask"]?.asInt ?? 0
            m.cycleLength = r.fields["cycleLength"]?.asInt ?? 28
            m.cyclePauseDays = r.fields["cyclePauseDays"]?.asInt ?? 7
            m.cycleAnchor = r.fields["cycleAnchor"]?.asDate
            m.doseTime = r.fields["doseTime"]?.asDate
            m.doseTimesJSON = r.fields["doseTimesJSON"]?.asString
            m.doseAmount = r.fields["doseAmount"]?.asDouble
            m.doseUnitRaw = r.fields["doseUnitRaw"]?.asString
            m.timeOfDayRaw = r.fields["timeOfDayRaw"]?.asString
            context.insert(m)
        }
    }

    private func applyMedicationLog(_ r: RemoteRecord) {
        let id = r.id
        let med = r.fields["medicationID"]?.asUUID.flatMap { mid in
            fetchByID(FetchDescriptor<Medication>(predicate: #Predicate { $0.id == mid }))
        }
        if let existing = fetchByID(FetchDescriptor<MedicationLog>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            if let d = r.fields["date"]?.asDate { existing.date = d }
            existing.taken = r.fields["taken"]?.asBool ?? existing.taken
            existing.slot = r.fields["slot"]?.asString
            existing.medication = med
            applyEnvelope(r, to: existing)
        } else {
            let log = MedicationLog(
                id: id, date: r.fields["date"]?.asDate ?? r.createdAt,
                slot: r.fields["slot"]?.asString,
                taken: r.fields["taken"]?.asBool ?? false, medication: med,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(log)
        }
    }

    private func applyInsight(_ r: RemoteRecord) {
        let id = r.id
        let accent = InsightAccent(rawValue: r.fields["accentRaw"]?.asString ?? "") ?? .terracotta
        if let existing = fetchByID(FetchDescriptor<Insight>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            existing.title = r.fields["title"]?.asString ?? existing.title
            existing.detail = r.fields["detail"]?.asString ?? existing.detail
            existing.timeframe = r.fields["timeframe"]?.asString ?? existing.timeframe
            existing.iconKey = r.fields["iconKey"]?.asString ?? existing.iconKey
            existing.accentRaw = accent.rawValue
            if let d = r.fields["generatedAt"]?.asDate { existing.generatedAt = d }
            applyEnvelope(r, to: existing)
        } else {
            let i = Insight(
                id: id, title: r.fields["title"]?.asString ?? "",
                detail: r.fields["detail"]?.asString ?? "",
                timeframe: r.fields["timeframe"]?.asString ?? "",
                iconKey: r.fields["iconKey"]?.asString ?? "sparkles",
                accent: accent, generatedAt: r.fields["generatedAt"]?.asDate ?? r.createdAt,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(i)
        }
    }

    private func applyActivityLog(_ r: RemoteRecord) {
        let id = r.id
        let date = r.fields["date"]?.asDate ?? r.createdAt
        let activityID = r.fields["activityID"]?.asString ?? ""
        let amount = r.fields["amount"]?.asDouble ?? 0
        if let existing = fetchByID(FetchDescriptor<ActivityLog>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            existing.date = date
            existing.activityID = activityID
            existing.amount = amount
            applyEnvelope(r, to: existing)
        } else {
            let m = ActivityLog(
                id: id, date: date, activityID: activityID, amount: amount,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(m)
        }
    }

    private func applyChatMessage(_ r: RemoteRecord) {
        let id = r.id
        let role = ChatRole(rawValue: r.fields["roleRaw"]?.asString ?? "") ?? .assistant
        let text = r.fields["text"]?.asString ?? ""
        if let existing = fetchByID(FetchDescriptor<ChatMessage>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            existing.roleRaw = role.rawValue
            existing.text = text
            applyEnvelope(r, to: existing)
        } else {
            let m = ChatMessage(
                id: id, role: role, text: text,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(m)
        }
    }

    private func applyDailySummary(_ r: RemoteRecord) {
        let id = r.id
        let source = DailySummarySource(rawValue: r.fields["sourceRaw"]?.asString ?? "") ?? .deterministic
        if let existing = fetchByID(FetchDescriptor<DailySummary>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            if let d = r.fields["day"]?.asDate { existing.day = d }
            existing.text = r.fields["text"]?.asString ?? existing.text
            existing.sourceRaw = source.rawValue
            existing.signalsJSON = r.fields["signalsJSON"]?.asString
            if let g = r.fields["generatedAt"]?.asDate { existing.generatedAt = g }
            applyEnvelope(r, to: existing)
        } else {
            let s = DailySummary(
                id: id, day: r.fields["day"]?.asDate ?? r.createdAt,
                text: r.fields["text"]?.asString ?? "", source: source,
                signalsJSON: r.fields["signalsJSON"]?.asString,
                generatedAt: r.fields["generatedAt"]?.asDate ?? r.createdAt,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(s)
        }
    }

    private func applyHealthSample(_ r: RemoteRecord) {
        let id = r.id
        let source = DataSource(rawValue: r.fields["sourceRaw"]?.asString ?? "") ?? .healthKit
        if let existing = fetchByID(FetchDescriptor<HealthSample>(predicate: #Predicate { $0.id == id })) {
            guard !isStale(existing, r) else { return }
            existing.typeID = r.fields["typeID"]?.asString ?? existing.typeID
            if let d = r.fields["day"]?.asDate { existing.day = d }
            existing.value = r.fields["value"]?.asDouble ?? existing.value
            existing.unit = r.fields["unit"]?.asString ?? existing.unit
            existing.source = source
            applyEnvelope(r, to: existing)
        } else {
            let m = HealthSample(
                id: id, typeID: r.fields["typeID"]?.asString ?? "",
                day: r.fields["day"]?.asDate ?? r.createdAt,
                value: r.fields["value"]?.asDouble ?? 0,
                unit: r.fields["unit"]?.asString ?? "", source: source,
                ownerID: r.ownerID, createdAt: r.createdAt, updatedAt: r.updatedAt,
                deletedAt: r.deletedAt, syncStatus: .synced
            )
            context.insert(m)
        }
    }
}
