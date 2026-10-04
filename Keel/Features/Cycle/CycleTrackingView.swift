import SwiftUI
import SwiftData

/// Cycle tracking in the shape of Apple Health's, on her own data: a horizontal
/// timeline of the current cycle, an honest status and history, and tap-to-log
/// flow. Nothing here is invented. When there isn't enough to say (few cycles, or
/// too variable, common in perimenopause), it says so rather than guessing.
struct CycleTrackingView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var month = Date.now
    @State private var editing: Date?
    @State private var refresh = 0
    /// Period marks for the visible days, computed once per change (not per day drawn).
    @State private var marks: [Date: CycleDayMark] = [:]
    /// Imported period days, observed so a sync that lands while this screen is open
    /// shows straight away.
    @Query(filter: #Predicate<HealthFlowSample> { $0.deletedAt == nil }) private var importedRows: [HealthFlowSample]
    private let cal = Calendar.current

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ScreenHeader(title: "Cycle") { dismiss() }

                // The timeline is the screen's top pane in every state: with data it
                // shows her current cycle; empty, it shows a recent week she can tap to
                // log into (rather than being hidden behind a separate card).
                timelineCard
                if hasAnyPeriod {
                    if stats.typicalRange != nil { historyCard }
                    phaseCard
                    if let insight { insightCard(insight) }
                }
                logPastCard
                backgroundLink
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .sheet(item: editingBinding) { day in
            CycleDaySheet(date: day.date, current: env.cycle.flow(on: day.date),
                          fromAppleHealth: env.cycle.importedFlow(from: day.date, to: day.date)[day.date.startOfDay]) { level in
                env.cycle.setFlow(level, on: day.date)
                refresh += 1
            }
            // Taller detent for large text sizes, where the Apple Health note wraps more.
            .presentationDetents([.height(360), .large])
            .presentationDragIndicator(.visible)
        }
        .task(id: marksKey) { marks = computeMarks() }
        .onAppear {
            _ = refresh
            #if DEBUG
            if DebugHarness.showCycleSheet { editing = Date.now.startOfDay.adding(days: -2) }
            #endif
        }
    }

    /// A gentle way out for someone this screen doesn't fit (3C): leads to Profile,
    /// whose My background section is where she can note that periods no longer apply or add her
    /// background. Nothing here changes because of it.
    private var backgroundLink: some View {
        NavigationLink(value: MainRoute.profile) {
            Text("Periods don't apply to you? You can turn this off, or add your background in Profile.")
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .underline(color: theme.muted.opacity(0.4))
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.top, 4)
    }

    // MARK: Data (recomputed on `refresh`)

    private var stats: CycleStats { _ = refresh; return env.cycle.stats(lookbackDays: 400, now: .now) }
    private var today: Date { Date.now.startOfDay }
    private var hasAnyPeriod: Bool { stats.lastStart != nil }

    /// A real cycle-variability observation from the shared engine, never a hardcoded
    /// line. Nil when there's no genuine signal yet.
    private var insight: PatternFinding? {
        _ = refresh
        return PatternEngine.build(context: env.context).findings().first { $0.kind == .cycleVariability }
    }

    // MARK: Timeline (hero)

    private var timelineCard: some View {
        StandardCard(padding: 20) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Your cycle").font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                Text(statusLine).font(KeelFont.body).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true).padding(.bottom, 16)

                ScrollViewReader { proxy in
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 9) {
                            ForEach(railDays, id: \.self) { day in dayPip(day) }
                        }
                        .padding(.horizontal, 2)
                    }
                    .onAppear { proxy.scrollTo(today, anchor: .center) }
                }
                if hasAnyPeriod || hasAnyImported {
                    legend.padding(.top, 16)
                }
                if hasAnyImported {
                    Text("Days outlined are from Apple Health. They're shown here, but Keel's estimates only use the periods you log in Keel.")
                        .font(KeelFont.caption).foregroundStyle(theme.muted).lineSpacing(2)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 10)
                }
                if !hasAnyPeriod {
                    Text("Tap a day here or in the calendar below to log a period, and Keel will build your timeline, keep your cycle lengths, and gently estimate the next one once there's a pattern.")
                        .font(KeelFont.caption).foregroundStyle(theme.text.opacity(0.7)).lineSpacing(2)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 14)
                }
            }
        }
    }

    private func dayPip(_ day: Date) -> some View {
        let mark = marks[day]
        let isToday = day == today
        let inEstimate = estimateWindow?.contains(day) == true && day > today
        let isFuture = day > today
        return Button {
            Haptics.selection(); editing = day
        } label: {
            VStack(spacing: 6) {
                Text(weekdayInitial(day))
                    .font(KeelFont.sans(11)).foregroundStyle(theme.muted)
                Text("\(cal.component(.day, from: day))")
                    .font(KeelFont.sans(13, weight: isToday ? .semibold : .regular))
                    .foregroundStyle(pipTextColor(mark: mark, isFuture: isFuture))
                    .frame(width: 34, height: 34)
                    .background(mark.map { theme.accent.opacity(fillOpacity($0)) } ?? .clear)
                    .clipShape(Circle())
                    .overlay(pipOverlay(isToday: isToday, inEstimate: inEstimate,
                                        fromAppleHealth: mark?.fromAppleHealth == true))
            }
        }
        .buttonStyle(.plain)
        .id(day)
        .accessibilityLabel(accessibilityLabel(day: day, mark: mark, inEstimate: inEstimate))
    }

    @ViewBuilder
    private func pipOverlay(isToday: Bool, inEstimate: Bool, fromAppleHealth: Bool) -> some View {
        if isToday {
            Circle().stroke(theme.text, lineWidth: 2)
        } else if fromAppleHealth {
            Circle().stroke(theme.accent, lineWidth: 1.5)
        } else if inEstimate {
            Circle().stroke(theme.accent, style: StrokeStyle(lineWidth: 1.5, dash: [3, 2.5]))
        }
    }

    private func pipTextColor(mark: CycleDayMark?, isFuture: Bool) -> Color {
        if let mark, fillOpacity(mark) >= 0.7 { return theme.onFill }
        return isFuture ? theme.muted.opacity(0.7) : theme.text
    }

    /// Her own days fill by heaviness; Apple Health days are a lighter fill with an
    /// outline (see the legend), so the two are never confused.
    private func fillOpacity(_ mark: CycleDayMark) -> Double {
        mark.fromAppleHealth ? fillOpacity(mark.level) * 0.35 : fillOpacity(mark.level)
    }

    private func fillOpacity(_ level: FlowLevel) -> Double {
        switch level {
        case .spotting: 0.38
        case .light: 0.6
        case .medium, .unspecified: 0.9
        case .heavy: 1.0
        }
    }

    private var legend: some View {
        // Wraps rather than squeezing, so five items never break mid-word.
        FlowLayout(spacing: 14) {
            legendItem(fill: theme.accent, "Period")
            legendItem(fill: theme.accent.opacity(0.5), "Lighter")
            if hasAnyImported {
                HStack(spacing: 6) {
                    Circle().fill(theme.accent.opacity(0.3)).overlay(Circle().stroke(theme.accent, lineWidth: 1.5))
                        .frame(width: 12, height: 12)
                    Text("Apple Health")
                }
            }
            legendChip(ring: theme.text, "Today")
            legendChip(ring: theme.accent, dashed: true, "Estimated")
        }
        .font(KeelFont.sans(11)).foregroundStyle(theme.muted)
    }
    private func legendItem(fill: Color, _ label: String) -> some View {
        HStack(spacing: 6) { Circle().fill(fill).frame(width: 12, height: 12); Text(label) }
    }
    private func legendChip(ring: Color, dashed: Bool = false, _ label: String) -> some View {
        HStack(spacing: 6) {
            Circle().stroke(ring, style: StrokeStyle(lineWidth: 1.5, dash: dashed ? [2.5, 2] : []))
                .frame(width: 12, height: 12)
            Text(label)
        }
    }

    // MARK: History

    private var historyCard: some View {
        StandardCard(padding: 20) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Your recent cycles").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
                if let range = stats.typicalRange {
                    Text(range.lowerBound == range.upperBound
                         ? "About \(range.lowerBound) days between periods."
                         : "Lately your cycles have run \(range.lowerBound) to \(range.upperBound) days.")
                        .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8))
                        .fixedSize(horizontal: false, vertical: true)
                }
                FlowLayout(spacing: 8) {
                    ForEach(Array(stats.recentLengths.reversed().enumerated()), id: \.offset) { _, len in
                        Text("\(len) days")
                            .font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.accent)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(theme.accent.opacity(0.1)).clipShape(Capsule())
                    }
                }
            }
        }
    }

    // MARK: Phase

    private var phaseCard: some View {
        let phase = env.cycle.estimatedPhase(on: .now)
        return VStack(alignment: .leading, spacing: 10) {
            Text("Estimated phase").font(KeelFont.caption).foregroundStyle(theme.muted)
            Text(phase == .unknown ? "Learning your rhythm" : phase.label)
                .font(KeelFont.serif(22, weight: .semibold)).foregroundStyle(theme.heading)
            Text(phase == .unknown
                 ? "Log a couple of cycles and Keel can estimate roughly where you are. In perimenopause cycles are often irregular, so this stays a gentle guide, never a prediction."
                 : "In perimenopause cycles can be irregular, so this is a gentle estimate from your own pattern, not a prediction.")
                .font(KeelFont.caption).foregroundStyle(theme.text.opacity(0.7)).lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20).frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.sageTint)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.sageBorder, lineWidth: 1))
    }

    // MARK: Insight (real, from PatternEngine)

    private func insightCard(_ finding: PatternFinding) -> some View {
        StandardCard(padding: 20) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: finding.icon).font(.system(size: 22)).foregroundStyle(theme.accent)
                    .frame(width: 44, height: 44)
                    .background(theme.accent.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 6) {
                    Text(finding.title).font(KeelFont.serif(17, weight: .semibold)).foregroundStyle(theme.heading)
                    Text(finding.detail).font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8)).lineSpacing(2)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(finding.timeframe).font(KeelFont.caption).italic().foregroundStyle(theme.muted)
                }
            }
        }
    }

    // MARK: Log past days (the demoted month grid)

    private var logPastCard: some View {
        StandardCard(padding: 20) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Button { shiftMonth(-1) } label: {
                        Image(systemName: "chevron.left").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.muted)
                            .frame(width: 40, height: 40)
                    }.buttonStyle(.plain)
                    Spacer()
                    Text(month.formatted(.dateTime.month(.wide).year()))
                        .font(KeelFont.serif(17, weight: .semibold)).foregroundStyle(theme.heading)
                    Spacer()
                    Button { shiftMonth(1) } label: {
                        Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.muted)
                            .frame(width: 40, height: 40)
                    }.buttonStyle(.plain).disabled(isCurrentOrFutureMonth)
                     .opacity(isCurrentOrFutureMonth ? 0.35 : 1)
                }
                Text("Tap a day to log or edit its flow.").font(KeelFont.caption).foregroundStyle(theme.muted)
                calendarGrid
            }
        }
    }

    private var calendarGrid: some View {
        let cols = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
        return LazyVGrid(columns: cols, spacing: 4) {
            ForEach(Array(weekdayLetters.enumerated()), id: \.offset) { _, l in
                Text(l).font(KeelFont.sans(12, weight: .medium)).foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity).padding(.vertical, 4)
            }
            ForEach(Array(monthCells.enumerated()), id: \.offset) { _, day in gridCell(day) }
        }
    }

    @ViewBuilder
    private func gridCell(_ date: Date?) -> some View {
        if let date {
            let mark = marks[date.startOfDay]
            let isToday = date.isSameDay(as: .now)
            let isFuture = date.startOfDay > today
            Button { Haptics.selection(); editing = date.startOfDay } label: {
                Text("\(cal.component(.day, from: date))")
                    .font(KeelFont.body)
                    .foregroundStyle(mark.map { fillOpacity($0) >= 0.7 ? theme.onFill : theme.text } ?? (isFuture ? theme.muted.opacity(0.5) : theme.text))
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .background(mark.map { theme.accent.opacity(fillOpacity($0)) } ?? .clear)
                    .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .stroke(gridStroke(mark: mark, isToday: isToday), lineWidth: mark?.fromAppleHealth == true ? 1.5 : 2))
            }
            .buttonStyle(.plain)
            .disabled(isFuture)
            .accessibilityLabel(accessibilityLabel(day: date.startOfDay, mark: mark, inEstimate: false))
        } else {
            Color.clear.frame(minHeight: 40)
        }
    }

    private func gridStroke(mark: CycleDayMark?, isToday: Bool) -> Color {
        if mark?.fromAppleHealth == true { return theme.accent }
        return isToday && mark == nil ? theme.accent : .clear
    }

    // MARK: Derived values

    private var visibleRange: (lower: Date, upper: Date) {
        (min(railStart, monthStart).adding(days: -1), max(railEnd, monthEnd))
    }

    /// What the marks depend on: her edits (`refresh`), the month shown, and the
    /// imported rows (day and level).
    private struct MarksKey: Equatable {
        let refresh: Int
        let month: Date
        let imported: [String]
    }
    private var marksKey: MarksKey {
        MarksKey(refresh: refresh, month: monthStart,
                 imported: importedRows.map { "\($0.date.timeIntervalSince1970):\($0.flowRaw)" }.sorted())
    }

    /// Period days across the timeline and the shown month: hers, plus Apple Health's
    /// where she hasn't logged that day (shown once, hers winning).
    private func computeMarks() -> [Date: CycleDayMark] {
        let range = visibleRange
        let manual = env.cycle.entries(from: range.lower, to: range.upper)
            .filter { $0.type != .periodEnd }
            .reduce(into: [Date: FlowLevel]()) { $0[$1.date.startOfDay] = $1.flowLevel }
        let imported = importedRows
            .filter { $0.date >= range.lower.startOfDay && $0.date < range.upper.startOfDay.adding(days: 1) }
            .reduce(into: [Date: FlowLevel]()) { $0[$1.date.startOfDay] = $1.flowLevel }
        return CycleDayMark.merge(manual: manual, imported: imported)
    }

    private var hasAnyImported: Bool { marks.values.contains(where: \.fromAppleHealth) }


    private var estimateWindow: ClosedRange<Date>? { stats.estimatedWindow() }

    private var railBounds: (start: Date, end: Date) {
        Self.railBounds(lastStart: stats.lastStart, estimateEnd: estimateWindow?.upperBound,
                        today: today, calendar: cal)
    }
    private var railStart: Date { railBounds.start }
    private var railEnd: Date { railBounds.end }
    private var railDays: [Date] {
        var days: [Date] = []; var d = railBounds.start
        while d <= railBounds.end { days.append(d); d = d.adding(days: 1) }
        return days
    }

    /// The timeline's day window. With data it runs from the current cycle's start to
    /// today (or the estimated next period). With none, it's a recent week ending
    /// today, so the empty top pane still shows real, tappable days to log the first
    /// period into. Pure, so the boundaries can be tested.
    static func railBounds(lastStart: Date?, estimateEnd: Date?, today: Date, calendar: Calendar) -> (start: Date, end: Date) {
        let startOfToday = calendar.startOfDay(for: today)
        guard let lastStart else {
            let start = calendar.date(byAdding: .day, value: -6, to: startOfToday) ?? startOfToday
            return (start, startOfToday)
        }
        let defaultEnd = calendar.date(byAdding: .day, value: 3, to: startOfToday) ?? startOfToday
        let maxEnd = max(startOfToday, estimateEnd ?? defaultEnd)
        let end = calendar.date(byAdding: .day, value: 1, to: maxEnd) ?? maxEnd
        return (calendar.startOfDay(for: lastStart), end)
    }

    private var statusLine: String {
        guard let dayN = stats.cycleDay(on: today) else { return "Log a period to start your cycle." }
        var line = "Day \(dayN) of your cycle."
        if let window = estimateWindow {
            let f = Date.FormatStyle.dateTime.month(.abbreviated).day()
            line += " Next period likely \(window.lowerBound.formatted(f)) to \(window.upperBound.formatted(f))."
        } else if stats.typicalRange != nil {
            line += " Your recent cycles have varied, so no estimate yet."
        }
        return line
    }

    // MARK: Month grid helpers

    private var monthStart: Date { cal.dateInterval(of: .month, for: month)?.start ?? month }
    private var monthEnd: Date { cal.dateInterval(of: .month, for: month)?.end ?? month }
    private var isCurrentOrFutureMonth: Bool {
        cal.compare(month, to: .now, toGranularity: .month) != .orderedAscending
    }
    private func shiftMonth(_ delta: Int) {
        guard let m = cal.date(byAdding: .month, value: delta, to: month) else { return }
        if delta > 0 && cal.compare(m, to: .now, toGranularity: .month) == .orderedDescending { return }
        month = m
    }

    private var weekdayLetters: [String] {
        let s = cal.veryShortStandaloneWeekdaySymbols
        let shift = cal.firstWeekday - 1
        return Array(s[shift...] + s[..<shift])
    }
    private func weekdayInitial(_ date: Date) -> String {
        let w = cal.component(.weekday, from: date)
        return cal.veryShortStandaloneWeekdaySymbols[w - 1]
    }
    private var monthCells: [Date?] {
        guard let interval = cal.dateInterval(of: .month, for: month),
              let firstWeekday = cal.dateComponents([.weekday], from: interval.start).weekday else { return [] }
        let leading = (firstWeekday - cal.firstWeekday + 7) % 7
        let days = cal.range(of: .day, in: .month, for: month)?.count ?? 30
        var cells: [Date?] = Array(repeating: nil, count: leading)
        for d in 0..<days { cells.append(cal.date(byAdding: .day, value: d, to: interval.start)) }
        return cells
    }

    private func accessibilityLabel(day: Date, mark: CycleDayMark?, inEstimate: Bool) -> String {
        let d = day.formatted(.dateTime.weekday(.wide).month(.wide).day())
        if let mark { return "\(d), \(mark.level.label)\(mark.fromAppleHealth ? ", from Apple Health" : "")" }
        if day == today { return "\(d), today" }
        if inEstimate { return "\(d), estimated period" }
        return d
    }

    // Identifiable wrapper so `.sheet(item:)` can present a tapped day.
    private struct DayID: Identifiable { let date: Date; var id: Date { date } }
    private var editingBinding: Binding<DayID?> {
        Binding(get: { editing.map(DayID.init) }, set: { editing = $0?.date })
    }
}

/// The tap-to-log sheet: pick a flow level, or mark the day as not a period day.
private struct CycleDaySheet: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    let date: Date
    let current: FlowLevel?
    /// Apple Health's level for this day, if it has one (shown for reference).
    let fromAppleHealth: FlowLevel?
    let onSave: (FlowLevel?) -> Void

    var body: some View {
        // Scrolls rather than clips at large text sizes.
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(date.formatted(.dateTime.weekday(.wide).month(.wide).day()))
                        .font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                    Text("How was your flow?").font(KeelFont.sans(13)).foregroundStyle(theme.muted)
                    if let imported = fromAppleHealth, current == nil {
                        Text((imported == .unspecified
                              ? "Apple Health has this as a period day."
                              : "Apple Health has this as a period day, \(imported.label.lowercased()) flow.")
                             + " Choose a level to log it in Keel too.")
                            .font(KeelFont.caption).foregroundStyle(theme.muted)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 4)
                    }
                }

                FlowLayout(spacing: 10) {
                    ForEach(FlowLevel.loggingOrder) { level in
                        chip(level.label, selected: current == level, tint: theme.accent) {
                            pick(level)
                        }
                    }
                }

                // Keel can't change Apple Health, so a day only Apple Health has offers
                // logging a level, not "Not a period day".
                if current != nil || fromAppleHealth == nil {
                    Button { pick(nil) } label: {
                        HStack(spacing: 6) {
                            Image(systemName: current == nil ? "checkmark.circle" : "xmark.circle")
                            Text(current == nil ? "Not a period day" : "Remove this period day")
                        }
                        .font(KeelFont.body).foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(22)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(theme.background.ignoresSafeArea())
    }

    private func pick(_ level: FlowLevel?) {
        Haptics.selection(); onSave(level); dismiss()
    }

    private func chip(_ title: String, selected: Bool, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(KeelFont.body)
                .foregroundStyle(selected ? theme.onFill : theme.text.opacity(0.85))
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(selected ? tint : theme.card)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(selected ? .clear : theme.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
