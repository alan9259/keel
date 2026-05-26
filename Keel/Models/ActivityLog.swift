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
        ActivityDef(id: "journal", symbol: "book.fill", label: "Journalling", unit: nil, step: 1, goal: nil),
    ]
}

/// Per-day amount logged for one activity. `amount > 0` means completed.
@Model
final class ActivityLog: Syncable {
    @Attribute(.unique) var id: UUID
    var date: Date
    var activityID: String
    var amount: Double

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        date: Date,
        activityID: String,
        amount: Double,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.date = date.startOfDay
        self.activityID = activityID
        self.amount = amount
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }
}
