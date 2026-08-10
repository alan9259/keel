import Foundation

/// Upload state for a record's local changes.
enum SyncStatus: String, Codable {
    case pendingUpload
    case synced
}

/// Every persisted entity conforms to `Syncable`. These fields are exactly what a
/// backend-agnostic sync layer needs, and they map 1:1 onto both a CloudKit
/// `CKRecord` and a Postgres row — which is what makes the eventual Supabase
/// migration a drop-in swap rather than a rewrite:
///
/// - `id`            → CKRecord.ID name  /  Postgres `uuid` primary key
/// - `ownerID`       → record owner scope /  Postgres `owner_id` (RLS: `= auth.uid()`)
/// - `createdAt/updatedAt` → last-write-wins conflict resolution + delta sync cursor
/// - `deletedAt`     → soft-delete tombstone (hard deletes don't propagate cleanly)
/// - `syncStatus`    → local-only bookkeeping (never written to the remote)
protocol Syncable: AnyObject {
    var id: UUID { get }
    var ownerID: String { get set }
    var createdAt: Date { get set }
    var updatedAt: Date { get set }
    var deletedAt: Date? { get set }
    var syncStatusRaw: String { get set }
}

extension Syncable {
    var syncStatus: SyncStatus {
        get { SyncStatus(rawValue: syncStatusRaw) ?? .pendingUpload }
        set { syncStatusRaw = newValue.rawValue }
    }

    /// True once the record has been tombstoned (soft-deleted).
    var isTombstoned: Bool { deletedAt != nil }

    /// Mark the record dirty so the next sync pass uploads it.
    func touch(_ now: Date = Date.now) {
        updatedAt = now
        syncStatusRaw = SyncStatus.pendingUpload.rawValue
    }

    /// Soft-delete: write a tombstone and queue it for upload.
    func softDelete(_ now: Date = Date.now) {
        deletedAt = now
        touch(now)
    }
}
