import SwiftUI
import SwiftData

/// A Health-first daily picture (inspired by Bevel's read-from-Health approach,
/// adapted to Keel): metrics Apple Health already knows flow in automatically
/// with a gentle "vs your usual" read, the day is tied back to how she felt, and
/// only the things Health can't capture (water, eating) are logged
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

    /// Metrics that come from Apple Health automatically. Movement is deliberately a
    /// single signal — steps — rather than three overlapping ones (steps, exercise,
    /// active energy all say the same thing); sleep and mindful minutes are distinct.
    private let healthMetrics: [Metric] = [
        Metric(id: "steps", label: "Steps", symbol: "figure.walk", unit: "steps", source: .activity, decimal: false),
        Metric(id: "sleep", label: "Sleep", symbol: "moon.fill", unit: "hrs", source: .activity, decimal: true),
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

                bodySection

                eatingPanel

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

    // MARK: Your body lately (imported vitals, gently framed)

    /// How far back the baseline reads.
    private static let vitalWindow = 21

    private func vitalTrend(_ typeID: String) -> VitalTrend {
        let start = today.adding(days: -Self.vitalWindow)
        let pts = samples
            .filter { $0.typeID == typeID && $0.day >= start }
            .map { VitalTrend.Point(day: $0.day.startOfDay, value: $0.value) }
        return VitalTrend(points: pts)
    }

    private var restingHR: VitalTrend { vitalTrend("restingHeartRate") }
    private var hrv: VitalTrend { vitalTrend("hrv") }
    /// Weight is measured less often than daily, so it shows from just a couple of
    /// readings; the direction word only appears once there are enough (6+) to be fair.
    private var weight: VitalTrend { vitalTrend("bodyMass") }
    /// Overnight skin temperature from Apple Watch. Shown as a direction only (no
    /// absolute °C, which reads oddly out of Apple's baseline context and could alarm).
    private var wristTemp: VitalTrend { vitalTrend("wristTemperature") }
    private var hasVitals: Bool {
        restingHR.count >= 3 || hrv.count >= 3 || weight.count >= 2 || wristTemp.count >= 3
    }

    private var bodySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your body lately").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            if hasVitals {
                VStack(spacing: 12) {
                    if restingHR.count >= 3 { vitalRow(title: "Resting heart rate", unit: "bpm", trend: restingHR) }
                    if hrv.count >= 3 { vitalRow(title: "Heart rate variability", unit: "ms", trend: hrv) }
                    if weight.count >= 2 { vitalRow(title: "Weight", unit: "kg", trend: weight) }
                    if wristTemp.count >= 3 { vitalRow(title: "Overnight wrist temperature", unit: "°C", trend: wristTemp, showsAverage: false) }
                }
                Text(bodyNote).font(KeelFont.caption).foregroundStyle(theme.text.opacity(0.7)).lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                // Keep the charts on screen even before there's data, so she can see
                // where her vitals will appear. No invented values: empty tracks only.
                VStack(spacing: 12) {
                    emptyVitalRow(title: "Resting heart rate", unit: "bpm")
                    emptyVitalRow(title: "Heart rate variability", unit: "ms")
                }
                Text("These fill in once Apple Health has a few days of data. Weight, overnight temperature and blood pressure show up here too, when you record them.")
                    .font(KeelFont.caption).foregroundStyle(theme.text.opacity(0.7)).lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// A vital row before there's data: the title and a faint, flat placeholder track
    /// where the sparkline will be, labelled honestly rather than filled with a guess.
    private func emptyVitalRow(title: String, unit: String) -> some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(KeelFont.body).foregroundStyle(theme.text)
                Text("No data yet").font(KeelFont.caption).foregroundStyle(theme.muted)
            }
            Spacer(minLength: 8)
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(theme.track).frame(width: 78, height: 3)
        }
        .padding(14).background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func vitalRow(title: String, unit: String, trend: VitalTrend, showsAverage: Bool = true) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(KeelFont.body).foregroundStyle(theme.text)
                    if showsAverage, let avg = trend.average {
                        Text("\(Text("~\(avg)").font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading))\(Text(" \(unit)").font(KeelFont.caption).foregroundStyle(theme.muted))")
                    } else if !showsAverage {
                        Text("vs your usual").font(KeelFont.caption).foregroundStyle(theme.muted)
                    }
                }
                Spacer(minLength: 8)
                if let dir = trend.direction {
                    HStack(spacing: 3) {
                        Image(systemName: directionSymbol(dir)).font(.system(size: 10, weight: .bold))
                        Text(directionLabel(dir)).font(KeelFont.caption)
                    }
                    .foregroundStyle(theme.muted)
                }
            }
            VitalLineChart(points: trend.points, color: theme.accent, unit: unit, showsScale: showsAverage)
        }
        .padding(14).background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func directionSymbol(_ d: VitalTrend.Direction) -> String {
        switch d { case .up: "arrow.up"; case .down: "arrow.down"; case .steady: "equal" }
    }
    private func directionLabel(_ d: VitalTrend.Direction) -> String {
        switch d { case .up: "up a little"; case .down: "down a little"; case .steady: "steady" }
    }

    /// Plain, non-alarming context, with a GP nudge added only when it genuinely
    /// applies. The sleep→resting-heart-rate observation lives in Patterns and the
    /// daily reflection now (meaning, not a tile), so it isn't repeated here.
    private var bodyNote: String {
        var line = "These naturally shift with sleep, stress and where you are in your cycle."
        if restingHR.direction == .up {
            line += " If your resting heart rate keeps climbing, it's worth a mention to your GP."
        }
        return line
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
        let ids = ["water"]
        return ActivityCatalog.all.filter { ids.contains($0.id) }
    }

    // MARK: Eating today (tri-state yes/no panel, feeds the diet-trigger pattern)

    private var eatingPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Eating today").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            VStack(spacing: 0) {
                eatingGroupLabel("Nourishing")
                ForEach(EatingCatalog.nourishment) { eatingRow($0) }
                eatingGroupLabel("Might nudge symptoms")
                ForEach(EatingCatalog.triggers) { eatingRow($0) }
            }
            .padding(.horizontal, 14).padding(.vertical, 6)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
            Text("Nothing here is good or bad. It just helps Keel notice what tends to go with how you feel.")
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func eatingGroupLabel(_ text: String) -> some View {
        Text(text.uppercased()).font(KeelFont.eyebrow).tracking(0.6).foregroundStyle(theme.muted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 10).padding(.bottom, 2)
    }

    private func eatingRow(_ item: EatingItem) -> some View {
        let state = EatingLog.state(for: item.id, on: today, in: logs)
        return HStack(spacing: 12) {
            Text(item.label).font(KeelFont.body).foregroundStyle(theme.text)
            Spacer(minLength: 8)
            eatingPill("Yes", active: state == true) { setEating(item.id, state == true ? nil : true) }
            eatingPill("No", active: state == false) { setEating(item.id, state == false ? nil : false) }
        }
        .padding(.vertical, 8)
    }

    /// Neutral pill (no good/bad colour): the selected answer is filled, the other is
    /// outlined, and tapping the active one clears back to "not logged".
    private func eatingPill(_ label: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(KeelFont.sans(13, weight: .medium))
                .foregroundStyle(active ? theme.accent : theme.muted)
                .padding(.horizontal, 15).padding(.vertical, 7)
                .background(active ? theme.accent.opacity(0.14) : Color.clear)
                .overlay(Capsule().stroke(active ? theme.accent : theme.border, lineWidth: 1))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private func setEating(_ id: String, _ value: Bool?) {
        EatingLog.set(value, for: id, on: today, ownerID: env.auth.ownerID, in: context)
        Haptics.selection()
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
