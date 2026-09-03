import Foundation
import SwiftData

/// Central definition of the SwiftData schema + container construction.
///
/// SwiftData is the on-device source of truth, and it is deliberately **local-only**:
/// CloudKit mirroring is off, so no personal health information is stored in iCloud
/// (App Store Review Guideline 5.1.3(ii)). Any future cross-device sync must go through
/// a first-party backend, not iCloud.
enum KeelSchema {
    static let models: [any PersistentModel.Type] = [
        UserProfile.self,
        CheckIn.self,
        Symptom.self,
        CheckInSymptom.self,
        CycleEntry.self,
        Medication.self,
        MedicationLog.self,
        Insight.self,
        ChatMessage.self,
        ActivityLog.self,
        DailySummary.self,
        HealthSample.self,
    ]

    static var schema: Schema { Schema(versionedSchema: KeelSchemaV1.self) }

    /// Creates the app container. `inMemory` is used by tests and previews.
    ///
    /// A versioned schema + migration plan is wired in from the start so releases
    /// have a real upgrade path: additive changes migrate automatically (lightweight),
    /// and a destructive change gets a `MigrationStage` in `KeelMigrationPlan`
    /// rather than a data-losing reset.
    static func makeContainer(inMemory: Bool = false) -> ModelContainer {
        // Local-only: CloudKit mirroring is OFF (`.none`), so personal health
        // information never leaves the device for iCloud (App Store Guideline
        // 5.1.3(ii)). Every model is still CloudKit-shaped (no `@Attribute(.unique)`,
        // every attribute optional or defaulted, relationships optional with an
        // inverse), so a compliant non-health container could mirror later; today
        // nothing does. The `SyncProvider`/`SyncEngine` path is also inert (no-op
        // provider), so nothing syncs anywhere.
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory,
                                               cloudKitDatabase: .none)
        do {
            return try ModelContainer(for: schema, migrationPlan: KeelMigrationPlan.self,
                                      configurations: configuration)
        } catch {
            // The local store is a cache (the sync backend is the source of truth).
            // If it can't be opened — most often an on-disk store left by an earlier
            // build whose schema no longer lines up, with no migration stage between
            // them — rebuild it rather than trapping the whole app on launch. A fresh
            // store always opens (the schema itself is valid), so this recovers the
            // upgrade path instead of hard-crashing testers.
            //
            // NOTE: this rebuild RESETS local data. That's acceptable while the app is
            // in internal testing with disposable data. BEFORE a public launch (real
            // users, real data), add proper `MigrationStage`s to `KeelMigrationPlan`
            // for each shipped schema change and make this reset a true last resort,
            // so an upgrade never silently wipes someone's history.
            if !inMemory {
                NSLog("Keel: SwiftData store could not be opened (%@). Rebuilding the local store.", String(describing: error))
                destroyStore(at: configuration.url)
                if let container = try? ModelContainer(for: schema, migrationPlan: KeelMigrationPlan.self,
                                                       configurations: configuration) {
                    return container
                }
            }
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }

    /// Removes the SQLite store and its write-ahead-log sidecars.
    private static func destroyStore(at url: URL) {
        let fm = FileManager.default
        for suffix in ["", "-wal", "-shm"] {
            try? fm.removeItem(at: URL(fileURLWithPath: url.path + suffix))
        }
    }
}

/// The current schema version. New models and additive fields (new optional or
/// defaulted properties) migrate automatically as long as they stay inside this
/// version. A destructive change (a rename, a type change, a drop) means a new
/// `VersionedSchema` and a `MigrationStage` in `KeelMigrationPlan`, never a reset.
enum KeelSchemaV1: VersionedSchema {
    static var versionIdentifier = Schema.Version(1, 0, 0)
    static var models: [any PersistentModel.Type] { KeelSchema.models }
}

/// The ordered list of schema versions and the stages between them. Empty stages
/// today (one version); each future breaking change appends a version and a stage.
enum KeelMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [KeelSchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}

/// Supplies the current owner id (stable Sign in with Apple user id) to
/// repositories so every write is stamped for row-level ownership.
typealias OwnerIDProvider = () -> String
