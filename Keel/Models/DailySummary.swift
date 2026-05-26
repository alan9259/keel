import Foundation
import SwiftData

/// A once-a-day reflection, kept over time so past patterns are recorded rather
/// than only regenerated. The narrative is written by Apple Intelligence from
/// grounded facts, or a plain deterministic summary when on-device AI isn't
/// available. Maps to a `daily_summaries` row.
@Model
final class DailySummary: Syncable {
    @Attribute(.unique) var id: UUID
    /// The calendar day this summary is for (start of day). One per day.
    var day: Date
    /// The reflection shown to her.
    var text: String
    /// "ai" (Apple Intelligence narrated) or "deterministic" (plain fallback).
    var sourceRaw: String
    /// The grounded facts it was built from, JSON-encoded, kept for the record.
    var signalsJSON: String?
    var generatedAt: Date

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        day: Date,
        text: String,
        source: DailySummarySource,
        signalsJSON: String? = nil,
        generatedAt: Date = .now,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
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
