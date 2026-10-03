import XCTest
import SwiftData
@testable import Keel

/// The pattern engine reports single measures only (her cycle-length range). V1 never
/// links two measures in a sentence, so the sleep, heart rate, temperature, diet and
/// before-your-period detectors are gone.
final class PatternEngineTests: XCTestCase {

    private let cal = TestStore.utcCalendar
    private let base = Date(timeIntervalSince1970: 1_600_000_000)
    private func day(_ offset: Int) -> Date {
        cal.startOfDay(for: cal.date(byAdding: .day, value: offset, to: base)!)
    }

    private func engine(periodStarts: [Date] = []) -> PatternEngine {
        PatternEngine(checkIns: [], periodStarts: periodStarts)
    }

    func testCycleVariabilityReportsHerRealRange() {
        let starts = [day(-90), day(-65), day(-30)]   // 25 then 35 days apart
        let finding = engine(periodStarts: starts).findings().first { $0.kind == .cycleVariability }
        XCTAssertEqual(finding?.detail,
                       "Your recent cycles ranged from about 25 to 35 days apart. That's the kind of detail that can be useful to bring to your GP.")
    }

    /// Regression (submission pack): no finding sentence may link two measures, e.g.
    /// "On the mornings after less sleep, your resting heart rate readings were a
    /// little higher on average." Seeds every kind of record that the removed
    /// detectors used and checks nothing pairs them.
    @MainActor
    func testNoFindingLinksTwoMeasures() {
        let container = KeelSchema.makeContainer(inMemory: true)
        let context = container.mainContext
        let today = Date.now.startOfDay
        for i in 0..<40 {
            let d = today.adding(days: -i)
            let short = i.isMultiple(of: 2)
            context.insert(ActivityLog(date: d, activityID: "sleep", amount: short ? 5.5 : 8, ownerID: "o"))
            context.insert(HealthSample(typeID: "restingHeartRate", day: d, value: short ? 70 : 58, unit: "bpm", ownerID: "o"))
            context.insert(HealthSample(typeID: "wristTemperature", day: d, value: short ? 35.8 : 35.0, unit: "degC", ownerID: "o"))
            context.insert(CheckIn(date: d, mood: short ? .low : .good, energy: short ? 20 : 80, ownerID: "o"))
        }
        // Period starts 25 and 35 days apart, so the single-measure cycle finding fires.
        for start in [-95, -70, -35] { context.insert(CycleEntry(date: today.adding(days: start), type: .flow, flowLevel: .medium, ownerID: "o")) }
        try? context.save()

        let findings = PatternEngine.build(context: context).findings()
        XCTAssertEqual(findings.map(\.kind), [.cycleVariability])
        let linking = ["sleep", "heart rate", "temperature", "energy", "before your period", "on average", "after"]
        for finding in findings {
            for phrase in linking {
                XCTAssertFalse(finding.detail.lowercased().contains(phrase), "\(finding.kind): \(finding.detail)")
            }
        }
        _ = container
    }
}
