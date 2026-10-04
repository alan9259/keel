import SwiftUI
import SwiftData

/// Looking back: a fixed-wording summary of the current month from her own check-ins
/// (`MonthSummary`), plus any single-measure pattern cards. No generated text.
struct PatternsView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(AppEnvironment.self) private var env

    @Query(filter: #Predicate<Insight> { $0.deletedAt == nil }, sort: \Insight.generatedAt, order: .reverse)
    private var insights: [Insight]
    /// Observed so the summary rebuilds when she adds or edits a check-in.
    @Query(filter: #Predicate<CheckIn> { $0.deletedAt == nil })
    private var checkIns: [CheckIn]

    private var summary: MonthSummary {
        _ = checkIns.count
        return MonthSummary.current(context: env.context)
    }

    private var hasAnyNotes: Bool {
        checkIns.contains { !($0.notes ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    var body: some View {
        let summary = summary
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                ScreenHeader(title: "Looking back") { dismiss() }

                monthCard(summary)
                notesSection(summary)

                if !insights.isEmpty {
                    VStack(spacing: 16) {
                        ForEach(insights) { InsightCard(insight: $0) }
                    }
                }

                gpSummaryButton
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    // MARK: This month

    private func monthCard(_ summary: MonthSummary) -> some View {
        HeroCard {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(summary.heading(calendar: .current).uppercased())
                        .font(KeelFont.eyebrow).tracking(1).foregroundStyle(theme.muted)
                    Text(summary.checkInLine)
                        .font(KeelFont.serif(19, weight: .semibold)).foregroundStyle(theme.heading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if summary.daysCheckedIn > 0 {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Symptoms you logged most often")
                            .font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.text)
                        if summary.topSymptoms.isEmpty {
                            Text("No symptoms logged this month.")
                                .font(KeelFont.body).foregroundStyle(theme.muted)
                        } else {
                            ForEach(summary.topSymptoms, id: \.name) { item in
                                HStack {
                                    Text(item.name).font(KeelFont.body).foregroundStyle(theme.text)
                                    Spacer()
                                    Text(MonthSummary.days(item.days))
                                        .font(KeelFont.body).monospacedDigit().foregroundStyle(theme.muted)
                                }
                                .accessibilityElement(children: .combine)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: What you wrote

    private func notesSection(_ summary: MonthSummary) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("What you wrote")
                .font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            if summary.notes.isEmpty {
                Text("Nothing written this month.")
                    .font(KeelFont.body).foregroundStyle(theme.muted)
            } else {
                ForEach(summary.notes.prefix(MonthSummary.notePreviewLimit)) { NoteCard(note: $0, lineLimit: 4) }
            }
            if hasAnyNotes {
                NavigationLink(value: MainRoute.notes) {
                    HStack {
                        Text("See all notes").font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.accent)
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(theme.accent)
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: GP Visit Summary (always shown)

    private var gpSummaryButton: some View {
        NavigationLink(value: MainRoute.gpSummary) {
            HStack(spacing: 14) {
                Image(systemName: "doc.text")
                    .font(.system(size: 20)).foregroundStyle(theme.accent)
                    .frame(width: 40, height: 40)
                    .background(theme.accentTint)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Seeing your GP soon?")
                        .font(KeelFont.sans(16, weight: .medium)).foregroundStyle(theme.text)
                    Text("Turn this into a GP Visit Summary")
                        .font(KeelFont.caption).foregroundStyle(theme.muted)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(theme.muted)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// One of her notes with its date. Shared by Looking back and All notes.
struct NoteCard: View {
    @Environment(\.keelTheme) private var theme
    let note: MonthSummary.Note
    var lineLimit: Int? = nil

    /// The year shows only when it isn't this year (All notes covers every year).
    private var dateText: String {
        let sameYear = Calendar.current.isDate(note.date, equalTo: .now, toGranularity: .year)
        let style = Date.FormatStyle.dateTime.weekday(.abbreviated).day().month(.abbreviated)
        return note.date.formatted(sameYear ? style : style.year())
    }

    var body: some View {
        StandardCard(padding: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text(dateText)
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                Text(note.text)
                    .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.85)).lineSpacing(2)
                    .lineLimit(lineLimit)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Every note she has written, newest first ("See all notes").
struct AllNotesView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(AppEnvironment.self) private var env
    @Query(filter: #Predicate<CheckIn> { $0.deletedAt == nil && $0.notes != nil })
    private var checkIns: [CheckIn]

    var body: some View {
        let notes = MonthSummary.notes(fromCheckIns: checkIns)
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ScreenHeader(title: "Your notes") { dismiss() }
                if notes.isEmpty {
                    Text("Nothing written yet.").font(KeelFont.body).foregroundStyle(theme.muted)
                } else {
                    ForEach(notes) { NoteCard(note: $0) }
                }
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }
}

private struct InsightCard: View {
    @Environment(\.keelTheme) private var theme
    let insight: Insight

    private var accent: Color {
        switch insight.accent {
        case .terracotta: theme.accent
        case .sage: theme.sage
        case .warmGrey: theme.heading
        }
    }

    var body: some View {
        StandardCard(padding: 20) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: insight.iconKey)
                    .font(.system(size: 24)).foregroundStyle(accent)
                    .frame(width: 48, height: 48)
                    .background(accent.opacity(0.10))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 8) {
                    Text(insight.title).font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
                    Text(insight.detail).font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8)).lineSpacing(2)
                    Text(insight.timeframe).font(KeelFont.caption).italic().foregroundStyle(theme.muted)
                }
            }
        }
    }
}
