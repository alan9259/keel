import Foundation

/// The single seam that makes the backend swappable. `CloudKitSyncProvider`
/// implements it today; a `SupabaseSyncProvider` implementing the *same*
/// protocol is all that's needed to migrate — no repository, view, or model
/// changes. The `SyncEngine` only ever talks to this.
protocol SyncProvider: Sendable {
    /// Upload local changes (creates, updates, and tombstones).
    func push(_ records: [RemoteRecord]) async throws

    /// Fetch remote changes since the opaque `cursor` (nil = full/initial pull).
    /// The cursor is provider-defined (a CloudKit change token or a Supabase
    /// `updated_at` high-water mark) and is persisted by the engine between runs.
    func pull(since cursor: Data?) async throws -> SyncPullResult
}

struct SyncPullResult {
    var records: [RemoteRecord]
    /// Opaque cursor to persist and pass to the next `pull`.
    var cursor: Data?
}

enum SyncError: Error {
    case notAuthenticated
    case backendUnavailable(String)
}

/// A timestamp high-water-mark cursor, shared by the CloudKit and (future)
/// Supabase providers so both express "give me everything changed after T".
enum TimestampCursor {
    static func encode(_ date: Date) -> Data {
        withUnsafeBytes(of: date.timeIntervalSince1970) { Data($0) }
    }

    static func decode(_ data: Data?) -> Date? {
        guard let data, data.count == MemoryLayout<Double>.size else { return nil }
        let interval = data.withUnsafeBytes { $0.load(as: Double.self) }
        return Date(timeIntervalSince1970: interval)
    }
}
