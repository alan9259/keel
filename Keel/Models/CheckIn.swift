import Foundation
import SwiftData

@Model
final class CheckIn: Syncable {
    @Attribute(.unique) var id: UUID
    var date: Date
    var moodRaw: String
    /// Energy 0–100.
    var energy: Int
    var notes: String?

    /// Join rows to the shared symptom catalog. Cascades so deleting a check-in
    /// removes its links (but never the `Symptom` catalog entries themselves).
    @Relationship(deleteRule: .cascade, inverse: \CheckInSymptom.checkIn)
    var symptomLinks: [CheckInSymptom]

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        date: Date = .now,
        mood: Mood,
        energy: Int,
        notes: String? = nil,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.date = date
        self.moodRaw = mood.rawValue
        self.energy = energy
        self.notes = notes
        self.symptomLinks = []
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var mood: Mood {
        get { Mood(rawValue: moodRaw) ?? .okay }
        set { moodRaw = newValue.rawValue }
    }

    /// Active (non-tombstoned) symptoms attached to this check-in.
    var symptoms: [Symptom] {
        symptomLinks.compactMap { $0.isTombstoned ? nil : $0.symptom }
    }
}
