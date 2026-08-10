import Foundation
import SwiftData

/// A per-day "taken / not taken" record for a medication.
@Model
final class MedicationLog: Syncable {
    var id: UUID = UUID()
    /// Start-of-day the log applies to.
    var date: Date = Date.now
    /// Which dose of that day, as the `ScheduledTime` id. Nil is the whole day,
    /// which is what an entry with no set times has.
    var slot: String?
    var taken: Bool = false
    var medication: Medication?

    // Syncable
    var ownerID: String = ""
    var createdAt: Date = Date.now
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.pendingUpload.rawValue

    init(
        id: UUID = UUID(),
        date: Date,
        slot: String? = nil,
        taken: Bool,
        medication: Medication?,
        ownerID: String,
        createdAt: Date = Date.now,
        updatedAt: Date = Date.now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.date = date
        self.slot = slot
        self.taken = taken
        self.medication = medication
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var medicationID: UUID? { medication?.id }
}
