import Foundation
import SwiftData

/// A reusable symptom in the catalog. Built-in symptoms are seeded on first
/// launch (`isCustom == false`, empty `ownerID` so they're global); user-created
/// ones are `isCustom == true` and owned by the user. Check-ins reference these
/// many-to-many via `CheckInSymptom`.
@Model
final class Symptom: Syncable {
    var id: UUID = UUID()
    var name: String = ""
    var categoryRaw: String = ""
    var isCustom: Bool = false
    /// Hidden from pickers without deleting history.
    var isArchived: Bool = false
    /// Part of the small set shown straight away in the check-in, before "more".
    var isDefaultChip: Bool = false

    /// Inverse of `CheckInSymptom.symptom`. Present only because CloudKit
    /// mirroring requires every relationship to have an inverse; nullify (never
    /// cascade) so removing a link never deletes this shared catalog entry.
    @Relationship(deleteRule: .nullify, inverse: \CheckInSymptom.symptom)
    var checkInLinks: [CheckInSymptom] = []

    // Syncable
    var ownerID: String = ""
    var createdAt: Date = Date.now
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.pendingUpload.rawValue

    init(
        id: UUID = UUID(),
        name: String,
        category: SymptomCategory,
        isCustom: Bool,
        isArchived: Bool = false,
        isDefaultChip: Bool = false,
        ownerID: String,
        createdAt: Date = Date.now,
        updatedAt: Date = Date.now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.name = name
        self.categoryRaw = category.rawValue
        self.isCustom = isCustom
        self.isArchived = isArchived
        self.isDefaultChip = isDefaultChip
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var category: SymptomCategory {
        get { SymptomCategory(rawValue: categoryRaw) ?? .body }
        set { categoryRaw = newValue.rawValue }
    }
}
