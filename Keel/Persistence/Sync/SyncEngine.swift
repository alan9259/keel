import Foundation
import SwiftData
import OSLog

/// Orchestrates local ⇄ remote sync. It knows nothing about CloudKit or
/// Supabase — it collects `pendingUpload` rows, pushes them through the injected
/// `SyncProvider`, then pulls and applies remote changes. Swapping the provider
/// swaps the backend.
@MainActor
@Observable
final class SyncEngine {
    private let context: ModelContext
    private let provider: SyncProvider
    private let cursorKey = "keel.sync.cursor"
    private let logger = Logger(subsystem: "com.therecalibrationyears.keel", category: "sync")

    private(set) var isSyncing = false
    private(set) var lastSyncedAt: Date?

    init(context: ModelContext, provider: SyncProvider) {
        self.context = context
        self.provider = provider
    }

    /// Push local changes, then pull remote ones. Safe to call on app-active,
    /// after a check-in, etc. Failures are logged, not fatal (offline-friendly).
    func syncNow() async {
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }
        await pushPending()
        await pull()
        lastSyncedAt = .now
    }

    // MARK: Push

    private func pushPending() async {
        let pending = pendingModels()
        guard !pending.isEmpty else { return }
        let records = pending.map { $0.toRemoteRecord() }
        do {
            try await provider.push(records)
            for model in pending { model.syncStatus = .synced }
            try? context.save()
            logger.info("Pushed \(records.count) record(s).")
        } catch {
            logger.error("Push failed: \(error.localizedDescription)")
        }
    }

    private func pendingModels() -> [any RemoteMappable] {
        var out: [any RemoteMappable] = []
        out += fetchPending(UserProfile.self)
        out += fetchPending(CheckIn.self)
        out += fetchPending(Symptom.self)
        out += fetchPending(CheckInSymptom.self)
        out += fetchPending(CycleEntry.self)
        out += fetchPending(Medication.self)
        out += fetchPending(MedicationLog.self)
        out += fetchPending(Insight.self)
        out += fetchPending(ActivityLog.self)
        out += fetchPending(ChatMessage.self)
        return out
    }

    private func fetchPending<T: PersistentModel & RemoteMappable>(_ type: T.Type) -> [T] {
        // A `#Predicate` over a GENERIC model type builds a key path (`\T.syncStatusRaw`)
        // that SwiftData can't resolve to a column in optimized Release builds — it
        // traps in `graph_keyPathToString` (only worked in Debug on the simulator).
        // Fetch the type's rows unfiltered and filter in memory; per-user volume is
        // tiny. Reproduce/verify with a RELEASE build (Debug hides this): `xcodebuild
        // -configuration Release -sdk iphonesimulator`.
        let pending = SyncStatus.pendingUpload.rawValue
        let rows = (try? context.fetch(FetchDescriptor<T>())) ?? []
        return rows.filter { $0.syncStatusRaw == pending }
    }

    // MARK: Pull

    private func pull() async {
        do {
            let result = try await provider.pull(since: loadCursor())
            if !result.records.isEmpty {
                RemoteApplier(context: context).apply(result.records)
                logger.info("Applied \(result.records.count) remote record(s).")
            }
            saveCursor(result.cursor)
        } catch {
            logger.error("Pull failed: \(error.localizedDescription)")
        }
    }

    private func loadCursor() -> Data? {
        UserDefaults.standard.data(forKey: cursorKey)
    }

    private func saveCursor(_ data: Data?) {
        if let data {
            UserDefaults.standard.set(data, forKey: cursorKey)
        }
    }
}

extension RemoteMappable {
    func toRemoteRecord() -> RemoteRecord { makeRemoteRecord(self) }
}
