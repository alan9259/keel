import SwiftUI

enum MainRoute: Hashable {
    case cycle
    case medications
    case patterns
    case more
    case chat
    case colourMode
    case themes
    case moodIcons
    case reminders
    case reports
    case activities
    case appleHealth
    case backup
    case settings
    case connect
    case about
    case support
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
                case .chat: ChatView()
                case .colourMode: ColourModeView()
                case .themes: ThemesView()
                case .moodIcons: MoodIconsView()
                case .reminders: RemindersView()
                case .reports: ReportsView()
                case .activities: ActivitiesView()
                case .appleHealth: AppleHealthSettingsView()
                case .backup: BackupRestoreView()
                case .settings: SettingsView()
                case .connect: ConnectView()
                case .about: AboutView()
                case .support: SupportView()
                }
            }
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
        .onAppear {
            #if DEBUG
            if path.isEmpty, let route = DebugHarness.initialRoute { path.append(route) }
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
