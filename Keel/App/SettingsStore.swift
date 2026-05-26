import SwiftUI

enum ColourMode: String, CaseIterable, Identifiable {
    case light, dark, system
    var id: String { rawValue }

    var label: String {
        switch self {
        case .light: "Light"
        case .dark: "Dark"
        case .system: "System"
        }
    }

    var symbol: String {
        switch self {
        case .light: "sun.max.fill"
        case .dark: "moon.fill"
        case .system: "gearshape.fill"
        }
    }

    var detail: String {
        switch self {
        case .light: "Warm and bright, all day."
        case .dark: "Easy on the eyes at night."
        case .system: "Follow your device setting."
        }
    }

    var preferredColorScheme: ColorScheme? {
        switch self {
        case .light: .light
        case .dark: .dark
        case .system: nil
        }
    }
}

/// User preferences persisted to `UserDefaults`. `@Observable`, so changing a
/// value (e.g. colour mode or theme) re-renders `ThemedRoot` and re-injects the
/// resolved theme across the whole app.
@MainActor
@Observable
final class SettingsStore {
    private let defaults = UserDefaults.standard

    var colourMode: ColourMode { didSet { defaults.set(colourMode.rawValue, forKey: "keel.colourMode") } }
    var themeID: String { didSet { defaults.set(themeID, forKey: "keel.themeID") } }
    var moodPackID: String { didSet { defaults.set(moodPackID, forKey: "keel.moodPackID") } }

    var pushNotifications: Bool { didSet { defaults.set(pushNotifications, forKey: "keel.pushNotifications") } }
    var haptics: Bool { didSet { defaults.set(haptics, forKey: "keel.haptics"); Haptics.userEnabled = haptics } }
    var analytics: Bool { didSet { defaults.set(analytics, forKey: "keel.analytics") } }
    var icloudBackup: Bool { didSet { defaults.set(icloudBackup, forKey: "keel.icloudBackup") } }
    var autoBackup: Bool { didSet { defaults.set(autoBackup, forKey: "keel.autoBackup") } }

    var ownedThemeIDs: Set<String> { didSet { defaults.set(Array(ownedThemeIDs), forKey: "keel.ownedThemes") } }
    var ownedPackIDs: Set<String> { didSet { defaults.set(Array(ownedPackIDs), forKey: "keel.ownedPacks") } }
    var enabledReminderIDs: Set<String> { didSet { defaults.set(Array(enabledReminderIDs), forKey: "keel.reminders") } }
    var reminderConfig: ReminderConfig {
        didSet {
            if let data = try? JSONEncoder().encode(reminderConfig) { defaults.set(data, forKey: "keel.reminderConfig") }
        }
    }

    /// She has asked to see the intimacy and bladder group. Off until she does,
    /// so nothing personal appears in the picker uninvited.
    var showsSensitiveSymptoms: Bool { didSet { defaults.set(showsSensitiveSymptoms, forKey: "keel.sensitiveSymptoms") } }

    init() {
        colourMode = ColourMode(rawValue: defaults.string(forKey: "keel.colourMode") ?? "") ?? .system
        themeID = defaults.string(forKey: "keel.themeID") ?? ThemeCatalog.defaultID
        moodPackID = defaults.string(forKey: "keel.moodPackID") ?? MoodPacks.defaultID
        pushNotifications = defaults.object(forKey: "keel.pushNotifications") as? Bool ?? true
        haptics = defaults.object(forKey: "keel.haptics") as? Bool ?? true
        analytics = defaults.object(forKey: "keel.analytics") as? Bool ?? false
        icloudBackup = defaults.object(forKey: "keel.icloudBackup") as? Bool ?? true
        autoBackup = defaults.object(forKey: "keel.autoBackup") as? Bool ?? true

        let defaultThemes = Set(ThemeCatalog.all.filter(\.ownedByDefault).map(\.id))
        ownedThemeIDs = Set(defaults.stringArray(forKey: "keel.ownedThemes") ?? []).union(defaultThemes)
        let defaultPacks = Set(MoodPacks.all.filter(\.ownedByDefault).map(\.id))
        ownedPackIDs = Set(defaults.stringArray(forKey: "keel.ownedPacks") ?? []).union(defaultPacks)
        enabledReminderIDs = Set(defaults.stringArray(forKey: "keel.reminders") ?? ["dailyCheckIn", "medication"])
        reminderConfig = (defaults.data(forKey: "keel.reminderConfig")
            .flatMap { try? JSONDecoder().decode(ReminderConfig.self, from: $0) }) ?? ReminderConfig()
        showsSensitiveSymptoms = defaults.object(forKey: "keel.sensitiveSymptoms") as? Bool ?? false

        Haptics.userEnabled = haptics // all stored properties are set by here
    }

    // MARK: Derived

    func isDark(systemDark: Bool) -> Bool {
        switch colourMode {
        case .light: false
        case .dark: true
        case .system: systemDark
        }
    }

    var activePack: MoodPack { MoodPacks.pack(moodPackID) }

    func emoji(for mood: Mood) -> String { activePack.emoji(for: mood) }

    // Themes and mood packs are free for now (real monetisation is deferred), so
    // everything is available. Kept as methods so gating can return later without
    // touching the call sites.
    func owns(theme id: String) -> Bool { true }
    func owns(pack id: String) -> Bool { true }
}
