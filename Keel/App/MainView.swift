import SwiftUI

enum MainRoute: Hashable {
    case cycle
    case medications
    case patterns
    case more
    case profile
    case colourMode
    case themes
    case moodIcons
    case reminders
    case reports
    case activities
    case appleHealth
    case backup
    case settings
    case appLock
    case connect
    case about
    case support
    case gpSummary
    case notes
    case privacy
}

/// A pending check-in detail screen. Identifiable so `.fullScreenCover(item:)`
/// gives each presentation a fresh view identity (correct initial state).
struct CheckInRequest: Identifiable {
    let id = UUID()
    let mood: Mood
    /// The day this entry belongs to (today, or a past day being added/edited).
    var date: Date = .now
    /// When set, the detail screen edits this entry instead of creating one.
    var editingID: UUID? = nil
}

/// Root navigation for the signed-in app. Dashboard is home; the FAB row pushes
/// feature screens; every check-in entry point opens the mood slide first, which
/// then hands off to the check-in detail screen.
struct MainView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme

    @State private var path = NavigationPath()
    /// The active check-in (detail screen). Non-nil presents the cover; the mood
    /// is always chosen in the slide first.
    @State private var checkInRequest: CheckInRequest?
    @State private var showEntrySheet = false
    /// Mood picked in the slide, handed to the detail screen once the slide has
    /// fully dismissed (avoids a two-modal-in-one-transaction conflict).
    @State private var pendingEntryMood: Mood?
    /// The day the pending entry belongs to, and the entry being edited (if any).
    /// Both survive the "Change mood" round-trip so editing a past day, or
    /// re-picking a mood mid-edit, doesn't lose its target.
    @State private var pendingEntryDate: Date = .now
    @State private var pendingEditingID: UUID?
    /// Set when "Change" is tapped on the detail screen so the slide reopens.
    @State private var reopenSlideAfterClose = false
    @State private var toast: ToastData?
    /// The one-line reminders explanation shown once after onboarding, before iOS asks.
    @State private var showReminderExplainer = false
    /// The Apple Health offer to show on launch, if any (see `checkHealthAccessOnLaunch`).
    @State private var healthOffer: AppEnvironment.HealthOffer = .none

    var body: some View {
        NavigationStack(path: $path) {
            DashboardView(
                onNavigate: { path.append($0) },
                onCreateEntry: { date in
                    // New entry for the given day: mood slide first, then detail.
                    pendingEntryDate = date
                    pendingEditingID = nil
                    showEntrySheet = true
                },
                onEditEntry: { entry in
                    // Editing skips the mood slide: her mood is already set, and
                    // "Change" inside the detail can still reopen it.
                    pendingEntryDate = entry.date
                    pendingEditingID = entry.id
                    checkInRequest = CheckInRequest(mood: entry.mood, date: entry.date, editingID: entry.id)
                }
            )
            .navigationDestination(for: MainRoute.self) { route in
                switch route {
                case .cycle: CycleTrackingView()
                case .medications: MedicationsView()
                case .patterns: PatternsView()
                case .more: MoreView()
                case .profile: ProfileView()
                case .colourMode: ColourModeView()
                case .themes: ThemesView()
                case .moodIcons: MoodIconsView()
                case .reminders: RemindersView()
                case .reports: ReportsView()
                case .activities: ActivitiesView()
                case .appleHealth: AppleHealthSettingsView()
                case .backup: BackupRestoreView()
                case .settings: SettingsView()
                case .appLock: AppLockSettingsView()
                case .connect: ConnectView()
                case .about: AboutView()
                case .support: SupportView()
                case .gpSummary: GPSummaryFlowView()
                case .notes: AllNotesView()
                case .privacy: YourPrivacyView()
                }
            }
        }
        .environment(\.goHome) {
            // The trailing "home" button on every pushed screen: pop the whole
            // stack back to the Dashboard. No-op if already at home.
            guard !path.isEmpty else { return }
            path = NavigationPath()
        }
        .overlay(alignment: .bottom) {
            if let toast { ToastView(data: toast) }
        }
        .fullScreenCover(item: $checkInRequest, onDismiss: {
            if reopenSlideAfterClose { reopenSlideAfterClose = false; showEntrySheet = true }
        }) { request in
            CheckInModal(mood: request.mood, entryDate: request.date, editingID: request.editingID) { saved in
                let wasEditing = request.editingID != nil
                checkInRequest = nil
                if saved {
                    showToast(ToastData(title: wasEditing ? "Entry updated." : "Entry saved.",
                                        subtitle: "Every entry sharpens the picture."))
                    env.requestSync()
                }
            } onChangeMood: {
                // Reopen the slide once the detail cover has fully dismissed.
                reopenSlideAfterClose = true
                checkInRequest = nil
            } onDelete: {
                checkInRequest = nil
                showToast(ToastData(title: "Entry removed.", subtitle: "It's gone from your log."))
                env.requestSync()
            }
        }
        .sheet(isPresented: $showEntrySheet, onDismiss: {
            // The slide is the mood step. Once it's fully dismissed, present the
            // detail screen with the chosen mood.
            if let mood = pendingEntryMood {
                pendingEntryMood = nil
                checkInRequest = CheckInRequest(mood: mood, date: pendingEntryDate, editingID: pendingEditingID)
            }
        }) {
            EntrySheet { mood in
                pendingEntryMood = mood   // nil = "remind me later"
                showEntrySheet = false
            }
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
        .alert("Gentle reminders", isPresented: $showReminderExplainer) {
            Button("Not now", role: .cancel) { env.answerNotificationExplainer(allow: false) }
            Button("Continue") { env.answerNotificationExplainer(allow: true) }
        } message: {
            Text("Keel can remind you to check in and take your medicines. You can change this any time in Settings.")
        }
        .alert(healthOffer == .reconnect ? "Update Apple Health access?" : "Connect Apple Health?",
               isPresented: Binding(get: { healthOffer != .none },
                                    set: { if !$0 { healthOffer = .none } })) {
            let offer = healthOffer
            Button("Not now", role: .cancel) { env.answerHealthOffer(offer) }
            Button(offer == .reconnect ? "Continue" : "Connect") {
                env.answerHealthOffer(offer)
                Task { await env.connectAppleHealth() }
            }
        } message: {
            Text(healthOffer == .reconnect
                 ? "Keel can now also show your periods from Apple Health. Continue to choose what Keel can read. Anything you've already shared keeps syncing either way."
                 : "Keel can read your sleep, activity, heart readings and periods from Apple Health to show alongside your own record. You can change this any time under More, then Apple Health.")
        }
        .task {
            // Once per launch, after Home settles: is Apple Health access still there?
            // Never alongside the reminders explanation (one ask at a time).
            try? await Task.sleep(for: .seconds(1.2))
            guard !env.settings.notificationExplainerPending else { return }
            healthOffer = await env.checkHealthAccessOnLaunch()
        }
        .task(id: env.settings.notificationExplainerPending) {
            // Let Home settle first so the explanation doesn't land mid-transition.
            guard env.settings.notificationExplainerPending else { return }
            try? await Task.sleep(for: .seconds(0.8))
            if env.settings.notificationExplainerPending { showReminderExplainer = true }
        }
        .onAppear {
            #if DEBUG
            if path.isEmpty {
                let stack = DebugHarness.initialRouteStack
                if !stack.isEmpty { stack.forEach { path.append($0) } }
                else if let route = DebugHarness.initialRoute { path.append(route) }
            }
            if DebugHarness.showCheckIn { showEntrySheet = true }
            // Simulate the entry-slide handoff (mood picked → detail screen).
            if DebugHarness.entryHandoff { checkInRequest = CheckInRequest(mood: .good) }
            #endif
        }
    }

    private func showToast(_ data: ToastData) {
        withAnimation { toast = data }
        Task {
            try? await Task.sleep(for: .seconds(2.4))
            withAnimation { toast = nil }
        }
    }
}
