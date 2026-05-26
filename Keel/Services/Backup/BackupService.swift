import Foundation
import SwiftData

/// Real local backup. Serialises every SwiftData model to a portable JSON
/// archive and restores it back — fully offline, no CloudKit/signing required.
/// The exported `.keelbackup` file is a faithful, re-importable snapshot: it
/// captures the Syncable bookkeeping (ids, ownerID, timestamps, tombstones) and
/// reconstructs the object graph (check-in↔symptom links, medication logs) on
/// restore, so round-tripping loses nothing.
@MainActor
enum BackupService {
    /// Bumped whenever the archive shape changes; restore refuses newer versions.
    static let currentVersion = 1

    struct Summary {
        var checkIns = 0
        var symptoms = 0
        var cycleEntries = 0
        var medications = 0
        var medicationLogs = 0
        var activityLogs = 0
        var chatMessages = 0
    }

    enum BackupError: LocalizedError {
        case unsupportedVersion(Int)

        var errorDescription: String? {
            switch self {
            case .unsupportedVersion(let v):
                "This backup was made by a newer version of Keel (format \(v)). Update the app to restore it."
            }
        }
    }

    // MARK: - Export

    /// Snapshot the whole store into a Codable archive.
    static func export(context: ModelContext) throws -> KeelBackup {
        func all<T: PersistentModel>(_ type: T.Type) throws -> [T] {
            try context.fetch(FetchDescriptor<T>())
        }

        return KeelBackup(
            version: currentVersion,
            exportedAt: .now,
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
            profiles: try all(UserProfile.self).map(ProfileDTO.init),
            symptoms: try all(Symptom.self).map(SymptomDTO.init),
            checkIns: try all(CheckIn.self).map(CheckInDTO.init),
            checkInSymptoms: try all(CheckInSymptom.self).map(CheckInSymptomDTO.init),
            cycleEntries: try all(CycleEntry.self).map(CycleEntryDTO.init),
            medications: try all(Medication.self).map(MedicationDTO.init),
            medicationLogs: try all(MedicationLog.self).map(MedicationLogDTO.init),
            insights: try all(Insight.self).map(InsightDTO.init),
            activityLogs: try all(ActivityLog.self).map(ActivityLogDTO.init),
            chatMessages: try all(ChatMessage.self).map(ChatMessageDTO.init),
            dailySummaries: try all(DailySummary.self).map(DailySummaryDTO.init),
            healthSamples: try all(HealthSample.self).map(HealthSampleDTO.init)
        )
    }

    /// Pretty-printed JSON `Data` for a ShareLink / file write.
    static func exportData(context: ModelContext) throws -> Data {
        try encode(export(context: context))
    }

    static func encode(_ backup: KeelBackup) throws -> Data {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(backup)
    }

    /// Writes the archive to a temp file and returns its URL (for a file-based
    /// ShareLink so the user gets a real `.keelbackup` document, not raw text).
    static func exportFile(context: ModelContext) throws -> URL {
        let data = try exportData(context: context)
        let name = "Keel-backup-\(Self.fileStamp()).keelbackup"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try data.write(to: url, options: .atomic)
        return url
    }

    // MARK: - Import

    static func decode(_ data: Data) throws -> KeelBackup {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(KeelBackup.self, from: data)
    }

    /// Replace ALL local data with the archive's contents. Wipes every model,
    /// then reinserts and re-links. Runs inside the passed context and saves once.
    @discardableResult
    static func restore(from backup: KeelBackup, into context: ModelContext) throws -> Summary {
        guard backup.version <= currentVersion else {
            throw BackupError.unsupportedVersion(backup.version)
        }

        try wipeAll(context)

        // Insert parents first so relationships can be resolved by id.
        for dto in backup.symptoms { context.insert(dto.model()) }
        for dto in backup.profiles { context.insert(dto.model()) }
        for dto in backup.cycleEntries { context.insert(dto.model()) }
        for dto in backup.insights { context.insert(dto.model()) }
        for dto in backup.activityLogs { context.insert(dto.model()) }
        for dto in backup.chatMessages { context.insert(dto.model()) }
        for dto in backup.dailySummaries { context.insert(dto.model()) }
        for dto in backup.healthSamples { context.insert(dto.model()) }

        var checkInsByID: [UUID: CheckIn] = [:]
        for dto in backup.checkIns {
            let model = dto.model()
            context.insert(model)
            checkInsByID[model.id] = model
        }

        // Re-fetch the symptoms we just inserted so links point at live objects.
        var symptomsByID: [UUID: Symptom] = [:]
        for symptom in try context.fetch(FetchDescriptor<Symptom>()) { symptomsByID[symptom.id] = symptom }

        var medicationsByID: [UUID: Medication] = [:]
        for dto in backup.medications {
            let model = dto.model()
            context.insert(model)
            medicationsByID[model.id] = model
        }

        // Join rows: resolve both FKs, skip dangling links.
        for dto in backup.checkInSymptoms {
            guard let checkIn = dto.checkInID.flatMap({ checkInsByID[$0] }),
                  let symptom = dto.symptomID.flatMap({ symptomsByID[$0] }) else { continue }
            context.insert(dto.model(checkIn: checkIn, symptom: symptom))
        }

        for dto in backup.medicationLogs {
            let med = dto.medicationID.flatMap { medicationsByID[$0] }
            context.insert(dto.model(medication: med))
        }

        try context.save()

        return Summary(
            checkIns: backup.checkIns.count,
            symptoms: backup.symptoms.count,
            cycleEntries: backup.cycleEntries.count,
            medications: backup.medications.count,
            medicationLogs: backup.medicationLogs.count,
            activityLogs: backup.activityLogs.count,
            chatMessages: backup.chatMessages.count
        )
    }

    static func restore(data: Data, into context: ModelContext) throws -> Summary {
        try restore(from: decode(data), into: context)
    }

    // MARK: - Helpers

    /// Hard-delete every row of every model type. Deletes objects individually
    /// (not batch `delete(model:)`) in child→parent order, because a batch delete
    /// trips SwiftData's mandatory nullify-inverse constraint between
    /// `CheckInSymptom` and `CheckIn`. Individual deletes honour the cascade rules.
    private static func wipeAll(_ context: ModelContext) throws {
        func purge<T: PersistentModel>(_ type: T.Type) throws {
            for object in try context.fetch(FetchDescriptor<T>()) { context.delete(object) }
        }
        try purge(CheckInSymptom.self)
        try purge(MedicationLog.self)
        try purge(CheckIn.self)
        try purge(Medication.self)
        try purge(Symptom.self)
        try purge(CycleEntry.self)
        try purge(Insight.self)
        try purge(ActivityLog.self)
        try purge(ChatMessage.self)
        try purge(DailySummary.self)
        try purge(HealthSample.self)
        try purge(UserProfile.self)
    }

    private static func fileStamp() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd-HHmm"
        return f.string(from: .now)
    }
}
