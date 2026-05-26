import Foundation
import SwiftData

/// Something she is taking: a prescribed treatment or a supplement. Keel records
/// what she tells us she takes. It never suggests, doses, or compares products.
@Model
final class Medication: Syncable {
    @Attribute(.unique) var id: UUID
    var name: String
    /// Rendered dose, e.g. "400mg". Kept as the readable form for reports, the GP
    /// summary and sync; `doseAmount` + `doseUnit` are what she edits.
    var dosage: String
    var doseAmount: Double?
    var doseUnitRaw: String?
    /// Rendered timing, e.g. "8:00 am". Mirrors the schedule's time, and holds
    /// free text from entries made before schedules existed.
    var timing: String
    var methodRaw: String?
    var isActive: Bool
    /// Whether this medicine appears in the home "Medicines" log. It's the tick on
    /// the Medications screen: on means "track this, show it in the log", where she
    /// records each day whether she took it. It is NOT a record of a dose taken.
    /// On by default when she adds something, since that's usually the point.
    var isTracked: Bool = true
    /// Opt-in convenience: mark this medicine's doses taken without a tap. Enabled
    /// from the reminder's "Always mark taken" action or the edit form. When on,
    /// today's due doses are logged taken while the app is open (iOS can't run
    /// while it's closed, so a day she never opens the app isn't logged). Off by
    /// default — nothing is ever recorded as taken unless she opts in.
    var autoLogDoses: Bool = false

    // MARK: Schedule
    //
    // Stored in parts so they map cleanly onto a row in any backend; the rules
    // live in `DoseSchedule`, which `schedule` hands back.

    var scheduleKindRaw: String?
    /// Bitfield of `Calendar` weekdays: bit 0 is Sunday through bit 6 Saturday.
    var weekdaysMask: Int = 0
    var cycleLength: Int = 28
    var cyclePauseDays: Int = 7
    /// Day 1 of the cycle.
    var cycleAnchor: Date?
    /// Times of day, JSON encoded. A list rather than a column because it's an
    /// attribute of the schedule, never queried on its own, and always read with
    /// its parent. Maps onto a `jsonb` column just as cleanly.
    var doseTimesJSON: String?
    /// The single time entries carried before there could be several. Read once
    /// as a fallback, then superseded.
    var doseTime: Date?

    /// Treatment or supplement. Grouping only.
    var kindRaw: String = TreatmentKind.supplement.rawValue
    /// Catalog group it was picked from, e.g. "oestrogen-patches". Empty when she
    /// typed her own.
    var catalogGroupID: String?
    /// Superseded by the schedule. Read once by the migration, then left alone.
    var frequencyRaw: String?
    /// Superseded by the schedule's time. Same story.
    var timeOfDayRaw: String?
    /// When she started recording it. Captured for her, not asked for.
    var date: Date?
    /// When the dose last changed, so the before/after timeline has an anchor.
    /// Set automatically when a saved dose differs from the one before it.
    var doseChangedAt: Date?
    /// Her own note, e.g. "switched brands due to shortage".
    var note: String?
    /// Flags carried from the catalog: a product prescribed outside its approved
    /// use, or a compounded preparation that isn't standardised. Recorded so her
    /// GP summary is accurate, never shown as a warning.
    var isOffLabel: Bool = false
    var isCompounded: Bool = false

    @Relationship(deleteRule: .cascade, inverse: \MedicationLog.medication)
    var logs: [MedicationLog]

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        name: String,
        dosage: String,
        doseAmount: Double? = nil,
        doseUnit: DoseUnit? = nil,
        timing: String,
        method: MedicationMethod? = nil,
        isActive: Bool = true,
        isTracked: Bool = true,
        autoLogDoses: Bool = false,
        kind: TreatmentKind = .supplement,
        catalogGroupID: String? = nil,
        schedule: DoseSchedule? = nil,
        date: Date? = nil,
        doseChangedAt: Date? = nil,
        note: String? = nil,
        isOffLabel: Bool = false,
        isCompounded: Bool = false,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.name = name
        self.dosage = dosage
        self.doseAmount = doseAmount
        self.doseUnitRaw = doseUnit?.rawValue
        self.timing = timing
        self.methodRaw = method?.rawValue
        self.isActive = isActive
        self.isTracked = isTracked
        self.autoLogDoses = autoLogDoses
        self.kindRaw = kind.rawValue
        self.catalogGroupID = catalogGroupID
        self.date = date
        self.doseChangedAt = doseChangedAt
        self.note = note
        self.isOffLabel = isOffLabel
        self.isCompounded = isCompounded
        self.logs = []
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
        if let schedule { self.schedule = schedule }
    }

    var method: MedicationMethod? {
        get { methodRaw.flatMap(MedicationMethod.init(rawValue:)) }
        set { methodRaw = newValue?.rawValue }
    }

    var kind: TreatmentKind {
        get { TreatmentKind(rawValue: kindRaw) ?? .supplement }
        set { kindRaw = newValue.rawValue }
    }

    var doseUnit: DoseUnit? {
        get { doseUnitRaw.flatMap(DoseUnit.init(rawValue:)) }
        set { doseUnitRaw = newValue?.rawValue }
    }

    var timeOfDay: TimeOfDay? {
        get { timeOfDayRaw.flatMap(TimeOfDay.init(rawValue:)) }
        set { timeOfDayRaw = newValue?.rawValue }
    }

    /// The stored parts, as the thing that knows the rules.
    var schedule: DoseSchedule {
        get {
            DoseSchedule(
                kind: scheduleKindRaw.flatMap(DoseSchedule.Kind.init(rawValue:)) ?? .asNeeded,
                slots: decodedSlots,
                cycleLength: cycleLength,
                pauseDays: cyclePauseDays,
                anchor: cycleAnchor
            )
        }
        set {
            scheduleKindRaw = newValue.kind.rawValue
            cycleLength = newValue.cycleLength
            cyclePauseDays = newValue.pauseDays
            cycleAnchor = newValue.anchor
            doseTimesJSON = (try? JSONEncoder().encode(newValue.slots)).flatMap {
                String(data: $0, encoding: .utf8)
            }
            // Keep the flat columns filled so anything reading them, and any
            // older build, still sees the days and the first time of day.
            weekdaysMask = Self.mask(from: newValue.weekdays)
            doseTime = newValue.sortedSlots.first(where: \.hasTime)?.date
        }
    }

    /// Decoded doses, reconstructed from the older columns when there's no list
    /// yet, so nothing needs a migration pass.
    private var decodedSlots: [DoseSlot] {
        guard let doseTimesJSON, let data = doseTimesJSON.data(using: .utf8),
              let decoded = try? JSONDecoder().decode([DoseSlot].self, from: data),
              !decoded.isEmpty else {
            return [rebuiltSlot]
        }
        // Entries written before days moved onto the dose carried them on the
        // schedule instead: put them back where they now belong.
        let stored = Self.weekdays(from: weekdaysMask)
        guard !stored.isEmpty, stored.count < 7 else { return decoded }
        return decoded.map { slot in
            var slot = slot
            if slot.isEveryDay { slot.weekdays = stored }
            return slot
        }
    }

    private var rebuiltSlot: DoseSlot {
        let days = Self.weekdays(from: weekdaysMask)
        guard let doseTime else {
            return DoseSlot(weekdays: days.isEmpty ? Set(1...7) : days)
        }
        let parts = Calendar.current.dateComponents([.hour, .minute], from: doseTime)
        return DoseSlot(weekdays: days.isEmpty ? Set(1...7) : days,
                        hour: parts.hour, minute: parts.minute)
    }

    /// True when nothing has been set yet, so old entries keep behaving as they
    /// did rather than being treated as a deliberate "as directed".
    var hasSchedule: Bool { scheduleKindRaw != nil }

    static func weekdays(from mask: Int) -> Set<Int> {
        Set((1...7).filter { mask & (1 << ($0 - 1)) != 0 })
    }

    static func mask(from weekdays: Set<Int>) -> Int {
        weekdays.reduce(0) { $0 | (1 << ($1 - 1)) }
    }

    /// "400mg · Every day at 8:00 am", skipping whatever she left blank.
    var detailLine: String {
        var parts: [String] = []
        if isCompounded { parts.append("Compounded") } else if !dosage.isEmpty { parts.append(dosage) }
        if hasSchedule {
            parts.append(schedule.summary)
        } else if !timing.isEmpty {
            parts.append(timing)
        }
        return parts.joined(separator: " · ")
    }
}
