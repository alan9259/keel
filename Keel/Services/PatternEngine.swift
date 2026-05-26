import Foundation
import SwiftData

/// One grounded observation about her own data. `fact` is a terse, factual
/// sentence the daily summary hands to Apple Intelligence to narrate; the card
/// fields render the Patterns insight cards. Nothing here invents a statistic.
/// Every number is a real count or range from her own logs, and every claim is
/// framed as something to notice, never a diagnosis.
struct PatternFinding {
    enum Kind: String {
        case sleepEnergy
        case recurringSymptom
        case cycleVariability
        case premenstrual
    }

    let kind: Kind
    let title: String
    let detail: String
    let timeframe: String
    let icon: String
    let accent: InsightAccent
    /// A short factual line for AI narration, e.g. "On nights she slept less,
    /// her next-day energy was often lower."
    let fact: String
}

/// Derives perimenopause-relevant patterns from her own check-ins, sleep, and
/// cycle logs. A pure value type over already-extracted data so it is easy to
/// test and safe to run off the model context; build it with `PatternEngine.build`.
///
/// Detectors, in the order they read best in a summary:
///  1. Sleep → next-day energy (the strongest everyday link).
///  2. Premenstrual / late-luteal clustering of symptoms or low mood.
///  3. Cycle-length variability (a hallmark early-perimenopause change).
///  4. A recurring symptom worth watching (hot flushes and the rest).
struct PatternEngine {
    /// A day's worth of her logs, flattened off the SwiftData models.
    struct DayCheckIn {
        let day: Date
        let mood: Mood
        let energy: Int
        let symptoms: [String]
    }

    let checkIns: [DayCheckIn]
    let sleepByDay: [Date: Double]
    /// First day of each logged period run (start of day), ascending.
    let periodStarts: [Date]
    let today: Date

    /// Detectors that need recent, everyday signal look back this far.
    private static let recentWindow = 30

    func findings() -> [PatternFinding] {
        var out: [PatternFinding] = []
        if let f = sleepEnergy() { out.append(f) }
        if let f = premenstrual() { out.append(f) }
        if let f = cycleVariability() { out.append(f) }
        if let f = recurringSymptom() { out.append(f) }
        return out
    }

    /// Distinct calendar days she has checked in on (any window).
    var loggedDayCount: Int { Set(checkIns.map { $0.day }).count }

    // MARK: - 1. Sleep → next-day energy

    private func sleepEnergy() -> PatternFinding? {
        let recent = checkInsWithin(Self.recentWindow)
        var lowSleep: [Int] = []
        var goodSleep: [Int] = []
        for entry in recent {
            guard let hours = sleepByDay[entry.day] else { continue }
            if hours < 6.5 { lowSleep.append(entry.energy) }
            else if hours >= 7 { goodSleep.append(entry.energy) }
        }
        guard lowSleep.count >= 3, goodSleep.count >= 3 else { return nil }
        let lowAvg = lowSleep.reduce(0, +) / lowSleep.count
        let goodAvg = goodSleep.reduce(0, +) / goodSleep.count
        // Only surface a clear direction; describe the direction, never a number.
        guard goodAvg - lowAvg >= 10 else { return nil }
        return PatternFinding(
            kind: .sleepEnergy,
            title: "Sleep and energy",
            detail: "On the nights you slept less, your energy the next day was often lower. These seem to move together. You might like to keep an eye on it, or mention it to your GP.",
            timeframe: "Seen across \(lowSleep.count + goodSleep.count) days with both logged",
            icon: "moon.stars.fill",
            accent: .terracotta,
            fact: "On nights she slept less, her energy the next day was often lower.")
    }

    // MARK: - 2. Premenstrual / late-luteal clustering

    private func premenstrual() -> PatternFinding? {
        // Need at least two cycles to say something turns up "before your period".
        guard periodStarts.count >= 2 else { return nil }
        let windows = periodStarts.map { ($0.adding(days: -7), $0) } // [start-7, start)
        func inWindow(_ day: Date) -> Bool {
            windows.contains { day >= $0.0 && day < $0.1 }
        }

        let logged = checkIns
        guard !logged.isEmpty else { return nil }
        let windowDays = logged.filter { inWindow($0.day) }.count
        guard windowDays >= 3 else { return nil }
        let share = Double(windowDays) / Double(logged.count) // baseline chance of landing in a window

        // Symptoms clustering before the period.
        let symptomDays = logged.filter { !$0.symptoms.isEmpty }
        let symptomInWindow = symptomDays.filter { inWindow($0.day) }.count
        if symptomInWindow >= 3, !symptomDays.isEmpty {
            let expected = Double(symptomDays.count) * share
            if Double(symptomInWindow) >= expected * 1.5 {
                return PatternFinding(
                    kind: .premenstrual,
                    title: "Before your period",
                    detail: "Your symptoms have tended to turn up more in the days before your period. That premenstrual window is often a bumpier stretch in perimenopause. Worth noticing, and worth a mention to your GP if it's wearing on you.",
                    timeframe: "Across your last \(periodStarts.count) logged cycles",
                    icon: "calendar.badge.clock",
                    accent: .terracotta,
                    fact: "Her symptoms have tended to cluster in the days before her period.")
            }
        }

        // Mood dipping before the period, if symptoms didn't already surface it.
        let moodInWindow = logged.filter { inWindow($0.day) }.map(\.mood.score)
        let moodOutside = logged.filter { !inWindow($0.day) }.map(\.mood.score)
        if moodInWindow.count >= 3, moodOutside.count >= 3 {
            let inAvg = moodInWindow.reduce(0, +) / Double(moodInWindow.count)
            let outAvg = moodOutside.reduce(0, +) / Double(moodOutside.count)
            if outAvg - inAvg >= 0.6 {
                return PatternFinding(
                    kind: .premenstrual,
                    title: "Before your period",
                    detail: "Your mood has tended to dip in the days before your period. That premenstrual shift is common, and it can feel sharper through perimenopause. Gentle with yourself in that window, and worth raising with your GP if it's hard.",
                    timeframe: "Across your last \(periodStarts.count) logged cycles",
                    icon: "calendar.badge.clock",
                    accent: .terracotta,
                    fact: "Her mood has tended to dip in the days before her period.")
            }
        }
        return nil
    }

    // MARK: - 3. Cycle-length variability

    private func cycleVariability() -> PatternFinding? {
        guard periodStarts.count >= 3 else { return nil }
        let sorted = periodStarts.sorted()
        var intervals: [Int] = []
        for i in 1..<sorted.count {
            let gap = sorted[i].days(since: sorted[i - 1])
            // Ignore obvious mis-logs / spotting so the range stays meaningful.
            if gap >= 15 && gap <= 90 { intervals.append(gap) }
        }
        guard intervals.count >= 2, let lo = intervals.min(), let hi = intervals.max() else { return nil }
        // A persistent 7+ day swing between cycles is a recognised early sign of
        // the perimenopausal transition. We report her real range, not a guess.
        guard hi - lo >= 7 else { return nil }
        return PatternFinding(
            kind: .cycleVariability,
            title: "Your cycle length",
            detail: "Your recent cycles have varied more in length, ranging from about \(lo) to \(hi) days apart. Cycles becoming less predictable is one of the more common early signs of perimenopause. It's useful to note, and helpful for your GP to hear.",
            timeframe: "Across your last \(sorted.count) logged cycles",
            icon: "arrow.left.and.right",
            accent: .sage,
            // Qualitative on purpose: the exact range lives on the card, so the
            // AI narration can't restate a number and contradict it.
            fact: "Her cycles have been varying more in length lately, becoming less predictable.")
    }

    // MARK: - 4. A recurring symptom

    private func recurringSymptom() -> PatternFinding? {
        let recent = checkInsWithin(Self.recentWindow)
        var days: [String: Set<Date>] = [:]
        for entry in recent {
            for name in entry.symptoms {
                days[name, default: []].insert(entry.day)
            }
        }
        guard let top = days.max(by: { $0.value.count < $1.value.count }), top.value.count >= 3 else { return nil }
        let count = top.value.count
        let name = top.key.lowercased()
        return PatternFinding(
            kind: .recurringSymptom,
            title: "A symptom worth watching",
            detail: "You've noted \(name) on \(count) of your recent days. That's the kind of thing worth keeping an eye on, or bringing to your GP.",
            timeframe: "Your most-logged symptom lately",
            icon: "list.bullet.clipboard",
            accent: .sage,
            // Qualitative on purpose: the exact day count is on the card.
            fact: "\(name) has been her most frequently logged symptom lately.")
    }

    // MARK: - Helpers

    private func checkInsWithin(_ days: Int) -> [DayCheckIn] {
        let floor = today.startOfDay.adding(days: -(days - 1))
        return checkIns.filter { $0.day >= floor }
    }

    /// First day of each run of logged period days.
    static func periodStarts(from periodDays: Set<Date>) -> [Date] {
        periodDays.filter { !periodDays.contains($0.adding(days: -1)) }.sorted()
    }
}

// MARK: - Building from the store

@MainActor
extension PatternEngine {
    /// Reads the last `window` days of check-ins/sleep and all cycle logs in that
    /// span, flattening them into the pure engine. Cycle detectors need several
    /// months to see variability, so the window is wider than the recency ones.
    static func build(context: ModelContext, window: Int = 120, today: Date = Date()) -> PatternEngine {
        let floor = today.startOfDay.adding(days: -(window - 1))

        let checkInDescriptor = FetchDescriptor<CheckIn>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= floor },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        let rawCheckIns = (try? context.fetch(checkInDescriptor)) ?? []
        let checkIns = rawCheckIns.map { ci in
            DayCheckIn(day: ci.date.startOfDay, mood: ci.mood, energy: ci.energy,
                       symptoms: ci.symptoms.map(\.name))
        }

        let sleepDescriptor = FetchDescriptor<ActivityLog>(
            predicate: #Predicate<ActivityLog> { $0.deletedAt == nil && $0.activityID == "sleep" && $0.date >= floor }
        )
        var sleepByDay: [Date: Double] = [:]
        for log in ((try? context.fetch(sleepDescriptor)) ?? []) where log.amount > 0 {
            let day = log.date.startOfDay
            if sleepByDay[day] == nil { sleepByDay[day] = log.amount }
        }

        let cycleDescriptor = FetchDescriptor<CycleEntry>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= floor }
        )
        let periodDays = Set(((try? context.fetch(cycleDescriptor)) ?? []).map { $0.date.startOfDay })

        return PatternEngine(
            checkIns: checkIns,
            sleepByDay: sleepByDay,
            periodStarts: periodStarts(from: periodDays),
            today: today)
    }
}
