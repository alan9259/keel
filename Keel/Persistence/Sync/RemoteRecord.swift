import Foundation

/// A single primitive value in a synced record. The cases are deliberately the
/// intersection of what CloudKit `CKRecord` fields and Postgres columns can both
/// hold, so the same DTO maps to either backend.
enum RemoteValue: Equatable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case date(Date)
    case uuid(UUID)

    // Tolerant accessors — backends coerce types (e.g. CloudKit has no Bool/UUID),
    // so decoding reads through whatever representation came back.
    var asString: String? {
        switch self {
        case .string(let v): v
        case .uuid(let v): v.uuidString
        default: nil
        }
    }

    var asInt: Int? {
        switch self {
        case .int(let v): v
        case .double(let v): Int(v)
        case .bool(let v): v ? 1 : 0
        default: nil
        }
    }

    var asDouble: Double? {
        switch self {
        case .double(let v): v
        case .int(let v): Double(v)
        default: nil
        }
    }

    var asBool: Bool? {
        switch self {
        case .bool(let v): v
        case .int(let v): v != 0
        case .double(let v): v != 0
        default: nil
        }
    }

    var asDate: Date? {
        if case .date(let v) = self { return v }
        return nil
    }

    var asUUID: UUID? {
        switch self {
        case .uuid(let v): v
        case .string(let v): UUID(uuidString: v)
        default: nil
        }
    }
}

/// Backend-neutral representation of one persisted row. `SyncProvider`
/// implementations translate this to/from their native record type; nothing
/// above the provider ever sees a `CKRecord` or a Postgres row.
struct RemoteRecord: Equatable {
    let recordType: String
    let id: UUID
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    /// Entity-specific columns (the sync envelope above is handled separately).
    var fields: [String: RemoteValue]
}
