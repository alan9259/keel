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
                daysInARowSection
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
            ScreenHeader(title: "Reports", titleSize: 28, subtitle: "Your body, in numbers") { dismiss() }
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

    /// Logging consistency: the current run of consecutive days with an entry, a
    /// row of the past 7 days ticked, and the longest run she's ever managed.
    private var daysInARowSection: some View {
        section("Days in a Row") {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("\(currentStreak)").font(KeelFont.serif(30, weight: .semibold)).foregroundStyle(theme.heading)
                    Text(currentStreak == 1 ? "day in a row" : "days in a row")
                        .font(KeelFont.body).foregroundStyle(theme.muted)
                }
                HStack(spacing: 6) {
                    ForEach(last7Days, id: \.self) { day in
                        let logged = loggedDays.contains(day)
                        VStack(spacing: 6) {
                            ZStack {
                                Circle().fill(logged ? theme.sage : .clear).frame(width: 26, height: 26)
                                Circle().strokeBorder(logged ? .clear : theme.border, lineWidth: 1.5).frame(width: 26, height: 26)
                                if logged {
                                    Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                                }
                            }
                            Text(letter(day)).font(KeelFont.sans(11)).foregroundStyle(theme.muted)
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityLabel("\(day.formatted(.dateTime.weekday(.wide))): \(logged ? "logged" : "not logged")")
                    }
                }
                Divider().background(theme.border)
                HStack {
                    Text("Longest streak").font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8))
                    Spacer()
                    Text(longestStreak == 1 ? "1 day" : "\(longestStreak) days")
                        .font(KeelFont.body).foregroundStyle(theme.muted)
                }
            }
        }
    }

    private var statTiles: some View {
        let tiles: [(String, String, String)] = [
            ("Check-ins", "\(windowCheckIns.count)", "in \(period.rawValue.lowercased())"),
            ("Avg energy", "\(avgEnergy)%", "across entries"),
            ("Symptom-free", "\(symptomFreeDays)", "days logged"),
            ("Adherence", "\(Int(adherence * 100))%", "medications"),
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
                Text("\(item.count) days").font(KeelFont.body).foregroundStyle(theme.muted)
            }
            ProgressCapsule(fraction: Double(item.count) / Double(max(period.days, 1)), color: theme.accent)
                .frame(height: 8)
        }
    }

    private var medsSection: some View {
        section("Medications") {
            HStack(spacing: 18) {
                ZStack {
                    Circle().stroke(theme.track, lineWidth: 8).frame(width: 74, height: 74)
                    Circle().trim(from: 0, to: adherence).stroke(theme.sage, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                        .rotationEffect(.degrees(-90)).frame(width: 74, height: 74)
                    Text("\(Int(adherence * 100))%").font(KeelFont.sans(15, weight: .semibold)).foregroundStyle(theme.heading)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Adherence rate").font(KeelFont.body).foregroundStyle(theme.text)
                    Text(meds.isEmpty ? "No medications tracked" : "\(meds.count) tracked over \(period.days) days")
                        .font(KeelFont.caption).foregroundStyle(theme.muted)
                }
                Spacer()
            }
        }
    }

    private var sleepSection: some View {
        section("Sleep") {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Hours per night").font(KeelFont.eyebrow).tracking(0.6).foregroundStyle(theme.muted)
                    Spacer()
                    if sleepAvg > 0 {
                        Text("avg \(sleepHoursText(sleepAvg)) · score \(sleepScore)")
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

    // MARK: Streak

    /// Every calendar day (start of day) she logged at least one check-in.
    private var loggedDays: Set<Date> { Set(checkIns.map { $0.date.startOfDay }) }

    /// The past 7 calendar days, oldest first.
    private var last7Days: [Date] {
        let today = Date.now.startOfDay
        return (0..<7).map { today.adding(days: -6 + $0) }
    }

    /// Consecutive days with an entry, counting back from today. If today isn't
    /// logged yet the run isn't broken — it counts back from yesterday.
    private var currentStreak: Int {
        var day = Date.now.startOfDay
        if !loggedDays.contains(day) { day = day.adding(days: -1) }
        var streak = 0
        while loggedDays.contains(day) { streak += 1; day = day.adding(days: -1) }
        return streak
    }

    /// The longest run of consecutive logged days across all her history.
    private var longestStreak: Int {
        let sorted = loggedDays.sorted()
        guard !sorted.isEmpty else { return 0 }
        var longest = 1, run = 1
        for i in 1..<sorted.count {
            run = sorted[i - 1].adding(days: 1).isSameDay(as: sorted[i]) ? run + 1 : 1
            longest = max(longest, run)
        }
        return longest
    }

    private func letter(_ date: Date) -> String {
        let cal = Calendar.current
        return cal.veryShortStandaloneWeekdaySymbols[cal.component(.weekday, from: date) - 1]
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

    private var adherence: Double {
        guard !meds.isEmpty else { return 0 }
        let taken = medLogs.filter { $0.taken && $0.date >= since }.count
        let total = meds.count * period.days
        return total == 0 ? 0 : min(Double(taken) / Double(total), 1)
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

    private let sleepGoalHours: Double = 8

    /// Average across nights that have data (0 when none).
    private var sleepAvg: Double {
        let vals = sleepSeries.filter { $0 > 0 }
        guard !vals.isEmpty else { return 0 }
        return vals.reduce(0, +) / Double(vals.count)
    }

    /// 0–100 derived from average sleep against the nightly goal (not an Apple score).
    private var sleepScore: Int { min(100, Int((sleepAvg / sleepGoalHours * 100).rounded())) }

    private func sleepHoursText(_ h: Double) -> String { String(format: "%.1fh", h) }

    /// The tapped night's hours, else the most recent night with sleep.
    private var sleepDetail: String {
        let idx = selectedSleepIndex ?? sleepSeries.lastIndex(where: { $0 > 0 })
        guard let i = idx, i < sleepDays.count, sleepSeries[i] > 0 else { return "Tap a bar for that night." }
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

    private func moodColor(_ mood: Mood) -> Color {
        switch mood {
        case .great: Color(hex: 0x16A34A)
        case .good: Color(hex: 0x8BC34A)
        case .okay: Color(hex: 0xCA8A04)
        case .low: Color(hex: 0xF97316)
        case .difficult: Color(hex: 0xA9762F)
        }
    }

    private var exportText: String {
        var lines = ["Keel Report: \(period.rawValue)", ""]
        lines.append("Check-ins: \(windowCheckIns.count)")
        lines.append("Average energy: \(avgEnergy)%")
        lines.append("Symptom-free days: \(symptomFreeDays)")
        lines.append("Days in a row: \(currentStreak) (longest: \(longestStreak))")
        lines.append("Medication adherence: \(Int(adherence * 100))%")
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
            topSymptoms.forEach { lines.append("  • \($0.name): \($0.count) days") }
        }
        return lines.joined(separator: "\n")
    }
}
