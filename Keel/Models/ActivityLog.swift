import Foundation
import SwiftData

/// A lifestyle activity the user can log daily (steps, water, sleep, …).
struct ActivityDef: Identifiable {
    let id: String
    let symbol: String
    let label: String
    /// Unit for value-based activities (e.g. "glasses"); nil = simple done toggle.
    let unit: String?
    let step: Double
    let goal: Double?
}

enum ActivityCatalog {
    static let all: [ActivityDef] = [
        ActivityDef(id: "steps", symbol: "figure.walk", label: "Steps / Walk", unit: nil, step: 1, goal: nil),
        ActivityDef(id: "exercise", symbol: "figure.strengthtraining.traditional", label: "Exercise", unit: nil, step: 1, goal: nil),
        ActivityDef(id: "water", symbol: "drop.fill", label: "Water intake", unit: "glasses", step: 1, goal: 8),
        ActivityDef(id: "sleep", symbol: "moon.fill", label: "Sleep", unit: "hrs", step: 0.5, goal: 8),
        ActivityDef(id: "meditation", symbol: "wind", label: "Meditation / Breathwork", unit: nil, step: 1, goal: nil),
        ActivityDef(id: "eating", symbol: "fork.knife", label: "Healthy eating", unit: nil, step: 1, goal: nil),
    ]
}

/// Per-day amount logged for one activity. `amount > 0` means completed.
@Model
final class ActivityLog: Syncable {
    var id: UUID = UUID()
    var date: Date = Date.now
    var activityID: String = ""
    var amount: Double = 0
    /// Where this row came from. Lets Apple Health be the source of truth for a
    /// metric like sleep (a `.healthKit` row is authoritative and shown read-only)
    /// while she can still type a value for a day Health doesn't cover (`.manual`),
    /// so the two never compete over the same day. Defaults to `.manual` so any
    /// existing row reads as hers.
    var sourceRaw: String = DataSource.manual.rawValue

    // Syncable
    var ownerID: String = ""
    var createdAt: Date = Date.now
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.pendingUpload.rawValue

    var source: DataSource {
        get { DataSource(rawValue: sourceRaw) ?? .manual }
        set { sourceRaw = newValue.rawValue }
    }

    init(
        id: UUID = UUID(),
        date: Date,
        activityID: String,
        amount: Double,
        source: DataSource = .manual,
        ownerID: String,
        createdAt: Date = Date.now,
        updatedAt: Date = Date.now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.date = date.startOfDay
        self.activityID = activityID
        self.amount = amount
        self.sourceRaw = source.rawValue
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }
}
