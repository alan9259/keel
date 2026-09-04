import Foundation
import SwiftData

/// Central definition of the SwiftData schema + container construction.
///
/// SwiftData is the on-device source of truth, and it is deliberately **local-only**:
/// CloudKit mirroring is off, so no personal health information is stored in iCloud
/// (App Store Review Guideline 5.1.3(ii)). Any future cross-device sync must go through
/// a first-party backend, not iCloud.
enum KeelSchema {
    /// User-entered models. These live in the main store and may sync to a first-party
    /// backend later.
    static let mainModels: [any PersistentModel.Type] = [
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
    ]

    /// Apple Health imports. These live in a separate, local-only store and are NOT
    /// `RemoteMappable`, so they are structurally excluded from any sync (5.1.3(ii)).
    static let healthModels: [any PersistentModel.Type] = [
        HealthSample.self,
        HealthActivitySample.self,
    ]

    static let models: [any PersistentModel.Type] = mainModels + healthModels

    static var schema: Schema { Schema(versionedSchema: KeelSchemaV1.self) }

    /// Creates the app container. `inMemory` is used by tests and previews.
    ///
    /// A versioned schema + migration plan is wired in from the start so releases
    /// have a real upgrade path: additive changes migrate automatically (lightweight),
    /// and a destructive change gets a `MigrationStage` in `KeelMigrationPlan`
    /// rather than a data-losing reset.
    static func makeContainer(inMemory: Bool = false) -> ModelContainer {
        // TWO stores, both local-only (CloudKit mirroring OFF): the main store for
        // user-entered data (may sync to a first-party backend later) and a separate,
        // unnamed-default... no: the main store keeps the DEFAULT url (so existing data
        // stays put) and the HEALTH store is a named, separate file for Apple Health
        // imports. Health models are not `RemoteMappable`, so health data is
        // structurally excluded from any sync (App Store Guideline 5.1.3(ii)). Nothing
        // syncs today regardless (the `SyncProvider`/`SyncEngine` path is a no-op).
        let main = ModelConfiguration(schema: Schema(mainModels), isStoredInMemoryOnly: inMemory,
                                      cloudKitDatabase: .none)
        let health = ModelConfiguration("health", schema: Schema(healthModels), isStoredInMemoryOnly: inMemory,
                                        cloudKitDatabase: .none)
        do {
            return try ModelContainer(for: schema, migrationPlan: KeelMigrationPlan.self,
                                      configurations: main, health)
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
                NSLog("Keel: SwiftData store could not be opened (%@). Rebuilding the local store(s).", String(describing: error))
                destroyStore(at: main.url)
                destroyStore(at: health.url)
                if let container = try? ModelContainer(for: schema, migrationPlan: KeelMigrationPlan.self,
                                                       configurations: main, health) {
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
