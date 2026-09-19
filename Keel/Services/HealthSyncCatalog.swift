import Foundation

/// The Apple Health items Keel can import, as individually switchable units. Single
/// source of truth for the Apple Health settings toggles and for filtering a
/// freshly-read snapshot down to only the items she has left switched on.
///
/// Deliberately does NOT include symptoms or menstrual flow: Keel no longer reads
/// those from Apple Health (she logs them in Keel directly).
enum HealthSyncCatalog {
    struct Item: Identifiable, Equatable {
        let id: String
        let label: String
        let desc: String
        /// `HealthActivitySample` activityIDs this item covers (steps/exercise/sleep/meditation).
        let activityIDs: [String]
        /// `HealthSample` vital typeIDs this item covers.
        let vitalTypeIDs: [String]
    }

    /// Order shown on the Apple Health screen: movement first, then rest, then vitals.
    static let all: [Item] = [
        Item(id: "steps", label: "Steps", desc: "Daily step count",
             activityIDs: ["steps"], vitalTypeIDs: []),
        Item(id: "exercise", label: "Exercise minutes", desc: "Apple exercise minutes",
             activityIDs: ["exercise"], vitalTypeIDs: []),
        Item(id: "activeEnergy", label: "Active energy", desc: "Active calories burned",
             activityIDs: [], vitalTypeIDs: ["activeEnergy"]),
        Item(id: "distance", label: "Walking & running distance", desc: "Distance covered on foot",
             activityIDs: [], vitalTypeIDs: ["distance"]),
        Item(id: "flights", label: "Flights climbed", desc: "Flights of stairs",
             activityIDs: [], vitalTypeIDs: ["flights"]),
        Item(id: "sleep", label: "Sleep", desc: "Hours actually asleep each night",
             activityIDs: ["sleep"], vitalTypeIDs: []),
        Item(id: "heartVitals", label: "Heart & vitals",
             desc: "Heart rate, resting HR, HRV, respiratory rate, blood oxygen, blood pressure",
             activityIDs: [],
             vitalTypeIDs: ["heartRate", "restingHeartRate", "hrv", "respiratoryRate",
                            "oxygenSaturation", "bloodPressureSystolic", "bloodPressureDiastolic"]),
        Item(id: "bodyTemperature", label: "Body temperature", desc: "Basal, wrist and body temperature",
             activityIDs: [], vitalTypeIDs: ["bodyTemperature", "wristTemperature", "basalBodyTemperature"]),
        Item(id: "bodyWeight", label: "Body weight", desc: "Weight over time",
             activityIDs: [], vitalTypeIDs: ["bodyMass"]),
    ]

    /// Remove everything belonging to switched-off items from a freshly-read snapshot,
    /// so nothing new is imported for them (existing rows are left untouched, honouring
    /// "stop future syncing, keep existing"). Pure, so it's unit-tested without HealthKit.
    static func filter(_ snapshot: HealthSnapshot, disabled: Set<String>) -> HealthSnapshot {
        guard !disabled.isEmpty else { return snapshot }
        let off = all.filter { disabled.contains($0.id) }
        let offActivityIDs = Set(off.flatMap(\.activityIDs))
        let offVitalIDs = Set(off.flatMap(\.vitalTypeIDs))

        var out = snapshot
        if offActivityIDs.contains("sleep") { out.sleepByDay = [:] } // sleep lives in its own field
        for key in offActivityIDs { out.activityAmounts[key] = nil }
        out.vitals = out.vitals.filter { !offVitalIDs.contains($0.typeID) }
        return out
    }
}
