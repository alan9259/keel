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
    @Query(filter: #Predicate<HealthActivitySample> { $0.deletedAt == nil }) private var importedActivity: [HealthActivitySample]
    @Query(filter: #Predicate<HealthSample> { $0.deletedAt == nil }) private var samples: [HealthSample]
    @Query(filter: #Predicate<CheckIn> { $0.deletedAt == nil }) private var checkIns: [CheckIn]

    private var today: Date { Date.now.startOfDay }
    private var cal: Calendar { .current }

    /// Day / Week / Month, and the date the current window is anchored on.
    @State private var period: ActivityPeriod = {
        #if DEBUG
        if let p = DebugHarness.activitiesPeriod { return p }
        #endif
        return .day
    }()
    @State private var anchor: Date = Date.now.startOfDay

    /// The single day shown in Day mode.
    private var selectedDay: Date { cal.startOfDay(for: anchor) }
    /// The days covered by the current window (one in Day mode, the week or month otherwise).
    private var visibleDays: [Date] {
        ActivityAggregation.days(in: ActivityAggregation.interval(for: period, containing: anchor, calendar: cal), calendar: cal)
    }
    /// Days in the window that have actually happened, for "X of N days" denominators.
    private var elapsedDays: [Date] { visibleDays.filter { $0 <= today } }

    private enum Source { case activity, sample }
    private struct Metric: Identifiable {
        let id: String, label: String, symbol: String, unit: String
        let source: Source
        /// Whole numbers vs one decimal (e.g. sleep hours).
        let decimal: Bool
        /// How a week/month rolls up: a running count sums, a level averages.
        let aggregate: ActivityAggregation.Mode
    }

    /// Metrics that come from Apple Health automatically. She chooses which of these
    /// to import on the Apple Health screen (`HealthSyncCatalog`); a tile still shows
    /// data already imported for an item she has since switched off.
    private let allHealthMetrics: [Metric] = [
        Metric(id: "steps", label: "Steps", symbol: "figure.walk", unit: "steps", source: .activity, decimal: false, aggregate: .total),
        Metric(id: "exercise", label: "Exercise", symbol: "flame.fill", unit: "min", source: .activity, decimal: false, aggregate: .total),
        Metric(id: "activeEnergy", label: "Active energy", symbol: "bolt.fill", unit: "kcal", source: .sample, decimal: false, aggregate: .total),
        Metric(id: "distance", label: "Distance", symbol: "figure.walk.motion", unit: "km", source: .sample, decimal: true, aggregate: .total),
        Metric(id: "flights", label: "Flights", symbol: "stairs", unit: "", source: .sample, decimal: false, aggregate: .total),
        Metric(id: "sleep", label: "Sleep", symbol: "moon.fill", unit: "hrs", source: .activity, decimal: true, aggregate: .average),
    ]

    /// Metrics to show: everything she is still importing, plus anything she has
    /// switched off that nonetheless has data in the visible window (so nothing
    /// quietly disappears).
    private var shownMetrics: [Metric] {
        let disabled = env.settings.disabledHealthItemIDs
        let days = visibleDays
        return allHealthMetrics.filter { m in
            !disabled.contains(m.id) || days.contains { value(for: m, on: $0) != nil }
        }
    }

    private var healthConnected: Bool { env.users.currentProfile()?.healthKitAuthorized == true }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Activities", titleSize: 28) { dismiss() }

                periodPicker
                periodNav

                if period == .day {
                    feltCard
                    healthSection
                    bodySection
                    eatingPanel
                    manualSection
                } else {
                    aggregatedHealthSection
                    bodySection
                    periodSummaryCard
                }
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    // MARK: Period picker + navigation

    private var periodPicker: some View {
        KeelSegmented(options: ActivityPeriod.allCases.map(\.label), selection: Binding(
            get: { ActivityPeriod.allCases.firstIndex(of: period) ?? 0 },
            set: { period = ActivityPeriod.allCases[$0] }
        ))
    }

    private var periodNav: some View {
        HStack {
            iconButton("chevron.left") { shift(-1) }
            Spacer()
            Text(periodLabel).font(KeelFont.serif(17, weight: .semibold)).foregroundStyle(theme.heading)
            Spacer()
            iconButton("chevron.right", disabled: isCurrentPeriod) { shift(1) }
        }
    }

    private var isCurrentPeriod: Bool {
        ActivityAggregation.isCurrent(anchor, period: period, calendar: cal, now: today)
    }

    private func shift(_ delta: Int) {
        anchor = ActivityAggregation.shift(anchor, by: delta, period: period, calendar: cal, notAfter: today)
        Haptics.selection()
    }

    private var periodLabel: String {
        switch period {
        case .day:
            if selectedDay.isSameDay(as: today) { return "Today" }
            if selectedDay.isSameDay(as: today.adding(days: -1)) { return "Yesterday" }
            return selectedDay.formatted(.dateTime.weekday(.wide).month().day())
        case .week:
            if isCurrentPeriod { return "This week" }
            let iv = ActivityAggregation.interval(for: .week, containing: anchor, calendar: cal)
            let last = cal.date(byAdding: .day, value: -1, to: iv.end) ?? iv.start
            let start = iv.start.formatted(.dateTime.day().month(.abbreviated))
            let end = last.formatted(.dateTime.day().month(.abbreviated))
            return "\(start) – \(end)"
        case .month:
            if isCurrentPeriod { return "This month" }
            return anchor.formatted(.dateTime.month(.wide).year())
        }
    }

    private func iconButton(_ icon: String, disabled: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(disabled ? theme.muted.opacity(0.3) : theme.text)
                .frame(width: 36, height: 36)
        }
        .disabled(disabled)
        .accessibilityLabel(icon == "chevron.left" ? "Previous \(period.label.lowercased())" : "Next \(period.label.lowercased())")
    }

    // MARK: How you felt

    @ViewBuilder
    private var feltCard: some View {
        if let checkIn = checkIns.first(where: { $0.date.isSameDay(as: selectedDay) }) {
            HStack(spacing: 14) {
                EmojiGlyph(emoji: env.settings.emoji(for: checkIn.mood), size: 30)
                VStack(alignment: .leading, spacing: 2) {
                    Text(selectedDay.isSameDay(as: today) ? "How you felt today" : "How you felt")
                        .font(KeelFont.caption).foregroundStyle(theme.muted)
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
            connectHealthPrompt
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(shownMetrics) { metric in
                    metricTile(metric)
                }
            }
        }
    }

    @ViewBuilder
    private var connectHealthPrompt: some View {
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
    }

    // MARK: From Apple Health (week / month roll-up)

    private var aggregatedHealthSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("From Apple Health").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            connectHealthPrompt
            VStack(spacing: 12) {
                ForEach(shownMetrics) { aggregatedMetricCard($0) }
            }
        }
    }

    /// One metric across the window: label, the period total or average, and a small
    /// per-day bar chart. Honest empty state when there is nothing yet.
    private func aggregatedMetricCard(_ metric: Metric) -> some View {
        let days = visibleDays
        let roll = ActivityAggregation.rollup(byDay: series(for: metric), days: days, mode: metric.aggregate, calendar: cal)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Image(systemName: metric.symbol).font(.system(size: 15)).foregroundStyle(theme.accent)
                Text(metric.label).font(KeelFont.body).foregroundStyle(theme.text)
                Spacer(minLength: 8)
                if let s = roll.summary {
                    Text("\(Text(format(s, decimal: metric.decimal)).font(KeelFont.serif(20, weight: .semibold)))\(Text(metric.unit.isEmpty ? "" : " \(metric.unit)").font(KeelFont.caption))\(Text(metric.aggregate == .total ? " total" : " avg").font(KeelFont.caption).foregroundColor(theme.muted))")
                        .foregroundStyle(theme.heading).lineLimit(1).minimumScaleFactor(0.7)
                } else {
                    Text("No data yet").font(KeelFont.caption).foregroundStyle(theme.muted)
                }
            }
            barChart(roll.perDay)
        }
        .padding(14)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    /// Faint per-day bars, normalised to the window's own peak. Empty days show a
    /// low placeholder track so the shape reads without inventing a value.
    private func barChart(_ values: [Double]) -> some View {
        let peak = max(values.max() ?? 0, 1)
        return HStack(alignment: .bottom, spacing: values.count > 10 ? 2 : 4) {
            ForEach(Array(values.enumerated()), id: \.offset) { _, v in
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(v > 0 ? theme.accent.opacity(0.65) : theme.track.opacity(0.5))
                    .frame(height: max(3, CGFloat(v / peak) * 44))
                    .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 44, alignment: .bottom)
        .accessibilityHidden(true)
    }

    // MARK: Day-only summary (week / month)

    private var periodSummaryCard: some View {
        let n = max(elapsedDays.count, 1)
        let daySet = Set(elapsedDays.map { cal.startOfDay(for: $0) })
        let checkInDays = Set(checkIns.map { cal.startOfDay(for: $0.date) }).intersection(daySet).count
        let waterDays = Set(logs.filter { $0.activityID == "water" && $0.amount > 0 }
            .map { cal.startOfDay(for: $0.date) }).intersection(daySet).count
        return VStack(alignment: .leading, spacing: 12) {
            Text("You logged").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            VStack(spacing: 0) {
                summaryRow(symbol: "sun.max.fill", label: "Checked in", value: "\(checkInDays) of \(n) days")
                Divider().background(theme.border)
                summaryRow(symbol: "drop.fill", label: "Water logged", value: "\(waterDays) of \(n) days")
            }
            .padding(.horizontal, 14).padding(.vertical, 4)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
        }
    }

    private func summaryRow(symbol: String, label: String, value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 14)).foregroundStyle(theme.accent).frame(width: 24)
            Text(label).font(KeelFont.body).foregroundStyle(theme.text)
            Spacer(minLength: 8)
            Text(value).font(KeelFont.sans(14, weight: .medium)).foregroundStyle(theme.muted)
        }
        .padding(.vertical, 12)
    }

    /// A Bevel-style glanceable tile: big value, unit, gentle direction arrow.
    private func metricTile(_ metric: Metric) -> some View {
        let value = value(for: metric, on: selectedDay)
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
                // No qualitative characterisation (steady/up/down): the number, its
                // range and the graph speak for themselves. Keel does not judge whether
                // physiological data is steady, normal or unusual.
            }
            VitalLineChart(points: trend.points, color: theme.accent, unit: unit, showsScale: true)
        }
        .padding(14).background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    /// Plain, non-alarming context, with a GP nudge added only when it genuinely
    /// applies. The sleep→resting-heart-rate observation lives in Patterns and the
    /// daily reflection now (meaning, not a tile), so it isn't repeated here.
    private var bodyNote: String {
        var line = "Recent readings from Apple Health, shown as they were recorded."
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
            Text("Eating").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            VStack(spacing: 0) {
                eatingGroupLabel("Nourishing")
                ForEach(EatingCatalog.nourishment) { eatingRow($0) }
                // The symptom-trigger rows are held back with the correlation they feed,
                // pending clinical review (product alignment note).
                if DietTriggerCorrelation.surfacesToUser {
                    eatingGroupLabel("Might nudge symptoms")
                    ForEach(EatingCatalog.triggers) { eatingRow($0) }
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 6)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
            Text(DietTriggerCorrelation.surfacesToUser
                 ? "Nothing here is good or bad. It just helps Keel notice what tends to go with how you feel."
                 : "Tap what fits, or leave it. Nothing here is scored or judged.")
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
        let state = EatingLog.state(for: item.id, on: selectedDay, in: logs)
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
        EatingLog.set(value, for: id, on: selectedDay, ownerID: env.auth.ownerID, in: context)
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

    private func value(for metric: Metric, on date: Date) -> Double? {
        switch metric.source {
        case .activity:
            // Her manual entry wins; otherwise the Apple Health import (health store).
            return MergedActivity.amount(metric.id, on: date, manual: logs, imported: importedActivity)
        case .sample:
            return samples.first { $0.typeID == metric.id && $0.day.isSameDay(as: date) }.map(\.value)
        }
    }

    /// A metric's full per-day series, for the week/month roll-up.
    private func series(for metric: Metric) -> [Date: Double] {
        switch metric.source {
        case .activity:
            return MergedActivity.byDay(metric.id, manual: logs, imported: importedActivity)
        case .sample:
            var out: [Date: Double] = [:]
            for s in samples where s.typeID == metric.id { out[s.day.startOfDay] = s.value }
            return out
        }
    }

    private struct TrendDir { let symbol: String }

    /// A gentle read of today against her own recent days. Direction only (a plain
    /// arrow, no judging colour), never an invented statistic. Needs a few days of
    /// history before it says anything.
    private func trendDirection(_ metric: Metric, value: Double?) -> TrendDir? {
        guard let value else { return nil }
        let start = selectedDay.adding(days: -7)
        let past: [Double]
        switch metric.source {
        case .activity:
            past = MergedActivity.byDay(metric.id, manual: logs, imported: importedActivity)
                .filter { $0.key >= start && $0.key < selectedDay && $0.value > 0 }
                .map(\.value)
        case .sample:
            past = samples.filter { $0.typeID == metric.id && $0.day >= start && $0.day < selectedDay }.map(\.value)
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
        logs.first { $0.activityID == id && $0.date.isSameDay(as: selectedDay) }
    }
    private func amount(for id: String) -> Double { log(for: id)?.amount ?? 0 }

    private func setAmount(_ id: String, _ amount: Double) {
        if let existing = log(for: id) {
            existing.amount = amount
        } else if amount > 0 {
            context.insert(ActivityLog(date: selectedDay, activityID: id, amount: amount, ownerID: env.auth.ownerID))
        }
        try? context.save()
    }

    private func format(_ v: Double, decimal: Bool) -> String {
        if decimal && v != v.rounded() { return String(format: "%.1f", v) }
        if v >= 1000 { return v.formatted(.number.grouping(.automatic)) }
        return String(Int(v.rounded()))
    }
}
