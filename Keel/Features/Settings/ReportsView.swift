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

    enum Period: String, CaseIterable, Identifiable { case week = "Week", month = "Month", quarter = "3 Months"; var id: String { rawValue }
        var days: Int { switch self { case .week: 7; case .month: 30; case .quarter: 90 } } }
    enum Filter: String, CaseIterable, Identifiable { case all = "All", mood = "Mood", symptoms = "Symptoms", meds = "Medications", sleep = "Sleep"; var id: String { rawValue } }

    @State private var period: Period = .month
    @State private var filter: Filter = .all

    private var since: Date { Date.now.startOfDay.adding(days: -(period.days - 1)) }
    private var windowCheckIns: [CheckIn] { checkIns.filter { $0.date >= since } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header
                periodRow
                filterRow
                statTiles
                if show(.mood) { moodSection }
                if show(.symptoms) { symptomsSection }
                if show(.meds) { medsSection }
                if show(.sleep) { sleepSection }
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    private func show(_ f: Filter) -> Bool { filter == .all || filter == f }

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

    private var filterRow: some View {
        FlowLayout(spacing: 8) {
            ForEach(Filter.allCases) { f in
                Button { filter = f; Haptics.light() } label: {
                    Text(f.rawValue).font(KeelFont.body)
                        .foregroundStyle(filter == f ? theme.background : theme.text.opacity(0.8))
                        .padding(.horizontal, 14).padding(.vertical, 7)
                        .background(filter == f ? theme.accent : theme.card)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(filter == f ? .clear : theme.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
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
                if topSymptoms.isEmpty {
                    Text("No symptoms logged.").font(KeelFont.body).foregroundStyle(theme.muted)
                } else {
                    ForEach(topSymptoms, id: \.name) { item in
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Text(item.name).font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8))
                                Spacer()
                                Text("\(item.count) days").font(KeelFont.body).foregroundStyle(theme.muted)
                            }
                            ProgressCapsule(fraction: Double(item.count) / Double(max(windowCheckIns.count, 1)), color: theme.accent)
                                .frame(height: 8)
                        }
                    }
                }
            }
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
                Text("Hours per night").font(KeelFont.eyebrow).tracking(0.6).foregroundStyle(theme.muted)
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(sleepSeries.indices, id: \.self) { i in
                        let h = sleepSeries[i]
                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                            .fill(theme.plum.opacity(h > 0 ? 0.8 : 0.15))
                            .frame(height: max(CGFloat(h) / 10 * 90, 3))
                            .frame(maxWidth: .infinity)
                    }
                }
                .frame(height: 90)
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

    private var moodCounts: [Mood: Int] {
        Dictionary(grouping: windowCheckIns, by: \.mood).mapValues(\.count)
    }
    private func moodFraction(_ mood: Mood) -> Double {
        guard !windowCheckIns.isEmpty else { return 0 }
        return Double(moodCounts[mood] ?? 0) / Double(windowCheckIns.count)
    }

    private var topSymptoms: [(name: String, count: Int)] {
        var counts: [String: Int] = [:]
        for c in windowCheckIns { for s in c.symptoms { counts[s.name, default: 0] += 1 } }
        return counts.sorted { $0.value > $1.value }.prefix(5).map { ($0.key, $0.value) }
    }

    private var adherence: Double {
        guard !meds.isEmpty else { return 0 }
        let taken = medLogs.filter { $0.taken && $0.date >= since }.count
        let total = meds.count * period.days
        return total == 0 ? 0 : min(Double(taken) / Double(total), 1)
    }

    /// Sleep hours per day from activity logs; falls back to a gentle sample when empty.
    private var sleepSeries: [Double] {
        let days = (0..<min(period.days, 14)).map { Date.now.startOfDay.adding(days: -$0) }.reversed()
        let logged = days.map { day in
            activities.first { $0.activityID == "sleep" && $0.date.isSameDay(as: day) }?.amount ?? 0
        }
        if logged.contains(where: { $0 > 0 }) { return Array(logged) }
        return [7, 6.5, 5.5, 7.5, 6, 8, 6.5, 7, 5, 7.5, 6, 6.5, 7, 8].suffix(min(period.days, 14))
    }

    private func moodColor(_ mood: Mood) -> Color {
        switch mood {
        case .great: Color(hex: 0x16A34A)
        case .good: Color(hex: 0x8BC34A)
        case .okay: Color(hex: 0xCA8A04)
        case .low: Color(hex: 0xF97316)
        case .difficult: Color(hex: 0xEF4444)
        }
    }

    private var exportText: String {
        var lines = ["Keel Report: \(period.rawValue)", ""]
        lines.append("Check-ins: \(windowCheckIns.count)")
        lines.append("Average energy: \(avgEnergy)%")
        lines.append("Symptom-free days: \(symptomFreeDays)")
        lines.append("Medication adherence: \(Int(adherence * 100))%")
        if !topSymptoms.isEmpty {
            lines.append("")
            lines.append("Most reported symptoms:")
            topSymptoms.forEach { lines.append("  • \($0.name): \($0.count) days") }
        }
        return lines.joined(separator: "\n")
    }
}
