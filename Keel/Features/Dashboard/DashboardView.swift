import SwiftUI
import SwiftData

struct DashboardView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme

    let onNavigate: (MainRoute) -> Void
    /// Start a new entry for the given day (opens the mood slide, then detail).
    let onCreateEntry: (Date) -> Void
    /// Open an existing entry for editing.
    let onEditEntry: (CheckIn) -> Void

    @Query(filter: #Predicate<CheckIn> { $0.deletedAt == nil }, sort: \CheckIn.date, order: .reverse)
    private var checkIns: [CheckIn]
    @Query(filter: #Predicate<Medication> { $0.deletedAt == nil && $0.isActive == true }, sort: \Medication.createdAt)
    private var meds: [Medication]
    @Query(filter: #Predicate<MedicationLog> { $0.deletedAt == nil })
    private var medLogs: [MedicationLog]
    @Query(filter: #Predicate<UserProfile> { $0.deletedAt == nil })
    private var profiles: [UserProfile]
    @Query private var activityLogs: [ActivityLog]

    @State private var selDate: Date = {
        #if DEBUG
        if let d = DebugHarness.startSelDate { return d }
        #endif
        return Date.now.startOfDay
    }()
    @State private var calMonth = Date.now

    private let cal = Calendar.current

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            theme.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 22) {
                    topBar
                    snapshot
                    medicinesCard
                    trends
                    calendarCard
                    Spacer().frame(height: 90)
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
            }

            fabRow
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Top bar

    private var topBar: some View {
        HStack(alignment: .top) {
            HStack(spacing: 2) {
                iconButton("chevron.left") { shiftDay(-1) }
                Button { selDate = Date.now.startOfDay } label: {
                    VStack(spacing: 2) {
                        Text(selDateLabel).font(KeelFont.sans(14, weight: .medium)).foregroundStyle(theme.text)
                        if !selIsToday {
                            Text("Tap to return").font(KeelFont.sans(12)).foregroundStyle(theme.accent)
                        }
                    }
                    .frame(minWidth: 116)
                }
                iconButton("chevron.right", disabled: selIsToday) { shiftDay(1) }
            }
            Spacer(minLength: 12)
            Text(greeting)
                .font(KeelFont.serif(22, weight: .semibold))
                .foregroundStyle(theme.heading)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .padding(.top, 4)
        }
    }

    // MARK: Snapshot

    @ViewBuilder
    private var snapshot: some View {
        let dayEntries = entries(for: selDate)
        StandardCard {
            if dayEntries.isEmpty {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(selIsToday ? "No entry yet today" : "Nothing logged")
                            .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.7))
                        Text(selIsToday ? "How are you feeling?" : selDateLabel)
                            .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                    }
                    Spacer()
                    Button { onCreateEntry(selDate) } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(theme.background)
                            .frame(width: 40, height: 40)
                            .background(theme.accent).clipShape(Circle())
                    }
                    .accessibilityLabel(selIsToday ? "Quick check-in" : "Add an entry for this day")
                }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(selDateLabel.uppercased())
                            .font(KeelFont.eyebrow).tracking(0.8).foregroundStyle(theme.muted)
                        Spacer()
                        if sleepFraction(for: selDate) > 0 {
                            Text("Sleep \(sleepRingTitle(for: selDate))")
                                .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                        }
                    }
                    ForEach(dayEntries) { entryCard($0) }
                    Button { Haptics.selection(); onCreateEntry(selDate) } label: {
                        Label(selIsToday ? "Add a check-in" : "Add an entry", systemImage: "plus")
                            .font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.accent)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(RoundedRectangle(cornerRadius: Radius.input, style: .continuous)
                                .stroke(theme.accent.opacity(0.4), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// One check-in within a day. Tap to edit that specific entry.
    private func entryCard(_ entry: CheckIn) -> some View {
        Button { Haptics.selection(); onEditEntry(entry) } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    EmojiGlyph(emoji: env.settings.emoji(for: entry.mood), size: 28)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(entry.mood.label).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                        Text(entry.date.formatted(.dateTime.hour().minute()))
                            .font(KeelFont.caption).foregroundStyle(theme.muted)
                    }
                    Spacer()
                    Text("\(entry.energy)%").font(KeelFont.sans(13, weight: .semibold)).foregroundStyle(theme.accent)
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(theme.muted)
                }
                if !loggedSymptoms(entry).isEmpty {
                    FlowLayout(spacing: 6) {
                        ForEach(loggedSymptoms(entry), id: \.id) { item in
                            let sev = SymptomSeverity(rawValue: item.severity)
                            Text(item.name).font(KeelFont.sans(12))
                                .foregroundStyle(sev != nil ? .white : theme.text.opacity(0.7))
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .background(sev?.color ?? theme.track).clipShape(Capsule())
                        }
                    }
                }
                if let note = entry.notes?.nilIfEmpty {
                    Text(note).font(KeelFont.body).foregroundStyle(theme.text.opacity(0.85))
                        .frame(maxWidth: .infinity, alignment: .leading).fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.inputBackground)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(theme.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    // MARK: Trends

    private var trends: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Last 7 days").font(KeelFont.eyebrow).tracking(0.8).foregroundStyle(theme.muted)

            // Mood strip
            StandardCard(padding: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Mood").font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                    HStack(alignment: .bottom, spacing: 4) {
                        ForEach(last7, id: \.self) { day in
                            VStack(spacing: 6) {
                                if let e = entry(for: day) {
                                    EmojiGlyph(emoji: env.settings.emoji(for: e.mood), size: 26)
                                } else {
                                    Circle().fill(theme.track).frame(width: 26, height: 26)
                                }
                                Text(letter(day)).font(KeelFont.sans(11)).foregroundStyle(theme.muted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
            }

            // Energy (0–100%)
            barTrendCard(
                title: "Energy",
                trailing: energyAvg > 0 ? "avg \(energyAvg)%" : "No data yet",
                series: energyBars, color: theme.accent, maxValue: 100
            )

            // Sleep (hours; ~10h fills the slot)
            barTrendCard(
                title: "Sleep",
                trailing: sleepAvgHours > 0 ? "score \(sleepScore)" : "No data yet",
                series: sleepBars, color: theme.sage, maxValue: 10
            )

        }
    }

    // MARK: Medicines (sits just below the day's entry)

    /// The day's medicines log. Every medicine she's tracking (ticked on the
    /// Medications screen) shows here; tapping a row records whether she took it
    /// on the selected day. The card hides when she isn't tracking anything.
    @ViewBuilder
    private var medicinesCard: some View {
        let tracked = meds.filter { $0.isTracked }
        if !tracked.isEmpty {
            StandardCard(padding: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Medicines").font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                        Spacer()
                        Text(selDateLabel).font(KeelFont.sans(11)).foregroundStyle(theme.muted)
                    }
                    ForEach(tracked) { med in
                        let taken = tookSet.contains(Key(med: med.id, day: selDate))
                        Button { toggleTaken(med, taken: taken) } label: {
                            HStack(spacing: 10) {
                                Text(med.name).font(KeelFont.sans(13))
                                    .foregroundStyle(theme.text.opacity(taken ? 0.85 : 0.55))
                                    .lineLimit(1)
                                Spacer(minLength: 8)
                                ZStack {
                                    Circle().fill(taken ? theme.sage : .clear).frame(width: 22, height: 22)
                                    Circle().strokeBorder(taken ? .clear : theme.border, lineWidth: 2)
                                        .frame(width: 22, height: 22)
                                    if taken {
                                        Image(systemName: "checkmark").font(.system(size: 11, weight: .bold))
                                            .foregroundStyle(.white)
                                    }
                                }
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(med.name)
                        .accessibilityValue(taken ? "Taken" : "Not taken")
                        .accessibilityHint("Records whether you took it on \(selDateLabel)")
                    }
                    Divider().background(theme.border).padding(.top, 2)
                    HStack(spacing: 8) {
                        ProgressCapsule(fraction: adherenceFraction, color: theme.sage)
                            .frame(height: 6)
                        Text("\(Int(adherenceFraction * 100))% this week")
                            .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                    }
                }
            }
            .id(selDate)
        }
    }

    /// A 7-day bar strip. Every day keeps a faint placeholder track (like the mood
    /// strip's empty circles) so real bars sit in their correct slot instead of
    /// floating; a day with no data shows just the empty track (never a fake value).
    private func barTrendCard(title: String, trailing: String,
                              series: [(day: Date, value: Double?)],
                              color: Color, maxValue: Double) -> some View {
        let barHeight: CGFloat = 72
        let barWidth: CGFloat = 30
        return StandardCard(padding: 16) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline) {
                    Text(title).font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                    Spacer()
                    Text(trailing).font(KeelFont.sans(12)).foregroundStyle(theme.text.opacity(0.6))
                }
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(series, id: \.day) { point in
                        VStack(spacing: 6) {
                            ZStack(alignment: .bottom) {
                                RoundedRectangle(cornerRadius: 5, style: .continuous)
                                    .fill(theme.track)
                                    .frame(width: barWidth, height: barHeight)
                                if let v = point.value, v > 0 {
                                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                                        .fill(color.gradient)
                                        .frame(width: barWidth, height: max(barHeight * CGFloat(min(v / maxValue, 1)), 5))
                                }
                            }
                            Text(letter(point.day)).font(KeelFont.sans(11)).foregroundStyle(theme.muted)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }

    // MARK: Calendar

    private var calendarCard: some View {
        StandardCard {
            VStack(spacing: 14) {
                HStack {
                    iconButton("chevron.left", size: 16) { calMonth = cal.date(byAdding: .month, value: -1, to: calMonth) ?? calMonth }
                    Text(calMonth.formatted(.dateTime.month(.wide).year()))
                        .font(KeelFont.serif(16, weight: .medium)).foregroundStyle(theme.heading)
                        .frame(maxWidth: .infinity)
                    iconButton("chevron.right", size: 16, disabled: isCurrentMonth) {
                        calMonth = cal.date(byAdding: .month, value: 1, to: calMonth) ?? calMonth
                    }
                }
                let cols = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
                LazyVGrid(columns: cols, spacing: 4) {
                    ForEach(Array(weekdayLetters.enumerated()), id: \.offset) { _, l in
                        Text(l).font(KeelFont.sans(11)).foregroundStyle(theme.muted).frame(maxWidth: .infinity)
                    }
                    ForEach(Array(monthCells.enumerated()), id: \.offset) { _, day in
                        calendarCell(day)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func calendarCell(_ date: Date?) -> some View {
        if let date {
            let sel = date.isSameDay(as: selDate)
            // No check-ins for days that haven't happened yet: future cells are
            // dimmed and not selectable, so the day selector can never land on the
            // future (and the "+" only ever adds for the selected day).
            let isFuture = date.startOfDay > Date.now.startOfDay
            let e = entry(for: date)
            Button {
                selDate = date.startOfDay
                Haptics.selection()
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(sel ? theme.accent.opacity(0.12) : .clear)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(sel ? theme.accent : .clear, lineWidth: 1.5)
                        )
                    if let e {
                        EmojiGlyph(emoji: env.settings.emoji(for: e.mood), size: 22)
                    } else {
                        Text("\(cal.component(.day, from: date))")
                            .font(KeelFont.sans(13))
                            .foregroundStyle(theme.text.opacity(isFuture ? 0.25 : 0.75))
                    }
                }
                .frame(height: 38)
            }
            .buttonStyle(.plain)
            .disabled(isFuture)
            .accessibilityHint(isFuture ? "Future date, not available" : "")
        } else {
            Color.clear.frame(height: 38)
        }
    }

    // MARK: FAB row

    private var fabRow: some View {
        HStack(spacing: 10) {
            miniFab("bubble.left.and.bubble.right.fill", tint: theme.plum) { onNavigate(.chat) }
            miniFab("pills.fill", tint: theme.sage) { onNavigate(.medications) }
            miniFab("chart.line.uptrend.xyaxis", tint: theme.heading) { onNavigate(.patterns) }
            miniFab("ellipsis", tint: theme.muted) { onNavigate(.more) }
            // Always adds a new check-in for the selected day (multiple per day are
            // allowed); existing entries are edited by tapping their card above.
            Button {
                onCreateEntry(selDate)
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(theme.background)
                    .frame(width: 56, height: 56)
                    .background(theme.accent).clipShape(Circle())
                    .shadow(color: theme.accent.opacity(0.45), radius: 11, y: 8)
            }
            .accessibilityLabel("New check-in")
        }
        .padding(.trailing, 18)
        .padding(.bottom, 30)
    }

    private func miniFab(_ icon: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundStyle(tint)
                .frame(width: 46, height: 46)
                .background(theme.card).clipShape(Circle())
                .overlay(Circle().stroke(theme.border, lineWidth: 2))
                .shadow(color: theme.text.opacity(0.14), radius: 8, y: 6)
        }
    }

    // MARK: Small components

    private func iconButton(_ icon: String, size: CGFloat = 20, disabled: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: size, weight: .medium))
                .foregroundStyle(disabled ? theme.muted.opacity(0.3) : theme.text)
                .frame(width: 36, height: 36)
        }
        .disabled(disabled)
    }

    // MARK: Derived data

    private struct Key: Hashable { let med: UUID; let day: Date }

    private var tookSet: Set<Key> {
        Set(medLogs.filter { $0.taken }.compactMap { log in
            log.medication.map { Key(med: $0.id, day: log.date.startOfDay) }
        })
    }

    /// Records whether she took a medicine on the selected day. Taking it writes a
    /// whole-day log; untaking clears every log for it that day.
    private func toggleTaken(_ med: Medication, taken: Bool) {
        env.toggleMedicationFromHome(med, on: selDate, currentlyTaken: taken)
    }

    private var last7: [Date] {
        let today = Date.now.startOfDay
        return (0..<7).map { today.adding(days: -6 + $0) }
    }

    /// Latest check-in for a day (checkIns is newest-first), used for the mood dot.
    private func entry(for date: Date) -> CheckIn? {
        checkIns.first { $0.date.isSameDay(as: date) }
    }

    /// All check-ins for a day, earliest first, for the stacked snapshot list.
    private func entries(for date: Date) -> [CheckIn] {
        checkIns.filter { $0.date.isSameDay(as: date) }.sorted { $0.date < $1.date }
    }

    /// The day's average energy across its check-ins.
    private func avgEnergy(for date: Date) -> Double? {
        let es = entries(for: date)
        guard !es.isEmpty else { return nil }
        return Double(es.reduce(0) { $0 + $1.energy }) / Double(es.count)
    }

    /// Active symptom links for a check-in, with their name + severity.
    private func loggedSymptoms(_ entry: CheckIn) -> [(id: UUID, name: String, severity: Int)] {
        (entry.symptomLinks ?? [])
            .filter { !$0.isTombstoned }
            .compactMap { link in link.symptom.map { (link.id, $0.name, link.severity) } }
    }

    private var energyBars: [(day: Date, value: Double?)] {
        last7.map { day in (day, avgEnergy(for: day)) }
    }

    private var energyAvg: Int {
        let vals = last7.compactMap { avgEnergy(for: $0) }
        guard !vals.isEmpty else { return 0 }
        return Int(vals.reduce(0, +) / Double(vals.count))
    }

    // Sleep (hours logged in Activities, activityID "sleep").
    private let sleepGoalHours: Double = 8

    private func sleepHours(for day: Date) -> Double? {
        let logs = activityLogs.filter { $0.activityID == "sleep" && $0.date.isSameDay(as: day) && $0.amount > 0 }
        guard !logs.isEmpty else { return nil }
        return logs.map(\.amount).reduce(0, +)
    }

    private var sleepBars: [(day: Date, value: Double?)] {
        last7.map { day in (day, sleepHours(for: day)) }
    }

    private var sleepAvgHours: Double {
        let vals = last7.compactMap { sleepHours(for: $0) }
        guard !vals.isEmpty else { return 0 }
        return vals.reduce(0, +) / Double(vals.count)
    }

    /// 0–100 score: average sleep as a share of the nightly goal.
    private var sleepScore: Int {
        min(100, Int((sleepAvgHours / sleepGoalHours * 100).rounded()))
    }

    /// Today's (or the selected day's) sleep, for the ring gauge.
    private func sleepFraction(for day: Date) -> Double {
        guard let h = sleepHours(for: day) else { return 0 }
        return min(1, h / sleepGoalHours)
    }

    private func sleepRingTitle(for day: Date) -> String {
        guard let h = sleepHours(for: day) else { return "–" }
        return "\(min(100, Int((h / sleepGoalHours * 100).rounded())))"
    }

    private var adherenceFraction: Double {
        let tracked = meds.filter { $0.isTracked }
        guard !tracked.isEmpty else { return 0 }
        let total = tracked.count * 7
        let taken = last7.reduce(0) { acc, day in
            acc + tracked.filter { tookSet.contains(Key(med: $0.id, day: day.startOfDay)) }.count
        }
        return total == 0 ? 0 : Double(taken) / Double(total)
    }

    private var greeting: String { Greeting.current() }

    private var selIsToday: Bool { selDate.isSameDay(as: .now) }

    private var selDateLabel: String {
        if selIsToday { return "Today" }
        if selDate.isSameDay(as: Date.now.adding(days: -1)) { return "Yesterday" }
        return selDate.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
    }

    private var weekdayLetters: [String] {
        let s = cal.veryShortStandaloneWeekdaySymbols
        let shift = cal.firstWeekday - 1
        return Array(s[shift...] + s[..<shift])
    }

    private func letter(_ date: Date) -> String {
        cal.veryShortStandaloneWeekdaySymbols[cal.component(.weekday, from: date) - 1]
    }

    private var isCurrentMonth: Bool {
        cal.isDate(calMonth, equalTo: .now, toGranularity: .month)
    }

    private var monthCells: [Date?] {
        guard let interval = cal.dateInterval(of: .month, for: calMonth),
              let firstWeekday = cal.dateComponents([.weekday], from: interval.start).weekday
        else { return [] }
        let leading = (firstWeekday - cal.firstWeekday + 7) % 7
        let days = cal.range(of: .day, in: .month, for: calMonth)?.count ?? 30
        var cells: [Date?] = Array(repeating: nil, count: leading)
        for d in 0..<days { cells.append(cal.date(byAdding: .day, value: d, to: interval.start)) }
        return cells
    }

    private func shiftDay(_ n: Int) {
        let next = selDate.adding(days: n)
        if n > 0 && next > Date.now.startOfDay { return }
        selDate = next.startOfDay
    }
}

/// Circular gauge (ring) for a 0–1 fraction with a value in the centre.
struct RingGauge: View {
    @Environment(\.keelTheme) private var theme
    let fraction: Double
    let title: String
    let caption: String
    let color: Color

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                Circle().stroke(theme.track, lineWidth: 7)
                Circle()
                    .trim(from: 0, to: max(0.001, min(fraction, 1)))
                    .stroke(color, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Text(title).font(KeelFont.sans(15, weight: .semibold)).foregroundStyle(theme.text)
            }
            .frame(width: 62, height: 62)
            Text(caption).font(KeelFont.sans(11)).foregroundStyle(theme.muted)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(caption) \(title)")
    }
}

/// Simple filled progress capsule.
struct ProgressCapsule: View {
    @Environment(\.keelTheme) private var theme
    let fraction: Double
    let color: Color
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(theme.track)
                Capsule().fill(color).frame(width: geo.size.width * min(max(fraction, 0), 1))
            }
        }
    }
}
