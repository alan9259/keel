import SwiftUI

struct CycleTrackingView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var month = Date.now
    @State private var refresh = 0
    private let cal = Calendar.current

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                ScreenHeader(title: "Cycle Tracking") { dismiss() }

                StandardCard(padding: 22) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(month.formatted(.dateTime.month(.wide).year()))
                            .font(KeelFont.serif(24, weight: .semibold)).foregroundStyle(theme.heading)
                        Text("Tap days to log your period")
                            .font(KeelFont.body).foregroundStyle(theme.muted)
                            .padding(.top, 6).padding(.bottom, 20)
                        calendarGrid
                    }
                }

                Text("Cycle Phase").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)

                phaseCard

                InfoNoteCard(lead: "Pattern insight:",
                             message: "Your anxiety symptoms tend to cluster in the week before your period. We'll keep watching this.")
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    private var calendarGrid: some View {
        let cols = Array(repeating: GridItem(.flexible(), spacing: 6), count: 7)
        return LazyVGrid(columns: cols, spacing: 6) {
            ForEach(Array(weekdayLetters.enumerated()), id: \.offset) { _, l in
                Text(l).font(KeelFont.sans(13, weight: .medium)).foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity).padding(.vertical, 6)
            }
            ForEach(Array(monthCells.enumerated()), id: \.offset) { _, day in
                dayCell(day)
            }
        }
    }

    @ViewBuilder
    private func dayCell(_ date: Date?) -> some View {
        if let date {
            let isPeriod = periodDays.contains(date.startOfDay)
            let isToday = date.isSameDay(as: .now)
            Button {
                env.cycle.togglePeriodDay(date); refresh += 1; Haptics.selection()
            } label: {
                Text("\(cal.component(.day, from: date))")
                    .font(KeelFont.body).fontWeight(isPeriod ? .semibold : .regular)
                    .foregroundStyle(isPeriod ? theme.background : theme.text)
                    .frame(maxWidth: .infinity, minHeight: 42)
                    .background(isPeriod ? theme.accent : .clear)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(isToday && !isPeriod ? theme.accent : .clear, lineWidth: 2))
            }
            .buttonStyle(.plain)
        } else {
            Color.clear.frame(minHeight: 42)
        }
    }

    private var phaseCard: some View {
        let phase = env.cycle.estimatedPhase(on: .now)
        return VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Estimated phase").font(KeelFont.caption).foregroundStyle(theme.muted)
                    Text(phase == .unknown ? "Follicular" : phase.label)
                        .font(KeelFont.serif(22, weight: .semibold)).foregroundStyle(theme.heading)
                }
                Spacer()
                Image(systemName: "circle").font(.system(size: 44, weight: .light)).foregroundStyle(theme.sage)
            }
            Text("In perimenopause, cycles can be irregular. Keel adapts to your unique pattern.")
                .font(KeelFont.caption).foregroundStyle(theme.text.opacity(0.7)).lineSpacing(2)
        }
        .padding(22).frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.sageTint)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.sageBorder, lineWidth: 1))
    }

    private var weekdayLetters: [String] {
        let s = cal.veryShortStandaloneWeekdaySymbols
        let shift = cal.firstWeekday - 1
        return Array(s[shift...] + s[..<shift])
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

    private var periodDays: Set<Date> {
        _ = refresh
        guard let interval = cal.dateInterval(of: .month, for: month) else { return [] }
        return Set(env.cycle.entries(from: interval.start, to: interval.end)
            .filter { $0.type != .periodEnd }.map { $0.date.startOfDay })
    }
}
