import SwiftUI

@main
struct KeelApp: App {
    @State private var env: AppEnvironment

    init() {
        let container = KeelSchema.makeContainer()
        _env = State(initialValue: AppEnvironment(
            container: container,
            provider: AppEnvironment.makeProvider()
        ))
    }

    var body: some Scene {
        WindowGroup {
            ThemedRoot()
                .environment(env)
                .modelContainer(env.container)
        }
    }
}

/// Applies the chosen colour scheme (forced for Light/Dark, or `nil` to follow
/// the device for System), then lets `ThemedContent` — a genuine descendant —
/// read the resolved scheme. This split matters: a view sits *above* the scheme
/// it sets on its own subtree, so if it read `\.colorScheme` itself the value
/// would be stuck at the default and "System" would never go dark.
///
/// `SettingsStore` is `@Observable`, so changing colour mode or theme re-renders
/// this and updates the whole app live.
private struct ThemedRoot: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ThemedContent()
            .preferredColorScheme(env.settings.colourMode.preferredColorScheme)
            .onChange(of: scenePhase) { _, phase in
                switch phase {
                case .background:
                    // Save-on-exit: back up to iCloud when she leaves, if opted in.
                    env.autoBackupToICloud()
                case .active:
                    // Fill in today's due doses for auto-log medicines on return.
                    env.autoLogTodaysDoses()
                default:
                    break
                }
            }
    }
}

/// Reads the now-resolved colour scheme (system appearance in System mode, or the
/// forced one otherwise) and injects the matching theme around the app.
private struct ThemedContent: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let theme = KeelTheme.resolve(themeID: env.settings.themeID, isDark: scheme == .dark)
        RootView()
            .environment(\.keelTheme, theme)
            .tint(theme.accent)
    }
}
