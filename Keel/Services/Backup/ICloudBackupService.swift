import Foundation
import CloudKit
import SwiftData

/// Real iCloud backup: stores the whole `KeelBackup` archive as a single CKAsset
/// in the user's **private** CloudKit database, so she can restore her data on a
/// new device from her own iCloud account. This is a snapshot backup, distinct
/// from the live per-record `SyncProvider` sync (both use the same container).
///
/// One record ("backup.current") holds the latest archive plus its date and app
/// version; backing up again overwrites it. Requires the iCloud/CloudKit
/// capability on a signed build with an iCloud account — on the Simulator (and
/// unsigned builds) iCloud is unavailable, so `availability()` reports that
/// honestly and the UI disables the actions rather than pretending.
@MainActor
final class ICloudBackupService {
    private let containerIdentifier: String?
    private let context: ModelContext

    private let recordType = "KeelBackupArchive"
    private let recordName = "backup.current"

    init(containerIdentifier: String?, context: ModelContext) {
        self.containerIdentifier = containerIdentifier
        self.context = context
    }

    private var ckContainer: CKContainer {
        containerIdentifier.map(CKContainer.init(identifier:)) ?? .default()
    }
    private var database: CKDatabase { ckContainer.privateCloudDatabase }

    // MARK: Availability

    enum Availability: Equatable {
        case available
        case unavailable(reason: String)

        var isAvailable: Bool { if case .available = self { return true } else { return false } }
    }

    func availability() async -> Availability {
        #if targetEnvironment(simulator)
        // iCloud/CloudKit can't run on the unsigned Simulator. Be honest rather
        // than showing a fake "connected" state.
        return .unavailable(reason: "iCloud backup works on a signed build on your device, once you're signed in to iCloud.")
        #else
        do {
            switch try await ckContainer.accountStatus() {
            case .available: return .available
            case .noAccount: return .unavailable(reason: "Sign in to iCloud in Settings to back up.")
            case .restricted: return .unavailable(reason: "iCloud is restricted on this device.")
            case .temporarilyUnavailable: return .unavailable(reason: "iCloud is temporarily unavailable. Try again soon.")
            default: return .unavailable(reason: "iCloud isn't available right now.")
            }
        } catch {
            return .unavailable(reason: "Couldn't reach iCloud. Check your connection and try again.")
        }
        #endif
    }

    // MARK: Metadata

    struct Info: Equatable {
        let date: Date
        let bytes: Int
        let appVersion: String?
    }

    /// The stored backup's metadata, or nil if none exists yet.
    func latest() async throws -> Info? {
        do {
            let record = try await database.record(for: CKRecord.ID(recordName: recordName))
            let date = (record["exportedAt"] as? Date) ?? record.modificationDate ?? .now
            let bytes = (record["archive"] as? CKAsset)?.fileURL
                .flatMap { try? Data(contentsOf: $0).count } ?? 0
            return Info(date: date, bytes: bytes, appVersion: record["appVersion"] as? String)
        } catch let error as CKError where error.code == .unknownItem {
            return nil // no backup stored yet
        }
    }

    // MARK: Back up

    /// Snapshot the store and upload it, overwriting the previous backup. Returns
    /// the backup's timestamp.
    @discardableResult
    func backUpNow() async throws -> Date {
        let data = try BackupService.exportData(context: context)
        let exportedAt = Date()

        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("keel-icloud.keelbackup")
        try data.write(to: url, options: .atomic)
        defer { try? FileManager.default.removeItem(at: url) }

        let id = CKRecord.ID(recordName: recordName)
        let record = (try? await database.record(for: id)) ?? CKRecord(recordType: recordType, recordID: id)
        record["archive"] = CKAsset(fileURL: url)
        record["exportedAt"] = exportedAt
        record["appVersion"] = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String

        _ = try await database.save(record)
        return exportedAt
    }

    // MARK: Restore

    /// Fetch the stored archive and replace all local data with it.
    @discardableResult
    func restore() async throws -> BackupService.Summary {
        let record: CKRecord
        do {
            record = try await database.record(for: CKRecord.ID(recordName: recordName))
        } catch let error as CKError where error.code == .unknownItem {
            throw BackupError.noICloudBackup
        }
        guard let url = (record["archive"] as? CKAsset)?.fileURL else {
            throw BackupError.noICloudBackup
        }
        let data = try Data(contentsOf: url)
        return try BackupService.restore(data: data, into: context)
    }

    enum BackupError: LocalizedError {
        case noICloudBackup
        var errorDescription: String? {
            switch self {
            case .noICloudBackup: "There's no iCloud backup to restore yet."
            }
        }
    }
}
