import Foundation
import SwiftData

/// A daily reflection stored by earlier builds (partly written by Apple Intelligence).
/// V1 has no generated text: nothing writes these any more, and bootstrap deletes any
/// that exist (`AppEnvironment.purgeStoredReflections`). The model stays in the schema
/// only so existing stores open without a migration.
@Model
final class DailySummary: Syncable {
    var id: UUID = UUID()
    /// The calendar day this summary is for (start of day). One per day.
    var day: Date = Date.now
    /// The reflection shown to her.
    var text: String = ""
    /// "ai" or "deterministic", as written by earlier builds.
    var sourceRaw: String = ""
    /// The grounded facts it was built from, JSON-encoded, kept for the record.
    var signalsJSON: String?
    var generatedAt: Date = Date.now

    // Syncable
    var ownerID: String = ""
    var createdAt: Date = Date.now
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.pendingUpload.rawValue

    init(
        id: UUID = UUID(),
        day: Date,
        text: String,
        source: DailySummarySource,
        signalsJSON: String? = nil,
        generatedAt: Date = Date.now,
        ownerID: String,
        createdAt: Date = Date.now,
        updatedAt: Date = Date.now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.day = day.startOfDay
        self.text = text
        self.sourceRaw = source.rawValue
        self.signalsJSON = signalsJSON
        self.generatedAt = generatedAt
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var source: DailySummarySource {
        get { DailySummarySource(rawValue: sourceRaw) ?? .deterministic }
        set { sourceRaw = newValue.rawValue }
    }
}

enum DailySummarySource: String, Codable {
    case ai
    case deterministic
}
