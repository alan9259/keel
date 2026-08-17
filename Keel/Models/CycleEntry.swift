import Foundation
import SwiftData

@Model
final class CycleEntry: Syncable {
    var id: UUID = UUID()
    var date: Date = Date.now
    var typeRaw: String = ""
    /// How heavy the day was. Optional so legacy period days (logged before levels
    /// existed) and CloudKit-mirrored rows decode; nil reads as `.unspecified`.
    var flowLevelRaw: String?
    /// Where this entry came from: "manual" (she logged it) or "healthKit".
    var sourceRaw: String = DataSource.manual.rawValue

    // Syncable
    var ownerID: String = ""
    var createdAt: Date = Date.now
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.pendingUpload.rawValue

    init(
        id: UUID = UUID(),
        date: Date,
        type: CycleEntryType,
        flowLevel: FlowLevel? = nil,
        source: DataSource = .manual,
        ownerID: String,
        createdAt: Date = Date.now,
        updatedAt: Date = Date.now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.date = date
        self.typeRaw = type.rawValue
        self.flowLevelRaw = flowLevel?.rawValue
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

    /// A period day's heaviness. Any period entry without a stored level reads as
    /// `.unspecified` (she logged a period but not how heavy).
    var flowLevel: FlowLevel {
        get { flowLevelRaw.flatMap(FlowLevel.init(rawValue:)) ?? .unspecified }
        set { flowLevelRaw = newValue.rawValue }
    }

    var source: DataSource {
        get { DataSource(rawValue: sourceRaw) ?? .manual }
        set { sourceRaw = newValue.rawValue }
    }
}
