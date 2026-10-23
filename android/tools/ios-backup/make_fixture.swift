// Writes fixtures/backup/v1-ios-sample.keelbackup with Swift's own JSONEncoder, using the iOS
// backup structs (KeelBackupStructs.swift, extracted from the iOS source by extract_structs.py).
// Covers the legacy shapes iOS still reads: startDate, no severity/source/isDefaultChip/
// alcoholCount, an activity log without the sync envelope, frequencyRaw without a schedule kind.
import Foundation

func d(_ s: String) -> Date { ISO8601DateFormatter().date(from: s)! }
func u(_ n: Int) -> UUID { UUID(uuidString: String(format: "AAAAAAAA-0000-0000-0000-%012X", n))! }
let t = d("2026-10-01T09:00:00Z")
let owner = "local-6F1C0E2A-4B9D-4C1E-9A7B-1D2E3F4A5B6C"

// Start of day in Sydney either side of the 4 Oct 2026 daylight saving change.
let beforeDST = d("2026-10-02T14:00:00Z")   // 3 Oct, AEST (+10)
let afterDST = d("2026-10-04T13:00:00Z")    // 5 Oct, AEDT (+11)

let backup = KeelBackup(
    version: 1, exportedAt: d("2026-10-22T09:30:45Z"), appVersion: "1.0",
    profiles: [ProfileDTO(
        id: u(0x50), firstName: "Mischa", email: nil, appleUserID: nil, pathwayRaw: "both",
        healthKitAuthorized: true, trackingStartDate: d("2025-01-15T00:00:00Z"),
        lastName: "R", birthYear: 1977, mobile: nil, periodsNotApplicableReason: nil,
        hysterectomyRaw: "yesOvariesKept", hysterectomyYear: 2019, region: "AU", localeID: "en_AU",
        timeZoneID: "Australia/Sydney", appVersion: "1.0 (59)", deviceModel: "iPhone17,1", osVersion: "iOS 26.5",
        ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload")],
    symptoms: [
        SymptomDTO(id: u(0x10), name: "Hot flushes", categoryRaw: "vasomotor", isCustom: false, isArchived: false,
                   isDefaultChip: nil, ownerID: "", createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "synced"),
        SymptomDTO(id: u(0x11), name: "Sawdust sneezes", categoryRaw: "woodworking", isCustom: true, isArchived: false,
                   isDefaultChip: false, ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
    ],
    checkIns: [
        CheckInDTO(id: u(1), date: beforeDST, moodRaw: "low", energy: 40, notes: "Slept badly 😴, ok after café",
                   alcoholCount: 2, ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
        CheckInDTO(id: u(2), date: afterDST, moodRaw: "good", energy: 0, notes: nil, alcoholCount: nil,
                   ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
        CheckInDTO(id: u(3), date: d("2026-09-01T14:00:00Z"), moodRaw: "okay", energy: 60, notes: "removed later",
                   alcoholCount: 0, ownerID: owner, createdAt: t, updatedAt: t, deletedAt: d("2026-09-03T00:00:00Z"), syncStatusRaw: "pendingUpload"),
    ],
    checkInSymptoms: [
        CheckInSymptomDTO(id: u(0xA), checkInID: u(1), symptomID: u(0x10), severity: 3, sourceRaw: "manual",
                          ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
        // Older archives: no severity, no source.
        CheckInSymptomDTO(id: u(0xB), checkInID: u(2), symptomID: u(0x11), severity: nil, sourceRaw: nil,
                          ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
        // Dangling: its check-in isn't in the archive.
        CheckInSymptomDTO(id: u(0xC), checkInID: u(0xFF), symptomID: u(0x10), severity: 1, sourceRaw: "manual",
                          ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
    ],
    cycleEntries: [
        CycleEntryDTO(id: u(0x20), date: beforeDST, typeRaw: "periodStart", flowLevelRaw: "heavy", sourceRaw: "manual",
                      ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
        CycleEntryDTO(id: u(0x21), date: afterDST, typeRaw: "flow", flowLevelRaw: nil, sourceRaw: nil,
                      ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
    ],
    medications: [
        MedicationDTO(id: u(0x30), name: "Estradiol gel", dosage: "2 pumps", timing: "8:00 am", methodRaw: "gel", isActive: true,
                      doseAmount: 2, doseUnitRaw: "pumps", timeOfDayRaw: nil, kindRaw: "treatment", catalogGroupID: "mht-estrogen",
                      scheduleKindRaw: "weekly", weekdaysMask: 127, cycleLength: 28, cyclePauseDays: 7, cycleAnchor: nil, doseTime: nil,
                      doseTimesJSON: "[{\"id\":\"9B2F0C4E-1D3A-4E5B-8C7D-6A5B4C3D2E1F\",\"weekdays\":[1,2,3,4,5,6,7],\"hour\":8,\"minute\":0},{\"id\":\"1A2B3C4D-5E6F-4A7B-8C9D-0E1F2A3B4C5D\",\"weekdays\":[1,2,3,4,5,6,7],\"hour\":20,\"minute\":30}]",
                      frequencyRaw: nil, date: d("2025-02-01T00:00:00Z"), startDate: nil, doseChangedAt: nil, note: "From my GP",
                      isOffLabel: false, isCompounded: false, ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
        // Legacy: startDate, frequencyRaw without a schedule kind, no kind, an example brand in the name.
        MedicationDTO(id: u(0x31), name: "Magnesium", dosage: "", timing: "Evening", methodRaw: nil, isActive: true,
                      doseAmount: 0.1, doseUnitRaw: nil, timeOfDayRaw: "evening", kindRaw: nil, catalogGroupID: nil,
                      scheduleKindRaw: nil, weekdaysMask: nil, cycleLength: nil, cyclePauseDays: nil, cycleAnchor: nil, doseTime: nil,
                      doseTimesJSON: nil, frequencyRaw: "daily", date: nil, startDate: d("2025-03-01T00:00:00Z"), doseChangedAt: nil,
                      note: nil, isOffLabel: nil, isCompounded: nil, ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
        MedicationDTO(id: u(0x32), name: "Progesterone (e.g. Prometrium)", dosage: "100 mg", timing: "", methodRaw: "tablet", isActive: true,
                      doseAmount: 8, doseUnitRaw: "mg", timeOfDayRaw: nil, kindRaw: "treatment", catalogGroupID: nil,
                      scheduleKindRaw: "cycle", weekdaysMask: 0, cycleLength: 28, cyclePauseDays: 7, cycleAnchor: d("2026-09-01T00:00:00Z"),
                      doseTime: nil, doseTimesJSON: nil, frequencyRaw: nil, date: nil, startDate: nil, doseChangedAt: nil, note: nil,
                      isOffLabel: true, isCompounded: false, ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
    ],
    medicationLogs: [
        MedicationLogDTO(id: u(0x40), date: beforeDST, slot: "9B2F0C4E-1D3A-4E5B-8C7D-6A5B4C3D2E1F", taken: true, medicationID: u(0x30),
                         ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
        MedicationLogDTO(id: u(0x41), date: afterDST, slot: nil, taken: true, medicationID: u(0x31),
                         ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload"),
    ],
    insights: [InsightDTO(id: u(0x60), title: "Cycle length", detail: "Your last three cycles ran 26 to 31 days.", timeframe: "Last 3 cycles",
                          iconKey: "arrow.left.and.right", accentRaw: "sage", generatedAt: t,
                          ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "pendingUpload")],
    // Legacy: an activity log from before the sync envelope.
    activityLogs: [ActivityLogDTO(id: u(0x70), date: beforeDST, activityID: "sleep", amount: 7.5, sourceRaw: nil,
                                  ownerID: nil, createdAt: nil, updatedAt: nil, deletedAt: nil, syncStatusRaw: nil)],
    chatMessages: [],
    dailySummaries: [],
    healthSamples: [
        HealthSampleDTO(id: u(0x80), typeID: "restingHeartRate", day: beforeDST, value: 58, unit: "bpm", sourceRaw: "healthKit",
                        ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "synced"),
        HealthSampleDTO(id: u(0x81), typeID: "hrv", day: afterDST, value: 42.25, unit: "ms", sourceRaw: "healthKit",
                        ownerID: owner, createdAt: t, updatedAt: t, deletedAt: nil, syncStatusRaw: "synced"),
    ]
)

let encoder = JSONEncoder()
encoder.dateEncodingStrategy = .iso8601
encoder.outputFormatting = [.prettyPrinted, .sortedKeys]   // as iOS BackupService.encode
let data = try encoder.encode(backup)
try data.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
print("wrote \(data.count) bytes")
