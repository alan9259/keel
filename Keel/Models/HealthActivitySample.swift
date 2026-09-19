import Foundation
import SwiftData

/// A daily activity amount imported from Apple Health (sleep, steps, exercise),
/// kept in the **local-only health store** so it never syncs. Mirrors
/// `ActivityLog`'s shape (activityID/day/amount) so readers can union the two: manual
/// entries live in `ActivityLog`, imported ones here. One row per (`activityID`, `day`),
/// deduped on import.
///
/// This type is deliberately **not** `RemoteMappable` and is assigned to the health
/// `ModelConfiguration`, so it is structurally excluded from any future sync.
@Model
final class HealthActivitySample: Syncable {
    var id: UUID = UUID()
    var date: Date = Date.now
    var activityID: String = ""
    var amount: Double = 0

    // Syncable bookkeeping (local only; never mapped to a remote record).
    var ownerID: String = ""
    var createdAt: Date = Date.now
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.synced.rawValue

    init(
        id: UUID = UUID(),
        date: Date,
        activityID: String,
        amount: Double,
        ownerID: String,
        createdAt: Date = Date.now,
        updatedAt: Date = Date.now,
        deletedAt: Date? = nil
    ) {
        self.id = id
        self.date = date.startOfDay
        self.activityID = activityID
        self.amount = amount
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = SyncStatus.synced.rawValue
    }
}
