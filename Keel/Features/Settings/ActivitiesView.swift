import SwiftUI
import SwiftData

/// A Health-first daily picture (inspired by Bevel's read-from-Health approach,
/// adapted to Keel): metrics Apple Health already knows flow in automatically
/// with a gentle "vs your usual" read, the day is tied back to how she felt, and
/// only the things Health can't capture (water, eating, journalling) are logged
/// by hand. No invented fitness scores.
struct ActivitiesView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @Query(filter: #Predicate<ActivityLog> { $0.deletedAt == nil }) private var logs: [ActivityLog]
    @Query(filter: #Predicate<HealthSample> { $0.deletedAt == nil }) private var samples: [HealthSample]
    @Query(filter: #Predicate<CheckIn> { $0.deletedAt == nil }) private var checkIns: [CheckIn]

    private var today: Date { Date.now.startOfDay }

    private enum Source { case activity, sample }
    private struct Metric: Identifiable {
        let id: String, label: String, symbol: String, unit: String
        let source: Source
        /// Whole numbers vs one decimal (e.g. sleep hours).
        let decimal: Bool
    }

    /// Metrics that come from Apple Health automatically.
    private let healthMetrics: [Metric] = [
        Metric(id: "steps", label: "Steps", symbol: "figure.walk", unit: "steps", source: .activity, decimal: false),
        Metric(id: "exercise", label: "Exercise", symbol: "figure.strengthtraining.traditional", unit: "min", source: .activity, decimal: false),
        Metric(id: "sleep", label: "Sleep", symbol: "moon.fill", unit: "hrs", source: .activity, decimal: true),
        Metric(id: "activeEnergy", label: "Active energy", symbol: "flame.fill", unit: "kcal", source: .sample, decimal: false),
        Metric(id: "meditation", label: "Mindful", symbol: "wind", unit: "min", source: .activity, decimal: false),
    ]

    private var healthConnected: Bool { env.users.currentProfile()?.healthKitAuthorized == true }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Activities", titleSize: 28,
                             subtitle: Date.now.formatted(.dateTime.weekday(.wide).month().day())) { dismiss() }

                feltCard

                healthSection

                manualSection
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    // MARK: How you felt

    @ViewBuilder
    private var feltCard: some View {
        if let checkIn = checkIns.first(where: { $0.date.isSameDay(as: today) }) {
            HStack(spacing: 14) {
                EmojiGlyph(emoji: env.settings.emoji(for: checkIn.mood), size: 30)
                VStack(alignment: .leading, spacing: 2) {
                    Text("How you felt today").font(KeelFont.caption).foregroundStyle(theme.muted)
                    Text("\(checkIn.mood.label) · energy \(EnergyLevel.from(percent: checkIn.energy).label.lowercased())")
                        .font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .background(theme.accent.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.accentBorder, lineWidth: 1))
        }
    }

    // MARK: From Apple Health

    private var healthSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("From Apple Health").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)

            if !healthConnected {
                NavigationLink(value: MainRoute.appleHealth) {
                    HStack(spacing: 12) {
                        Image(systemName: "heart.fill").font(.system(size: 15)).foregroundStyle(theme.accent)
                            .frame(width: 40, height: 40).background(theme.accent.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Connect Apple Health").font(KeelFont.body).foregroundStyle(theme.text)
                            Text("Let your steps, sleep and more fill in on their own").font(KeelFont.caption).foregroundStyle(theme.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(theme.muted)
                    }
                    .padding(14).background(theme.card)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(healthMetrics) { metric in
                    metricTile(metric)
                }
            }
        }
    }

    /// A Bevel-style glanceable tile: big value, unit, gentle direction arrow.
    private func metricTile(_ metric: Metric) -> some View {
        let value = todayValue(metric)
        let has = value != nil
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: metric.symbol).font(.system(size: 16))
                    .foregroundStyle(has ? theme.accent : theme.muted)
                Spacer()
                if let dir = trendDirection(metric, value: value) {
                    Image(systemName: dir.symbol).font(.system(size: 11, weight: .bold)).foregroundStyle(theme.muted)
                }
            }
            Spacer(minLength: 6)
            if let value {
                Text("\(Text(format(value, decimal: metric.decimal)).font(KeelFont.serif(24, weight: .semibold)))\(Text(" \(metric.unit)").font(KeelFont.caption))")
                    .foregroundStyle(theme.heading).lineLimit(1).minimumScaleFactor(0.7)
            } else {
                Text("No data yet").font(KeelFont.caption).foregroundStyle(theme.muted)
            }
            Text(metric.label).font(KeelFont.caption).foregroundStyle(theme.muted)
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 104, alignment: .leading)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    // MARK: You logged (manual)

    private var manualSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("You logged").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            VStack(spacing: 12) {
                ForEach(manualActivities) { activity in
                    manualRow(activity)
                }
            }
        }
    }

    private var manualActivities: [ActivityDef] {
        let ids = ["water", "eating", "journal"]
        return ActivityCatalog.all.filter { ids.contains($0.id) }
    }

    @ViewBuilder
    private func manualRow(_ activity: ActivityDef) -> some View {
        let value = amount(for: activity.id)
        let done = value > 0
        HStack(spacing: 14) {
            Image(systemName: activity.symbol).font(.system(size: 19))
                .foregroundStyle(done ? theme.accent : theme.muted)
                .frame(width: 44, height: 44)
                .background(done ? theme.accent.opacity(0.12) : theme.inputBackground)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(activity.label).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                if let unit = activity.unit, done {
                    Text("\(format(value, decimal: false)) \(unit)").font(KeelFont.caption).foregroundStyle(theme.accent)
                }
            }
            Spacer(minLength: 8)
            if activity.unit != nil {
                stepper(activity, value: value)
            } else {
                toggle(activity, done: done)
            }
        }
        .padding(14).background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func toggle(_ activity: ActivityDef, done: Bool) -> some View {
        Button {
            setAmount(activity.id, done ? 0 : 1); Haptics.success()
        } label: {
            ZStack {
                Circle().fill(done ? theme.accent : .clear).frame(width: 32, height: 32)
                Circle().stroke(done ? .clear : theme.border, lineWidth: 2).frame(width: 32, height: 32)
                if done { Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(theme.background) }
            }
        }
        .buttonStyle(.plain)
    }

    private func stepper(_ activity: ActivityDef, value: Double) -> some View {
        HStack(spacing: 12) {
            stepButton("minus") { setAmount(activity.id, max(0, value - activity.step)) }
            Text(format(value, decimal: false)).font(KeelFont.sans(15, weight: .semibold)).foregroundStyle(theme.text)
                .frame(minWidth: 26)
            stepButton("plus") { setAmount(activity.id, value + activity.step) }
        }
    }

    private func stepButton(_ icon: String, action: @escaping () -> Void) -> some View {
        Button { action(); Haptics.selection() } label: {
            Image(systemName: icon).font(.system(size: 13, weight: .bold))
                .foregroundStyle(theme.accent).frame(width: 30, height: 30)
                .background(theme.accent.opacity(0.12)).clipShape(Circle())
        }
        .buttonStyle(.plain)
    }

    // MARK: Values + trend

    private func todayValue(_ metric: Metric) -> Double? {
        switch metric.source {
        case .activity:
            return logs.first { $0.activityID == metric.id && $0.date.isSameDay(as: today) }.map(\.amount).flatMap { $0 > 0 ? $0 : nil }
        case .sample:
            return samples.first { $0.typeID == metric.id && $0.day.isSameDay(as: today) }.map(\.value)
        }
    }

    private struct TrendDir { let symbol: String }

    /// A gentle read of today against her own recent days. Direction only (a plain
    /// arrow, no judging colour), never an invented statistic. Needs a few days of
    /// history before it says anything.
    private func trendDirection(_ metric: Metric, value: Double?) -> TrendDir? {
        guard let value else { return nil }
        let start = today.adding(days: -7)
        let past: [Double]
        switch metric.source {
        case .activity:
            past = logs.filter { $0.activityID == metric.id && $0.date >= start && !$0.date.isSameDay(as: today) && $0.amount > 0 }.map(\.amount)
        case .sample:
            past = samples.filter { $0.typeID == metric.id && $0.day >= start && !$0.day.isSameDay(as: today) }.map(\.value)
        }
        guard past.count >= 3 else { return nil }
        let avg = past.reduce(0, +) / Double(past.count)
        guard avg > 0 else { return nil }
        if value >= avg * 1.1 { return TrendDir(symbol: "arrow.up") }
        if value <= avg * 0.9 { return TrendDir(symbol: "arrow.down") }
        return TrendDir(symbol: "equal")
    }

    // MARK: Manual data

    private func log(for id: String) -> ActivityLog? {
        logs.first { $0.activityID == id && $0.date.isSameDay(as: today) }
    }
    private func amount(for id: String) -> Double { log(for: id)?.amount ?? 0 }

    private func setAmount(_ id: String, _ amount: Double) {
        if let existing = log(for: id) {
            existing.amount = amount
        } else if amount > 0 {
            context.insert(ActivityLog(date: today, activityID: id, amount: amount, ownerID: env.auth.ownerID))
        }
        try? context.save()
    }

    private func format(_ v: Double, decimal: Bool) -> String {
        if decimal && v != v.rounded() { return String(format: "%.1f", v) }
        if v >= 1000 { return v.formatted(.number.grouping(.automatic)) }
        return String(Int(v.rounded()))
    }
}
