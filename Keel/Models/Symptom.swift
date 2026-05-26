import Foundation
import SwiftData

/// A reusable symptom in the catalog. Built-in symptoms are seeded on first
/// launch (`isCustom == false`, empty `ownerID` so they're global); user-created
/// ones are `isCustom == true` and owned by the user. Check-ins reference these
/// many-to-many via `CheckInSymptom`.
@Model
final class Symptom: Syncable {
    @Attribute(.unique) var id: UUID
    var name: String
    var categoryRaw: String
    var isCustom: Bool
    /// Hidden from pickers without deleting history.
    var isArchived: Bool
    /// Part of the small set shown straight away in the check-in, before "more".
    var isDefaultChip: Bool = false

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        name: String,
        category: SymptomCategory,
        isCustom: Bool,
        isArchived: Bool = false,
        isDefaultChip: Bool = false,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
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
