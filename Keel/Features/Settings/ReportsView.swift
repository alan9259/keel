import SwiftUI
import SwiftData

struct ReportsView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @Query(filter: #Predicate<CheckIn> { $0.deletedAt == nil }, sort: \CheckIn.date, order: .reverse)
    private var checkIns: [CheckIn]
    @Query(filter: #Predicate<Medication> { $0.deletedAt == nil && $0.isActive == true })
    private var meds: [Medication]
    @Query(filter: #Predicate<MedicationLog> { $0.deletedAt == nil })
    private var medLogs: [MedicationLog]
    @Query private var activities: [ActivityLog]
    @Query(filter: #Predicate<HealthSample> { $0.deletedAt == nil })
    private var samples: [HealthSample]

    enum Period: String, CaseIterable, Identifiable { case week = "Week", month = "Month", quarter = "3 Months"; var id: String { rawValue }
        var days: Int { switch self { case .week: 7; case .month: 30; case .quarter: 90 } } }

    @State private var period: Period = .month
    /// The sleep bar the user tapped, for the detail line (nil = show the latest night).
    @State private var selectedSleepIndex: Int?

    private var since: Date { Date.now.startOfDay.adding(days: -(period.days - 1)) }
    private var windowCheckIns: [CheckIn] { checkIns.filter { $0.date >= since } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header
                periodRow
                checkInSummary
                statTiles
                moodSection
                symptomsSection
                medsSection
                sleepSection
                vitalsSection
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    private var header: some View {
        HStack(alignment: .top) {
            ScreenHeader(title: "Looking back", titleSize: 28, subtitle: "What you have recorded") { dismiss() }
            ShareLink(item: exportText) {
                Label("Export", systemImage: "square.and.arrow.up")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(theme.border, lineWidth: 1))
            }
        }
    }

    private var periodRow: some View {
        HStack {
            KeelSegmented(options: Period.allCases.map(\.rawValue),
                          selection: Binding(get: { Period.allCases.firstIndex(of: period) ?? 1 },
                                             set: { period = Period.allCases[$0] }))
                .frame(maxWidth: 260)
            Spacer()
        }
    }

    /// A plain, non-judging count of recent check-ins. No streaks, no broken runs, no
    /// grey holes for the days a hard fortnight meant she didn't log.
    private var checkInSummary: some View {
        let count = last7Days.filter { loggedDays.contains($0) }.count
        return Text("You checked in on \(count) of the last 7 days.")
            .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
    }

    private var statTiles: some View {
        let tiles: [(String, String, String)] = [
            ("Check-ins", "\(windowCheckIns.count)", "of \(period.days) days"),
            ("Avg energy", "\(avgEnergy)%", "across entries"),
            ("Symptom-free", "\(symptomFreeDays)", "of \(windowCheckIns.count) days logged"),
            ("Medicines", "\(daysWithAnyMedTaken)", "taken of \(period.days) days"),
        ]
        return LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            ForEach(tiles.indices, id: \.self) { i in
                VStack(alignment: .leading, spacing: 4) {
                    Text(tiles[i].0.uppercased()).font(KeelFont.eyebrow).tracking(0.6).foregroundStyle(theme.muted)
                    Text(tiles[i].1).font(KeelFont.serif(24, weight: .semibold)).foregroundStyle(theme.heading)
                    Text(tiles[i].2).font(KeelFont.caption).foregroundStyle(theme.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(theme.card)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous).stroke(theme.border, lineWidth: 1))
            }
        }
    }

    private var moodSection: some View {
        section("Mood & Energy") {
            VStack(alignment: .leading, spacing: 12) {
                Text("Mood distribution").font(KeelFont.eyebrow).tracking(0.6).foregroundStyle(theme.muted)
                if moodCounts.isEmpty {
                    Text("No entries yet.").font(KeelFont.body).foregroundStyle(theme.muted)
                } else {
                    HStack(spacing: 0) {
                        ForEach(Mood.allCases) { mood in
                            let f = moodFraction(mood)
                            if f > 0 { Rectangle().fill(moodColor(mood)).frame(width: max(f * barWidth, 2)) }
                        }
                    }
                    .frame(height: 20).clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    FlowLayout(spacing: 14) {
                        ForEach(Mood.allCases) { mood in
                            if moodFraction(mood) > 0 {
                                HStack(spacing: 6) {
                                    Circle().fill(moodColor(mood)).frame(width: 9, height: 9)
                                    Text("\(mood.label) \(Int(moodFraction(mood) * 100))%")
                                        .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var symptomsSection: some View {
        section("Symptoms") {
            VStack(alignment: .leading, spacing: 12) {
                Text("Frequency: days reported").font(KeelFont.eyebrow).tracking(0.6).foregroundStyle(theme.muted)
                if let vaso = vasomotorSummary {
                    Text(vaso).font(KeelFont.caption).foregroundStyle(theme.text.opacity(0.7))
                        .fixedSize(horizontal: false, vertical: true)
                }
                if topSymptoms.isEmpty {
                    Text("No symptoms logged.").font(KeelFont.body).foregroundStyle(theme.muted)
                } else {
                    let pages = symptomPages
                    if pages.count == 1 {
                        VStack(spacing: 12) { ForEach(pages[0], id: \.name) { symptomRow($0) } }
                    } else {
                        // More than 10: page them, swipe left/right between pages.
                        TabView {
                            ForEach(pages.indices, id: \.self) { i in
                                VStack(spacing: 12) {
                                    ForEach(pages[i], id: \.name) { symptomRow($0) }
                                    Spacer(minLength: 0)
                                }
                                .tag(i)
                            }
                        }
                        .tabViewStyle(.page(indexDisplayMode: .always))
                        .frame(height: CGFloat(min(topSymptoms.count, symptomsPerPage)) * 46 + 30)
                    }
                }
            }
        }
    }

    private let symptomsPerPage = 10

    /// Symptoms split into pages of `symptomsPerPage`, most frequent first.
    private var symptomPages: [[(name: String, count: Int)]] {
        stride(from: 0, to: topSymptoms.count, by: symptomsPerPage).map {
            Array(topSymptoms[$0..<min($0 + symptomsPerPage, topSymptoms.count)])
        }
    }

    private func symptomRow(_ item: (name: String, count: Int)) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text(item.name).font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8))
                Spacer()
                Text("\(item.count) \(item.count == 1 ? "day" : "days")").font(KeelFont.body).foregroundStyle(theme.muted)
            }
            ProgressCapsule(fraction: Double(item.count) / Double(max(period.days, 1)), color: theme.accent)
                .frame(height: 8)
        }
    }

    private var medsSection: some View {
        section("Medications and supplements") {
            VStack(alignment: .leading, spacing: 6) {
                if meds.isEmpty {
                    Text("No medications or supplements tracked.").font(KeelFont.body).foregroundStyle(theme.muted)
                } else {
                    Text(medsTakenLine).font(KeelFont.body).foregroundStyle(theme.text.opacity(0.85))
                        .fixedSize(horizontal: false, vertical: true)
                    Text("\(meds.count) tracked over the \(period.rawValue.lowercased()).")
                        .font(KeelFont.caption).foregroundStyle(theme.muted)
                }
            }
        }
    }

    /// A factual count, not a score. "Recorded as taken", because auto-log can mark a
    /// dose taken without her confirming it, so we never imply she reported each one.
    private var medsTakenLine: String {
        let suffix = meds.contains { $0.autoLogDoses } ? ", including days auto-logged" : ""
        return "Recorded as taken on \(daysWithAnyMedTaken) of \(period.days) days\(suffix)."
    }

    /// Distinct days in the window with at least one medicine recorded as taken.
    private var daysWithAnyMedTaken: Int {
        Set(medLogs.filter { $0.taken && $0.date >= since }.map { $0.date.startOfDay }).count
    }

    private var sleepSection: some View {
        section("Sleep") {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Hours per night").font(KeelFont.eyebrow).tracking(0.6).foregroundStyle(theme.muted)
                    Spacer()
                    if sleepAvg > 0 {
                        Text("avg \(sleepHoursText(sleepAvg))")
                            .font(KeelFont.sans(12)).foregroundStyle(theme.text.opacity(0.6))
                    }
                }
                if sleepSeries.allSatisfy({ $0 <= 0 }) {
                    Text("No sleep logged yet. Add it in a check-in, or connect Apple Health.")
                        .font(KeelFont.body).foregroundStyle(theme.muted)
                } else {
                    Text(sleepDetail).font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.text)
                    HStack(alignment: .bottom, spacing: 4) {
                        ForEach(sleepSeries.indices, id: \.self) { i in
                            let h = sleepSeries[i]
                            let isSel = selectedSleepIndex == i
                            Button {
                                Haptics.selection()
                                selectedSleepIndex = isSel ? nil : i
                            } label: {
                                RoundedRectangle(cornerRadius: 3, style: .continuous)
                                    .fill(theme.plum.opacity(h > 0 ? (isSel ? 1 : 0.8) : 0.15))
                                    .frame(height: max(CGFloat(h) / 10 * 84, 3))
                                    .frame(maxWidth: .infinity, alignment: .bottom)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .disabled(h <= 0)
                        }
                    }
                    .frame(height: 84)
                }
            }
        }
    }

    /// Resting heart rate and HRV from Apple Health, as an average and range over the
    /// period — the kind of objective baseline a GP asks about. Reuses `VitalTrend`,
    /// so every number is her own imported data (no invented figure); always shown, so
    /// she can find it, with an honest empty state before there's data.
    private var vitalsSection: some View {
        section("Body") {
            VStack(alignment: .leading, spacing: 14) {
                if hasVitals {
                    if restingHRTrend.count >= 3 { vitalRow("Resting heart rate", unit: "bpm", trend: restingHRTrend) }
                    if hrvTrend.count >= 3 { vitalRow("Heart rate variability", unit: "ms", trend: hrvTrend) }
                    if weightTrend.count >= 2 { vitalRow("Weight", unit: "kg", trend: weightTrend) }
                    if let bp = bloodPressureText {
                        HStack(alignment: .firstTextBaseline) {
                            Text("Blood pressure").font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8))
                            Spacer()
                            Text("avg \(bp) mmHg").font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.heading)
                        }
                    }
                    Text("Averages over the \(period.rawValue.lowercased()), from Apple Health. These naturally shift with sleep, stress and your cycle.")
                        .font(KeelFont.caption).foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Text("No vitals from Apple Health in this \(period.rawValue.lowercased()) yet. Resting heart rate, heart rate variability, weight and blood pressure appear here once there's data.")
                        .font(KeelFont.body).foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func vitalRow(_ title: String, unit: String, trend: VitalTrend) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8))
            Spacer()
            if let avg = trend.average {
                Text("avg \(avg) \(unit)").font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.heading)
                if let range = vitalRangeText(trend) {
                    Text(range).font(KeelFont.caption).foregroundStyle(theme.muted)
                }
            }
        }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(KeelFont.serif(17, weight: .semibold)).foregroundStyle(theme.heading)
            content()
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(theme.card)
                .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous).stroke(theme.border, lineWidth: 1))
        }
    }

    // MARK: Metrics

    private let barWidth: CGFloat = 320

    private var avgEnergy: Int {
        let v = windowCheckIns.map(\.energy)
        return v.isEmpty ? 0 : v.reduce(0, +) / v.count
    }

    private var symptomFreeDays: Int { windowCheckIns.filter { $0.symptoms.isEmpty }.count }

    // MARK: Check-ins

    /// Every calendar day (start of day) she logged at least one check-in.
    private var loggedDays: Set<Date> { Set(checkIns.map { $0.date.startOfDay }) }

    /// The past 7 calendar days, oldest first.
    private var last7Days: [Date] {
        let today = Date.now.startOfDay
        return (0..<7).map { today.adding(days: -6 + $0) }
    }

    private var moodCounts: [Mood: Int] {
        Dictionary(grouping: windowCheckIns, by: \.mood).mapValues(\.count)
    }
    private func moodFraction(_ mood: Mood) -> Double {
        guard !windowCheckIns.isEmpty else { return 0 }
        return Double(moodCounts[mood] ?? 0) / Double(windowCheckIns.count)
    }

    /// Symptom days merged from her check-ins AND Apple Health's own symptom logs
    /// (archived `symptom.*` samples on days she didn't check in), so nothing she
    /// recorded in Health is silently dropped. A day from both sources counts once.
    private var symptomTally: SymptomTally {
        var tally = SymptomTally()
        for c in windowCheckIns { for s in c.symptoms { tally.add(name: s.name, day: c.date.startOfDay) } }
        for sample in samples where sample.day >= since {
            if let name = SymptomTally.name(fromHealthTypeID: sample.typeID) {
                tally.add(name: name, day: sample.day.startOfDay)
            }
        }
        return tally
    }

    private var topSymptoms: [(name: String, count: Int)] {
        // Every symptom she logged in the window, most days first — a GP report
        // shouldn't silently drop the less common ones.
        symptomTally.ranked().map { (name: $0.name, count: $0.days) }
    }

    /// A one-line vasomotor frequency (hot flushes / night sweats), the metric that
    /// most drives a menopause treatment conversation. Nil when there are none.
    private var vasomotorSummary: String? {
        let days = symptomTally.vasomotorDays
        guard days > 0 else { return nil }
        let perWeek = Double(days) * 7 / Double(period.days)
        let rate = perWeek >= 1 ? "about \(perWeek.rounded().formatted()) a week" : "less than one a week"
        return "Hot flushes or night sweats on \(days) of the last \(period.days) days (\(rate))."
    }

    /// Sleep hours per day from activity logs; falls back to a gentle sample when empty.
    /// The days the sleep chart covers, oldest first.
    private var sleepDays: [Date] {
        Array((0..<min(period.days, 14)).map { Date.now.startOfDay.adding(days: -$0) }.reversed())
    }

    /// Real logged sleep per day only. No fabricated fallback — an empty history
    /// stays empty (the section shows an honest "nothing logged" state instead).
    private var sleepSeries: [Double] {
        sleepDays.map { day in
            activities.first { $0.deletedAt == nil && $0.activityID == "sleep" && $0.date.isSameDay(as: day) }?.amount ?? 0
        }
    }

    /// Average across nights that have data (0 when none).
    private var sleepAvg: Double {
        let vals = sleepSeries.filter { $0 > 0 }
        guard !vals.isEmpty else { return 0 }
        return vals.reduce(0, +) / Double(vals.count)
    }

    private func sleepHoursText(_ h: Double) -> String { String(format: "%.1fh", h) }

    /// The tapped night's hours. Nothing floats over the chart until she taps a bar,
    /// so a stray "Last night" no longer sits mid-chart on the most recent night.
    private var sleepDetail: String {
        guard let i = selectedSleepIndex, i < sleepDays.count, sleepSeries[i] > 0 else {
            return "Tap a bar for that night's hours."
        }
        let day = sleepDays[i]
        let label = day.isSameDay(as: .now) ? "Last night"
            : day.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
        return "\(label): \(sleepHoursText(sleepSeries[i]))"
    }

    // MARK: Vitals (from Apple Health)

    /// A `VitalTrend` over the imported daily values for one metric across the period.
    private func vitalTrend(_ typeID: String) -> VitalTrend {
        let points = samples
            .filter { $0.typeID == typeID && $0.day >= since }
            .map { VitalTrend.Point(day: $0.day.startOfDay, value: $0.value) }
        return VitalTrend(points: points)
    }

    private var restingHRTrend: VitalTrend { vitalTrend("restingHeartRate") }
    private var hrvTrend: VitalTrend { vitalTrend("hrv") }
    private var weightTrend: VitalTrend { vitalTrend("bodyMass") }
    private var systolicTrend: VitalTrend { vitalTrend("bloodPressureSystolic") }
    private var diastolicTrend: VitalTrend { vitalTrend("bloodPressureDiastolic") }
    /// "122/78" once she has a couple of cuff readings, else nil.
    private var bloodPressureText: String? {
        guard systolicTrend.count >= 2, diastolicTrend.count >= 2,
              let sys = systolicTrend.average, let dia = diastolicTrend.average else { return nil }
        return "\(sys)/\(dia)"
    }
    private var hasVitals: Bool {
        restingHRTrend.count >= 3 || hrvTrend.count >= 3 || weightTrend.count >= 2 || bloodPressureText != nil
    }

    /// "58–70" style spread, or nil when there's only one value.
    private func vitalRangeText(_ trend: VitalTrend) -> String? {
        let v = trend.values
        guard let lo = v.min(), let hi = v.max(), lo != hi else { return nil }
        return "\(Int(lo.rounded()))–\(Int(hi.rounded()))"
    }

    /// Distinct mood tones on a calm warm-to-green scale. Amber is Keel's alert colour,
    /// so it is deliberately not used here (a tester flagged amber for "Okay"), and no
    /// two neighbouring moods share a hue.
    private func moodColor(_ mood: Mood) -> Color {
        switch mood {
        case .great: Color(hex: 0x6E9E73)     // sage
        case .good: Color(hex: 0x9DBBA0)      // light sage
        case .okay: Color(hex: 0xC9AE86)      // warm sand, neutral
        case .low: Color(hex: 0xB56A5A)       // soft terracotta
        case .difficult: Color(hex: 0x8A5A66) // muted rose, serious but not alarming
        }
    }

    private var exportText: String {
        var lines = ["Your Keel records: \(period.rawValue)", ""]
        lines.append("Check-ins: \(windowCheckIns.count)")
        lines.append("Average energy: \(avgEnergy)%")
        lines.append("Symptom-free days: \(symptomFreeDays)")
        if !meds.isEmpty { lines.append("Medicines: \(medsTakenLine)") }
        if hasVitals {
            lines.append("")
            lines.append("Body (from Apple Health, averaged over the \(period.rawValue.lowercased())):")
            if let avg = restingHRTrend.average, restingHRTrend.count >= 3 {
                let range = vitalRangeText(restingHRTrend).map { " (range \($0))" } ?? ""
                lines.append("  • Resting heart rate: avg \(avg) bpm\(range)")
            }
            if let avg = hrvTrend.average, hrvTrend.count >= 3 {
                let range = vitalRangeText(hrvTrend).map { " (range \($0))" } ?? ""
                lines.append("  • Heart rate variability: avg \(avg) ms\(range)")
            }
            if let avg = weightTrend.average, weightTrend.count >= 2 {
                let range = vitalRangeText(weightTrend).map { " (range \($0))" } ?? ""
                lines.append("  • Weight: avg \(avg) kg\(range)")
            }
            if let bp = bloodPressureText {
                lines.append("  • Blood pressure: avg \(bp) mmHg")
            }
        }
        if let vaso = vasomotorSummary {
            lines.append("")
            lines.append("Vasomotor symptoms: \(vaso)")
        }
        if !topSymptoms.isEmpty {
            lines.append("")
            lines.append("Most reported symptoms (days in \(period.rawValue.lowercased()), includes Apple Health logs):")
            topSymptoms.forEach { lines.append("  • \($0.name): \($0.count) \($0.count == 1 ? "day" : "days")") }
        }
        return lines.joined(separator: "\n")
    }
}
