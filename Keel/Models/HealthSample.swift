import Foundation
import SwiftData

/// A daily value imported from Apple Health that has no natural home in Keel's
/// own entities: vitals (heart rate, HRV, resting heart rate, respiratory rate,
/// blood oxygen, weight, body temperatures), workload (active energy, flights),
/// and any symptom occurrence on a day she didn't check in (so nothing she has
/// granted us is lost).
///
/// One row per (`typeID`, `day`): a daily aggregate, not every raw sample, so the
/// store stays small. Read-only mirror of Health; deduped on import by its
/// natural key. Maps to a `health_samples` row.
@Model
final class HealthSample: Syncable {
    @Attribute(.unique) var id: UUID
    /// Stable metric key, e.g. "heartRate", "hrv", "restingHeartRate",
    /// "respiratoryRate", "oxygenSaturation", "bodyMass", "bodyTemperature",
    /// "activeEnergy", "flights", or "symptom.hotFlushes".
    var typeID: String
    /// The calendar day this value is for (start of day).
    var day: Date
    /// The aggregated value (average for vitals, sum for workload, severity 1–3
    /// for a symptom occurrence).
    var value: Double
    /// Display unit, e.g. "bpm", "ms", "kcal", "count", "kg", "°C", "severity".
    var unit: String
    /// Always "healthKit" for now; kept for provenance and future sources.
    var sourceRaw: String

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        typeID: String,
        day: Date,
        value: Double,
        unit: String,
        source: DataSource = .healthKit,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .synced
    ) {
        self.id = id
        self.typeID = typeID
        self.day = day.startOfDay
        self.value = value
        self.unit = unit
        self.sourceRaw = source.rawValue
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var source: DataSource {
        get { DataSource(rawValue: sourceRaw) ?? .healthKit }
        set { sourceRaw = newValue.rawValue }
    }
}
