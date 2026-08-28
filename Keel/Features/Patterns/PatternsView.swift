import SwiftUI
import SwiftData

struct PatternsView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(AppEnvironment.self) private var env

    @Query(filter: #Predicate<Insight> { $0.deletedAt == nil }, sort: \Insight.generatedAt, order: .reverse)
    private var insights: [Insight]
    @Query(filter: #Predicate<CheckIn> { $0.deletedAt == nil })
    private var checkIns: [CheckIn]
    @Query(filter: #Predicate<DailySummary> { $0.deletedAt == nil }, sort: \DailySummary.day, order: .reverse)
    private var summaries: [DailySummary]

    @State private var isRefreshing = false

    /// The current reflection is the most recent one, whatever day it was written:
    /// a reflection is keyed to a change in her patterns, not the calendar, so it
    /// stands until something is different (see `DailySummaryService`).
    private var latest: DailySummary? { summaries.first }

    /// Earlier distinct reflections, most recent first.
    private var past: [DailySummary] { Array(summaries.dropFirst().prefix(14)) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                ScreenHeader(title: "Your Patterns") { dismiss() }

                todaysReflection

                if !insights.isEmpty {
                    VStack(spacing: 16) {
                        ForEach(insights) { InsightCard(insight: $0) }
                    }
                }

                if !past.isEmpty {
                    lookingBack
                }

                InfoNoteCard(lead: "Keep going:",
                             message: "The longer you track, the more nuanced these patterns become. Keel learns what's unique to you.")
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    // MARK: Today's reflection (hero)

    private var todaysReflection: some View {
        HeroCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "sparkles")
                        .font(.system(size: 15)).foregroundStyle(theme.accent)
                    Text("Today's reflection")
                        .font(KeelFont.eyebrow).tracking(1).foregroundStyle(theme.muted)
                    Spacer()
                    Button(action: refresh) {
                        if isRefreshing {
                            ProgressView().controlSize(.small)
                        } else {
                            Image(systemName: "arrow.clockwise").font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(theme.muted)
                        }
                    }
                    .disabled(isRefreshing)
                    .accessibilityLabel("Refresh today's reflection")
                }

                if let summary = latest {
                    Text(summary.text)
                        .font(KeelFont.serif(17, weight: .regular)).foregroundStyle(theme.heading).lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Text(placeholder)
                        .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.7)).lineSpacing(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    /// Distinct days she has checked in on, so the placeholder can be accurate
    /// about how far along she is.
    private var loggedDays: Int {
        Set(checkIns.map { $0.date.startOfDay }).count
    }

    private var placeholder: String {
        DailySummaryService.placeholderReflection(loggedDays: loggedDays)
    }

    private func refresh() {
        guard !isRefreshing else { return }
        isRefreshing = true
        Task {
            await env.dailySummary.regenerate()
            isRefreshing = false
        }
    }

    // MARK: Looking back (history)

    private var lookingBack: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Earlier reflections")
                .font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            ForEach(past) { summary in
                StandardCard(padding: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(summary.day.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated)))
                            .font(KeelFont.caption).foregroundStyle(theme.muted)
                        Text(summary.text)
                            .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.85)).lineSpacing(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
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
