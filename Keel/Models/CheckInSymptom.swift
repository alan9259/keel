import Foundation
import SwiftData

/// Join row linking a `CheckIn` to a `Symptom` (many-to-many). Maps to the
/// Postgres `check_in_symptoms` table (columns `check_in_id`, `symptom_id`).
@Model
final class CheckInSymptom: Syncable {
    @Attribute(.unique) var id: UUID
    var checkIn: CheckIn?
    /// Nullify (not cascade) — removing a link must never delete the shared
    /// catalog `Symptom`.
    @Relationship(deleteRule: .nullify)
    var symptom: Symptom?
    /// Severity felt on this check-in: 1 mild, 2 moderate, 3 severe.
    var severity: Int = 1
    /// Where this link came from: "manual" (she logged it) or "healthKit".
    var sourceRaw: String = DataSource.manual.rawValue

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        checkIn: CheckIn?,
        symptom: Symptom?,
        severity: Int = 1,
        source: DataSource = .manual,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.checkIn = checkIn
        self.symptom = symptom
        self.severity = severity
        self.sourceRaw = source.rawValue
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var source: DataSource {
        get { DataSource(rawValue: sourceRaw) ?? .manual }
        set { sourceRaw = newValue.rawValue }
    }

    /// Foreign keys for the sync layer / Postgres mapping.
    var checkInID: UUID? { checkIn?.id }
    var symptomID: UUID? { symptom?.id }
}
