import Foundation
import SwiftData

/// Moves every locally-owned row from one `ownerID` to another. Used when she
/// upgrades a "continue on this device" identity to a real Sign in with Apple
/// account: her existing data must follow the new owner id so it uploads under that
/// account once server sync is live.
///
/// Extracted from `AppEnvironment` as a pure store operation so it can be tested on
/// an in-memory context. Built-in reference rows (global `ownerID == ""`) never
/// match a `local-…` owner, so they stay shared as intended.
enum OwnershipMigration {
    /// Every owned model type. New `Syncable` `@Model`s must be added here so an
    /// account upgrade carries them too.
    @MainActor
    @discardableResult
    static func reassign(in context: ModelContext, from old: String, to new: String) -> Int {
        guard !old.isEmpty, old != new else { return 0 }
        var moved = 0
        moved += restamp(CheckIn.self, in: context, from: old, to: new)
        moved += restamp(CheckInSymptom.self, in: context, from: old, to: new)
        moved += restamp(Symptom.self, in: context, from: old, to: new)
        moved += restamp(ActivityLog.self, in: context, from: old, to: new)
        moved += restamp(HealthSample.self, in: context, from: old, to: new)
        moved += restamp(CycleEntry.self, in: context, from: old, to: new)
        moved += restamp(Medication.self, in: context, from: old, to: new)
        moved += restamp(MedicationLog.self, in: context, from: old, to: new)
        moved += restamp(DailySummary.self, in: context, from: old, to: new)
        moved += restamp(Insight.self, in: context, from: old, to: new)
        moved += restamp(ChatMessage.self, in: context, from: old, to: new)
        moved += restamp(UserProfile.self, in: context, from: old, to: new)
        try? context.save()
        return moved
    }

    @MainActor
    private static func restamp<T>(_ type: T.Type, in context: ModelContext, from old: String, to new: String) -> Int
        where T: PersistentModel, T: Syncable {
        let rows = (try? context.fetch(FetchDescriptor<T>())) ?? []
        var moved = 0
        for row in rows where row.ownerID == old {
            row.ownerID = new
            row.touch() // queue it for the next sync pass under the new owner
            moved += 1
        }
        return moved
    }
}
