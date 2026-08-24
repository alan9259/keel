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
        case restingHeartRateSleep
        case wristTemperatureSleep
        case dietTrigger
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
///  2. Sleep → resting heart rate (the body's own read of a short night).
///  3. Premenstrual / late-luteal clustering of symptoms or low mood.
///  4. Cycle-length variability (a hallmark early-perimenopause change).
///  5. A recurring symptom worth watching (hot flushes and the rest).
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
    /// Resting heart rate (bpm) per day, from Apple Health. Empty when unimported.
    let restingHRByDay: [Date: Double]
    /// Overnight wrist temperature (°C) per day, from Apple Watch. Empty when absent.
    let wristTempByDay: [Date: Double]
    /// Distinct days each symptom was reported, keyed by name, merged from her
    /// check-ins AND Apple Health's own symptom logs (see [[SymptomTally]]).
    let symptomDaysByName: [String: Set<Date>]
    /// Her yes/no diet-trigger logs (alcohol, caffeine, …) for the trigger↔symptom
    /// comparison. Empty when she hasn't used the eating panel.
    let dietTriggers: [DietTriggerCorrelation.Input]
    /// First day of each logged period run (start of day), ascending.
    let periodStarts: [Date]
    let today: Date
    /// Injected so day-keyed comparisons are deterministic in tests (fixed UTC).
    let calendar: Calendar

    /// Detectors that need recent, everyday signal look back this far.
    private static let recentWindow = 30

    func findings() -> [PatternFinding] {
        var out: [PatternFinding] = []
        if let f = sleepEnergy() { out.append(f) }
        if let f = restingHeartRateSleep() { out.append(f) }
        if let f = wristTemperatureSleep() { out.append(f) }
        if let f = dietTrigger() { out.append(f) }
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

    // MARK: - 2. Sleep → resting heart rate

    /// Her resting heart rate the mornings after shorter sleep, against her good-sleep
    /// mornings. The body's own, objective echo of the subjective sleep→energy link —
    /// a real paired comparison from Apple Health, never an invented figure. Surfaced
    /// only when the difference is clear (about 3 bpm+), so a trivial gap stays quiet.
    private func restingHeartRateSleep() -> PatternFinding? {
        guard let gap = VitalTrend.restingHRSleepGap(
            restingHRByDay: restingHRByDay, sleepHoursByDay: sleepByDay, calendar: calendar),
              gap.gap >= 3 else { return nil }
        return PatternFinding(
            kind: .restingHeartRateSleep,
            title: "Sleep and your heart rate",
            detail: "On the mornings after shorter-sleep nights, your resting heart rate has tended to run a little higher. That's a common way the body registers a short night, and it usually eases with rest. Worth noticing, and worth a mention to your GP if it keeps up.",
            timeframe: "Seen across \(gap.pairedDays) days with both logged",
            icon: "heart.fill",
            accent: .terracotta,
            // Qualitative on purpose: no bpm figure, so the AI narration can't restate
            // a number. The exact vitals live on the Activities screen.
            fact: "On mornings after shorter-sleep nights, her resting heart rate has tended to run a little higher.")
    }

    // MARK: - 3. Sleep → overnight body temperature

    /// Her overnight wrist temperature after shorter sleep vs good sleep. Temperature
    /// regulation is one of the clearest ways perimenopause shows up at night (hot
    /// flushes, night sweats), so a real, paired warmer-after-short-sleep signal is
    /// worth surfacing gently. Threshold is small (0.2°C) because skin-temperature
    /// swings are small; framed as "notice", never a reading to worry about.
    private func wristTemperatureSleep() -> PatternFinding? {
        guard let gap = VitalTrend.sleepSplitGap(
            valueByDay: wristTempByDay, sleepHoursByDay: sleepByDay, calendar: calendar),
              gap.gap >= 0.2 else { return nil }
        return PatternFinding(
            kind: .wristTemperatureSleep,
            title: "Sleep and body temperature",
            detail: "On the nights you slept less, your overnight temperature has tended to run a little warmer. Shifts in body temperature are part of how perimenopause can unsettle sleep, and they usually ease. Worth noticing, and worth a mention to your GP if hot, broken nights are wearing on you.",
            timeframe: "Seen across \(gap.pairedDays) nights with both logged",
            icon: "thermometer.medium",
            accent: .terracotta,
            fact: "On shorter-sleep nights, her overnight body temperature has tended to run a little warmer.")
    }

    // MARK: - 4. Diet trigger → vasomotor symptoms

    /// Days hot flushes or night sweats were reported (either source).
    private func vasomotorDays() -> Set<Date> {
        var union: Set<Date> = []
        for name in SymptomTally.vasomotorNames { union.formUnion(symptomDaysByName[name] ?? []) }
        return union
    }

    /// Whether hot flushes / night sweats have turned up more on the days she logged a
    /// trigger (alcohol, caffeine, spicy food) than on the days she logged she didn't.
    /// A real yes-vs-no comparison from her own logs; co-occurrence only, never cause.
    private func dietTrigger() -> PatternFinding? {
        guard let result = DietTriggerCorrelation.strongest(dietTriggers, symptomDays: vasomotorDays()) else { return nil }
        let label = result.label.lowercased()
        return PatternFinding(
            kind: .dietTrigger,
            title: "\(result.label) and your symptoms",
            detail: "Hot flushes or night sweats turned up on \(result.yesHit) of the \(result.yesTotal) days you had \(label), and on \(result.noHit) of the \(result.noTotal) days you didn't. They have tended to come with your \(label) days lately. That's worth noticing, and worth a mention to your GP.",
            timeframe: "From the days you logged \(label)",
            icon: "fork.knife",
            accent: .terracotta,
            fact: "Hot flushes or night sweats have tended to come with her \(label) days lately.")
    }

    // MARK: - 5. Premenstrual / late-luteal clustering

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

    // MARK: - 6. Cycle-length variability

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

    // MARK: - 7. A recurring symptom

    private func recurringSymptom() -> PatternFinding? {
        let floor = today.startOfDay.adding(days: -(Self.recentWindow - 1))
        // Days per symptom in the recent window, merged across check-ins and Apple
        // Health (via `symptomDaysByName`), so a symptom logged only in Health counts.
        var recentDays: [String: Int] = [:]
        for (name, days) in symptomDaysByName {
            let n = days.filter { $0 >= floor }.count
            if n > 0 { recentDays[name] = n }
        }
        guard let top = recentDays.max(by: { $0.value != $1.value ? $0.value < $1.value : $0.key > $1.key }),
              top.value >= 3 else { return nil }
        let count = top.value
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
    static func build(context: ModelContext, window: Int = 120, today: Date = Date(),
                      calendar: Calendar = .current) -> PatternEngine {
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

        // One ActivityLog fetch for the window: sleep hours + her yes/no diet-trigger
        // logs (from the eating panel), split by activityID.
        let activityDescriptor = FetchDescriptor<ActivityLog>(
            predicate: #Predicate<ActivityLog> { $0.deletedAt == nil && $0.date >= floor }
        )
        let activityLogs = (try? context.fetch(activityDescriptor)) ?? []
        var sleepByDay: [Date: Double] = [:]
        for log in activityLogs where log.activityID == "sleep" && log.amount > 0 {
            let day = log.date.startOfDay
            if sleepByDay[day] == nil { sleepByDay[day] = log.amount }
        }
        let dietTriggers: [DietTriggerCorrelation.Input] = EatingCatalog.triggers.map { item in
            var yes: Set<Date> = [], no: Set<Date> = []
            for log in activityLogs where log.activityID == item.id {
                let day = log.date.startOfDay
                if log.amount > 0.5 { yes.insert(day) } else { no.insert(day) }
            }
            return DietTriggerCorrelation.Input(label: item.label, yes: yes, no: no)
        }

        // One Apple Health fetch for the window, split into the series the detectors
        // need: resting heart rate (one daily aggregate per day) and archived symptom
        // occurrences (`symptom.*` rows for days she logged only in Health).
        let sampleDescriptor = FetchDescriptor<HealthSample>(
            predicate: #Predicate<HealthSample> { $0.deletedAt == nil && $0.day >= floor }
        )
        let healthSamples = (try? context.fetch(sampleDescriptor)) ?? []
        var restingHRByDay: [Date: Double] = [:]
        var wristTempByDay: [Date: Double] = [:]
        for sample in healthSamples where sample.value > 0 {
            let day = sample.day.startOfDay
            switch sample.typeID {
            case "restingHeartRate": if restingHRByDay[day] == nil { restingHRByDay[day] = sample.value }
            case "wristTemperature": if wristTempByDay[day] == nil { wristTempByDay[day] = sample.value }
            default: break
            }
        }

        // Symptom days merged from her check-ins and Apple Health's own logs.
        var symptomDaysByName: [String: Set<Date>] = [:]
        for entry in checkIns {
            for name in entry.symptoms { symptomDaysByName[name, default: []].insert(entry.day) }
        }
        for sample in healthSamples {
            if let name = SymptomTally.name(fromHealthTypeID: sample.typeID) {
                symptomDaysByName[name, default: []].insert(sample.day.startOfDay)
            }
        }

        let cycleDescriptor = FetchDescriptor<CycleEntry>(
            predicate: #Predicate { $0.deletedAt == nil && $0.date >= floor }
        )
        let periodDays = Set(((try? context.fetch(cycleDescriptor)) ?? []).map { $0.date.startOfDay })

        return PatternEngine(
            checkIns: checkIns,
            sleepByDay: sleepByDay,
            restingHRByDay: restingHRByDay,
            wristTempByDay: wristTempByDay,
            symptomDaysByName: symptomDaysByName,
            dietTriggers: dietTriggers,
            periodStarts: periodStarts(from: periodDays),
            today: today,
            calendar: calendar)
    }
}
