import Foundation
import CloudKit

/// CloudKit implementation of `SyncProvider`, targeting the user's **private**
/// database. Records map to `CKRecord`s of the same `recordType` used for the
/// Postgres table name, and sync is timestamp-driven (`updatedAt > cursor`) —
/// the same high-water-mark model a `SupabaseSyncProvider` would use, which
/// keeps the two implementations conceptually identical.
///
/// Runtime prerequisites (need a paid Apple Developer team):
///   • iCloud/CloudKit capability + container on the target.
///   • In the CloudKit dashboard, mark `updatedAt` queryable + sortable, and the
///     record types' `recordName` queryable, for each record type in `RecordType`.
struct CloudKitSyncProvider: SyncProvider {
    let containerIdentifier: String?

    private var database: CKDatabase {
        let container = containerIdentifier.map(CKContainer.init(identifier:)) ?? .default()
        return container.privateCloudDatabase
    }

    // MARK: Push

    func push(_ records: [RemoteRecord]) async throws {
        let ckRecords = records.map(makeCKRecord)
        // Tombstones (deletedAt != nil) are saved like any other record so the
        // soft-delete propagates; we don't hard-delete.
        _ = try await database.modifyRecords(saving: ckRecords, deleting: [], savePolicy: .changedKeys)
    }

    private func makeCKRecord(_ record: RemoteRecord) -> CKRecord {
        let id = CKRecord.ID(recordName: record.id.uuidString)
        let ck = CKRecord(recordType: record.recordType, recordID: id)
        ck["ownerID"] = record.ownerID
        ck["createdAt"] = record.createdAt
        ck["updatedAt"] = record.updatedAt
        if let deletedAt = record.deletedAt { ck["deletedAt"] = deletedAt }
        for (key, value) in record.fields {
            switch value {
            case .string(let v): ck[key] = v
            case .int(let v): ck[key] = v
            case .double(let v): ck[key] = v
            case .bool(let v): ck[key] = v
            case .date(let v): ck[key] = v
            case .uuid(let v): ck[key] = v.uuidString
            }
        }
        return ck
    }

    // MARK: Pull

    func pull(since cursor: Data?) async throws -> SyncPullResult {
        let since = TimestampCursor.decode(cursor)
        var pulled: [RemoteRecord] = []

        for recordType in RecordType.all {
            let predicate: NSPredicate = since.map { NSPredicate(format: "updatedAt > %@", $0 as NSDate) }
                ?? NSPredicate(value: true)
            let query = CKQuery(recordType: recordType, predicate: predicate)
            query.sortDescriptors = [NSSortDescriptor(key: "updatedAt", ascending: true)]

            do {
                let (matches, _) = try await database.records(matching: query)
                for (_, result) in matches {
                    if case .success(let ckRecord) = result {
                        pulled.append(remoteRecord(from: ckRecord))
                    }
                }
            } catch let error as CKError where error.code == .unknownItem {
                // Record type not yet present in the schema — nothing to pull.
                continue
            }
        }

        let maxUpdated = pulled.map(\.updatedAt).max()
        let newCursor = maxUpdated.map(TimestampCursor.encode) ?? cursor
        return SyncPullResult(records: pulled, cursor: newCursor)
    }

    private func remoteRecord(from ck: CKRecord) -> RemoteRecord {
        let envelopeKeys: Set<String> = ["ownerID", "createdAt", "updatedAt", "deletedAt"]
        var fields: [String: RemoteValue] = [:]
        for key in ck.allKeys() where !envelopeKeys.contains(key) {
            guard let value = ck[key] else { continue }
            if let s = value as? String {
                fields[key] = .string(s)
            } else if let d = value as? Date {
                fields[key] = .date(d)
            } else if let n = value as? NSNumber {
                fields[key] = .double(n.doubleValue)
            }
        }
        let id = UUID(uuidString: ck.recordID.recordName) ?? UUID()
        return RemoteRecord(
            recordType: ck.recordType,
            id: id,
            ownerID: (ck["ownerID"] as? String) ?? "",
            createdAt: (ck["createdAt"] as? Date) ?? ck.creationDate ?? .now,
            updatedAt: (ck["updatedAt"] as? Date) ?? ck.modificationDate ?? .now,
            deletedAt: ck["deletedAt"] as? Date,
            fields: fields
        )
    }
}
