import Foundation

/// The provider used until CloudKit entitlements (which need a paid Apple
/// Developer team) are wired up, and on the unsigned Simulator. It accepts pushes
/// and returns nothing, so data stays local. `AppEnvironment.makeProvider` swaps
/// in `CloudKitSyncProvider` on a signed device.
struct NoopSyncProvider: SyncProvider {
    func push(_ records: [RemoteRecord]) async throws {}
    func pull(since cursor: Data?) async throws -> SyncPullResult {
        SyncPullResult(records: [], cursor: cursor)
    }
}
