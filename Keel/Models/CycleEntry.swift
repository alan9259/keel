import Foundation
import SwiftData

@Model
final class CycleEntry: Syncable {
    @Attribute(.unique) var id: UUID
    var date: Date
    var typeRaw: String
    /// Where this entry came from: "manual" (she logged it) or "healthKit".
    var sourceRaw: String = DataSource.manual.rawValue

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        date: Date,
        type: CycleEntryType,
        source: DataSource = .manual,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.date = date
        self.typeRaw = type.rawValue
        self.sourceRaw = source.rawValue
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var type: CycleEntryType {
        get { CycleEntryType(rawValue: typeRaw) ?? .flow }
        set { typeRaw = newValue.rawValue }
    }

    var source: DataSource {
        get { DataSource(rawValue: sourceRaw) ?? .manual }
        set { sourceRaw = newValue.rawValue }
    }
}
