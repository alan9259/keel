import Foundation
import SwiftData

/// A narrative pattern insight card shown on the Patterns page. Derived for real
/// from her own data by `PatternEngine` via `InsightRepository.refreshDerived()`
/// (no invented statistics); this is the stored, displayable form.
@Model
final class Insight: Syncable {
    var id: UUID = UUID()
    var title: String = ""
    var detail: String = ""
    var timeframe: String = ""
    /// SF Symbol name for the leading icon.
    var iconKey: String = ""
    /// Accent key: "terracotta" | "sage" | "warmGrey".
    var accentRaw: String = ""
    var generatedAt: Date = Date.now

    // Syncable
    var ownerID: String = ""
    var createdAt: Date = Date.now
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.pendingUpload.rawValue

    init(
        id: UUID = UUID(),
        title: String,
        detail: String,
        timeframe: String,
        iconKey: String,
        accent: InsightAccent,
        generatedAt: Date = Date.now,
        ownerID: String,
        createdAt: Date = Date.now,
        updatedAt: Date = Date.now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.title = title
        self.detail = detail
        self.timeframe = timeframe
        self.iconKey = iconKey
        self.accentRaw = accent.rawValue
        self.generatedAt = generatedAt
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var accent: InsightAccent {
        get { InsightAccent(rawValue: accentRaw) ?? .terracotta }
        set { accentRaw = newValue.rawValue }
    }
}

enum InsightAccent: String, Codable {
    case terracotta
    case sage
    case warmGrey
}
