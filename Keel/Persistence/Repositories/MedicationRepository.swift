import Foundation
import SwiftData

/// What she entered on the add or edit form. Every field beyond the name is
/// optional: a partial entry beats none.
struct TreatmentDraft {
    var name: String
    var kind: TreatmentKind
    var catalogGroupID: String?
    var method: MedicationMethod?
    /// Dose as a number plus a unit. Never pre-filled for her.
    var doseAmount: Double?
    var doseUnit: DoseUnit?
    /// When she takes it: a weekly pattern, a cycle with a pause, or nothing set.
    var schedule = DoseSchedule.everyDay
    /// Free text kept from entries made before the structured fields existed.
    /// Shown only when there's nothing structured to show instead.
    var legacyDosage: String = ""
    var legacyTiming: String = ""
    /// Both dates are recorded for her rather than asked for, so they're carried
    /// through the form untouched. See `MedicationRepository.add`/`update`.
    var date: Date?
    var doseChangedAt: Date?
    var note: String = ""
    var isOffLabel: Bool = false
    var isCompounded: Bool = false
    /// Mark this medicine's doses taken automatically (see `Medication.autoLogDoses`).
    var autoLogDoses: Bool = false

    /// Readable dose, e.g. "400mg" or "2 tablets". Falls back to whatever free
    /// text an older entry carried.
    var doseText: String {
        guard let doseAmount, let doseUnit else { return legacyDosage }
        return doseUnit.format(doseAmount)
    }

    /// Readable time, kept on the model so reports and the GP summary can read
    /// one field.
    var timingText: String {
        schedule.sortedSlots.first(where: \.hasTime)?.label ?? legacyTiming
    }

    /// Only a treatment carries a method. Nothing is stored for a supplement,
    /// so a value can't linger out of sight after something is re-filed.
    var effectiveMethod: MedicationMethod? {
        kind == .treatment ? method : nil
    }

    /// Fill the form from what she already has recorded.
    init(_ med: Medication) {
        name = med.name
        kind = med.kind
        catalogGroupID = med.catalogGroupID
        method = med.method
        doseAmount = med.doseAmount
        doseUnit = med.doseUnit
        schedule = med.hasSchedule ? med.schedule : .everyDay
        // Free text only survives while there's nothing structured to replace it.
        legacyDosage = med.doseAmount == nil ? med.dosage : ""
        legacyTiming = med.doseTime == nil ? med.timing : ""
        date = med.date
        doseChangedAt = med.doseChangedAt
        note = med.note ?? ""
        isOffLabel = med.isOffLabel
        isCompounded = med.isCompounded
        autoLogDoses = med.autoLogDoses
    }

    init(name: String, kind: TreatmentKind, catalogGroupID: String? = nil,
         method: MedicationMethod? = nil, isOffLabel: Bool = false, isCompounded: Bool = false) {
        self.name = name
        self.kind = kind
        self.catalogGroupID = catalogGroupID
        self.method = method
        self.isOffLabel = isOffLabel
        self.isCompounded = isCompounded
    }
}

@MainActor
protocol MedicationRepositoring {
    func active() -> [Medication]
    func active(kind: TreatmentKind) -> [Medication]
    func active(named name: String) -> Medication?
    func migrateLegacySchedules()
    @discardableResult
    func add(name: String, dosage: String, timing: String, method: MedicationMethod?) -> Medication
    @discardableResult
    func add(_ draft: TreatmentDraft) -> Medication
    func update(_ medication: Medication, with draft: TreatmentDraft)
    func archive(_ medication: Medication)
    func setTracked(_ medication: Medication, _ tracked: Bool)
    func isTaken(_ medication: Medication, on date: Date, slot: String?) -> Bool
    func setTaken(_ medication: Medication, on date: Date, slot: String?, taken: Bool)
    func clearTaken(_ medication: Medication, on date: Date)
}

@MainActor
struct MedicationRepository: MedicationRepositoring {
    let context: ModelContext
    let ownerID: OwnerIDProvider

    func active() -> [Medication] {
        let descriptor = FetchDescriptor<Medication>(
            predicate: #Predicate { $0.deletedAt == nil && $0.isActive == true },
            sortBy: [SortDescriptor(\.createdAt)]
        )
        return (try? context.fetch(descriptor)) ?? []
    }

    func active(kind: TreatmentKind) -> [Medication] {
        active().filter { $0.kind == kind }
    }

    /// Case-insensitive match against what she's currently taking. Archived
    /// entries don't count: adding something back after stopping it starts a
    /// fresh row, so the old history stays where it belongs.
    func active(named name: String) -> Medication? {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        return active().first { $0.name.localizedCaseInsensitiveCompare(trimmed) == .orderedSame }
    }

    /// Carry entries made before schedules existed onto one, once. "Daily"
    /// becomes every day, "Cyclic" becomes the standard 28 day cycle with a
    /// 7 day pause, and a day-part becomes a time she can move.
    func migrateLegacySchedules() {
        let stale = active().filter { !$0.hasSchedule }
        guard !stale.isEmpty else { return }
        for medication in stale {
            var schedule = DoseSchedule.everyDay
            var days = Set(1...7)
            switch medication.frequencyRaw.flatMap(DoseFrequency.init(rawValue:)) {
            case .daily, .none:
                break
            case .twiceWeekly:
                // The usual spread for a patch changed twice a week; hers to move.
                days = [2, 5]
            case .weekly:
                days = [Calendar.current.component(.weekday, from: medication.date ?? medication.createdAt)]
            case .cyclic:
                schedule.kind = .cycle
                schedule.anchor = medication.date ?? medication.createdAt
            case .asDirected:
                schedule.kind = .asNeeded
            }
            let clock = Self.clock(forDayPart: medication.timeOfDay)
            schedule.slots = [DoseSlot(weekdays: days, hour: clock?.hour, minute: clock?.minute)]
            medication.schedule = schedule
            medication.timing = schedule.sortedSlots.first(where: \.hasTime)?.label ?? medication.timing
            medication.touch()
        }
        try? context.save()
    }

    /// Morning, afternoon and evening as clock times, so a migrated entry can
    /// still raise a reminder.
    private static func clock(forDayPart part: TimeOfDay?) -> (hour: Int, minute: Int)? {
        guard let part else { return nil }
        let hour = switch part {
        case .morning: 9
        case .afternoon: 13
        case .evening: 20
        }
        return (hour, 0)
    }

    @discardableResult
    func add(name: String, dosage: String, timing: String, method: MedicationMethod?) -> Medication {
        var draft = TreatmentDraft(name: name, kind: .supplement, method: method)
        draft.legacyDosage = dosage
        draft.legacyTiming = timing
        return add(draft)
    }

    /// Adds what she's taking, or updates it if it's already on her list. Upsert
    /// rather than insert so the same product can't appear twice: she has just
    /// told us its current details, so those win.
    @discardableResult
    func add(_ draft: TreatmentDraft) -> Medication {
        if let existing = active(named: draft.name) {
            update(existing, with: draft)
            return existing
        }
        let medication = Medication(
            name: draft.name.trimmingCharacters(in: .whitespaces),
            dosage: draft.doseText,
            doseAmount: draft.doseAmount,
            doseUnit: draft.doseUnit,
            timing: draft.timingText,
            method: draft.effectiveMethod,
            kind: draft.kind,
            catalogGroupID: draft.catalogGroupID,
            schedule: draft.schedule,
            // Only a date she actually entered is recorded, never a system timestamp,
            // so the GP summary carries her date unaltered and stays blank if she
            // didn't give one (clinical data-integrity rule, product alignment note).
            date: draft.date,
            doseChangedAt: draft.doseChangedAt,
            note: draft.note.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
            isOffLabel: draft.isOffLabel,
            isCompounded: draft.isCompounded,
            ownerID: ownerID()
        )
        medication.autoLogDoses = draft.autoLogDoses
        context.insert(medication)
        try? context.save()
        return medication
    }

    func update(_ medication: Medication, with draft: TreatmentDraft) {
        // Dates are recorded only as she entered them: never inferred and never stamped
        // with today, so the GP summary carries her dates unaltered and stays blank
        // when she didn't give one. A dose change with no date she gave stays blank.
        medication.doseChangedAt = draft.doseChangedAt
        medication.date = medication.date ?? draft.date
        medication.name = draft.name.trimmingCharacters(in: .whitespaces)
        medication.dosage = draft.doseText
        medication.doseAmount = draft.doseAmount
        medication.doseUnit = draft.doseUnit
        medication.timing = draft.timingText
        medication.schedule = draft.schedule
        medication.method = draft.effectiveMethod
        medication.kind = draft.kind
        medication.catalogGroupID = draft.catalogGroupID
        medication.note = draft.note.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        medication.isOffLabel = draft.isOffLabel
        medication.isCompounded = draft.isCompounded
        medication.autoLogDoses = draft.autoLogDoses
        medication.touch()
        try? context.save()
    }

    func archive(_ medication: Medication) {
        medication.isActive = false
        medication.softDelete()
        try? context.save()
    }

    /// The Medications-screen tick: whether this medicine shows in the home log.
    /// Not a dose record; adherence is set on the home screen, per day.
    func setTracked(_ medication: Medication, _ tracked: Bool) {
        medication.isTracked = tracked
        medication.touch()
        try? context.save()
    }

    /// Turn auto-logging of doses on or off for this medicine.
    func setAutoLog(_ medication: Medication, _ on: Bool) {
        medication.autoLogDoses = on
        medication.touch()
        try? context.save()
    }

    /// An active medicine by id (for the reminder handler / auto-log).
    func active(id: UUID) -> Medication? {
        active().first { $0.id == id }
    }

    /// Auto-log today's already-due doses for every medicine set to auto-log, so a
    /// day she opens the app has its doses ticked without tapping. Only today, only
    /// doses whose time has passed, only if not already logged — never the future,
    /// never a backfill of earlier days. Returns the `(medication, slot)` doses it
    /// logged, so their now-redundant reminders can be cancelled.
    @discardableResult
    func autoLogTodaysDueDoses(now: Date = .now) -> [(medID: UUID, slot: String?)] {
        let cal = Calendar.current
        let nowMinutes = cal.component(.hour, from: now) * 60 + cal.component(.minute, from: now)
        var logged: [(medID: UUID, slot: String?)] = []
        for med in active() where med.autoLogDoses {
            // Log each of today's due doses whose time has arrived. An untimed dose
            // counts as soon as the day is due; a timed dose only once its time has
            // passed (a still-future dose logs nothing). `dueSlots` is empty on a
            // day the med isn't scheduled, so nothing is logged then either.
            //
            // Note: log by the dose's slot id, never a whole-day nil. The old code
            // used `slots.isEmpty ? [nil] : slots.compactMap { … nil }`, which both
            // (a) logged a whole day on days the med wasn't due, and (b) — because
            // the ternary's nil landed in a `[String?]` compactMap — kept that nil
            // as an element, wrongly whole-day-logging a still-future dose.
            for slot in med.schedule.dueSlots(on: now)
            where !slot.hasTime || slot.minutesIntoDay <= nowMinutes {
                let id = slot.id.uuidString
                guard !isTaken(med, on: now, slot: id) else { continue }
                setTaken(med, on: now, slot: id, taken: true)
                logged.append((med.id, id))
            }
        }
        return logged
    }

    /// One log per dose: `slot` is the `ScheduledTime` id, or nil for an entry
    /// with no set times, where the tick means the whole day.
    private func log(_ medication: Medication, on date: Date, slot: String?) -> MedicationLog? {
        let start = date.startOfDay
        let end = start.adding(days: 1)
        let medID = medication.id
        let descriptor = FetchDescriptor<MedicationLog>(
            predicate: #Predicate { log in
                log.deletedAt == nil
                    && log.date >= start && log.date < end
                    && log.medication?.id == medID
                    && log.slot == slot
            }
        )
        return (try? context.fetch(descriptor))?.first
    }

    func isTaken(_ medication: Medication, on date: Date, slot: String? = nil) -> Bool {
        log(medication, on: date, slot: slot)?.taken ?? false
    }

    func setTaken(_ medication: Medication, on date: Date, slot: String? = nil, taken: Bool) {
        if let existing = log(medication, on: date, slot: slot) {
            if taken {
                existing.taken = true
                existing.touch()
            } else {
                // Unticking removes the record, so it's no longer on the log.
                existing.softDelete()
            }
        } else if taken {
            let entry = MedicationLog(
                date: date.startOfDay,
                slot: slot,
                taken: true,
                medication: medication,
                ownerID: ownerID()
            )
            context.insert(entry)
        }
        try? context.save()
    }

    /// Removes every taken log for this medication on the day, across all dose
    /// slots. Used when she unticks it from the home log: nothing should linger,
    /// so it also reads untaken on the per-slot Medications screen.
    func clearTaken(_ medication: Medication, on date: Date) {
        let start = date.startOfDay
        let end = start.adding(days: 1)
        let medID = medication.id
        let descriptor = FetchDescriptor<MedicationLog>(
            predicate: #Predicate { log in
                log.deletedAt == nil
                    && log.date >= start && log.date < end
                    && log.medication?.id == medID
            }
        )
        for log in (try? context.fetch(descriptor)) ?? [] { log.softDelete() }
        try? context.save()
    }
}
