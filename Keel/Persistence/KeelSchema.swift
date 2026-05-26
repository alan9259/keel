import Foundation
import SwiftData

/// Central definition of the SwiftData schema + container construction.
///
/// SwiftData is the local source of truth. We deliberately do **not** attach
/// SwiftData's automatic CloudKit mirroring here — sync goes through the
/// backend-agnostic `SyncEngine`/`SyncProvider` instead, which is what keeps the
/// Supabase migration a drop-in swap.
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
        // `cloudKitDatabase: .none` is load-bearing: the default is `.automatic`,
        // which turns on SwiftData's CloudKit mirroring whenever the app carries the
        // CloudKit entitlement (it does, on a signed build). CloudKit then validates
        // the schema against its rules and rejects it — every model has an
        // `@Attribute(.unique) id`, which CloudKit forbids — so the container fails
        // to open and the app traps on launch. We sync through `SyncProvider`, not
        // SwiftData's mirroring, so we disable it explicitly. (This only bit on
        // device: the unsigned simulator has no entitlement, so `.automatic` stayed
        // off there and hid it.)
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
