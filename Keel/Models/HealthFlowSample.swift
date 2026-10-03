import Foundation
import SwiftData

/// A period day imported from Apple Health (menstrual flow), kept in the **local-only
/// health store** so it never syncs, like `HealthActivitySample`. Her own period days
/// stay in `CycleEntry` (manual only); the Cycle screen shows the two together, her own
/// entry winning for a day, and labels these as from Apple Health.
///
/// Imported days are shown, never used for an estimate: cycle lengths, the next-period
/// window, the phase and Day N read `CycleEntry` only. One row per day, deduped on import.
/// Not `RemoteMappable`, so structurally excluded from any future sync.
@Model
final class HealthFlowSample: Syncable {
    var id: UUID = UUID()
    var date: Date = Date.now
    /// `FlowLevel` raw value.
    var flowRaw: String = FlowLevel.unspecified.rawValue

    var flowLevel: FlowLevel {
        get { FlowLevel(rawValue: flowRaw) ?? .unspecified }
        set { flowRaw = newValue.rawValue }
    }

    // Syncable bookkeeping (local only; never mapped to a remote record).
    var ownerID: String = ""
    var createdAt: Date = Date.now
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.synced.rawValue

    init(id: UUID = UUID(), date: Date, flowLevel: FlowLevel, ownerID: String) {
        self.id = id
        self.date = date.startOfDay
        self.flowRaw = flowLevel.rawValue
        self.ownerID = ownerID
        self.syncStatusRaw = SyncStatus.synced.rawValue
    }
}
