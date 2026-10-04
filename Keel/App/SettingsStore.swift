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
    /// Apple Health items she has switched OFF (see `HealthSyncCatalog`). Stored as
    /// the disabled set so anything not listed, including items added in a later
    /// version, defaults to on. Empty means everything syncs.
    var disabledHealthItemIDs: Set<String> { didSet { defaults.set(Array(disabledHealthItemIDs), forKey: "keel.disabledHealthItems") } }
    var reminderConfig: ReminderConfig {
        didSet {
            if let data = try? JSONEncoder().encode(reminderConfig) { defaults.set(data, forKey: "keel.reminderConfig") }
        }
    }

    /// She has asked to see the intimacy and bladder group. Off until she does,
    /// so nothing personal appears in the picker uninvited.
    var showsSensitiveSymptoms: Bool { didSet { defaults.set(showsSensitiveSymptoms, forKey: "keel.sensitiveSymptoms") } }
    /// The check-in offers an alcohol count. On by default (she can turn it off in
    /// Settings); the field itself always starts empty, so nothing is recorded unless
    /// she saves a number.
    var notesAlcohol: Bool { didSet { defaults.set(notesAlcohol, forKey: "keel.notesAlcohol") } }
    /// Medicine reminders name the medicine. Off by default: reminders show on the
    /// lock screen, so the generic "Time for your medication" unless she chooses.
    var medicineNamesInReminders: Bool { didSet { defaults.set(medicineNamesInReminders, forKey: "keel.medNamesInReminders") } }
    /// She has finished onboarding but hasn't yet seen the one-line reminders explanation
    /// that comes before the iOS permission prompt. Persisted so closing the app first
    /// doesn't skip it.
    var notificationExplainerPending: Bool { didSet { defaults.set(notificationExplainerPending, forKey: "keel.notificationExplainerPending") } }
    /// She said "Not now" to connecting Apple Health (the launch offer, or Skip in
    /// onboarding). Kept in UserDefaults on purpose: a reinstall clears it, so a fresh
    /// install, where iOS has also dropped Keel's Health access, can offer once again.
    var healthConnectOfferDeclined: Bool { didSet { defaults.set(healthConnectOfferDeclined, forKey: "keel.healthOfferDeclined") } }
    /// She has answered the "Update Apple Health access?" offer (shown to a connected
    /// user when iOS would ask again). Cleared once iOS no longer needs to ask, so a
    /// later new read type gets its own single offer.
    var healthReconnectOfferAnswered: Bool { didSet { defaults.set(healthReconnectOfferAnswered, forKey: "keel.healthReconnectAnswered") } }

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
        disabledHealthItemIDs = Set(defaults.stringArray(forKey: "keel.disabledHealthItems") ?? [])
        reminderConfig = (defaults.data(forKey: "keel.reminderConfig")
            .flatMap { try? JSONDecoder().decode(ReminderConfig.self, from: $0) }) ?? ReminderConfig()
        showsSensitiveSymptoms = defaults.object(forKey: "keel.sensitiveSymptoms") as? Bool ?? false
        notesAlcohol = defaults.object(forKey: "keel.notesAlcohol") as? Bool ?? true
        medicineNamesInReminders = defaults.object(forKey: "keel.medNamesInReminders") as? Bool ?? false
        notificationExplainerPending = defaults.object(forKey: "keel.notificationExplainerPending") as? Bool ?? false
        healthConnectOfferDeclined = defaults.object(forKey: "keel.healthOfferDeclined") as? Bool ?? false
        healthReconnectOfferAnswered = defaults.object(forKey: "keel.healthReconnectAnswered") as? Bool ?? false

        Haptics.userEnabled = haptics // all stored properties are set by here
    }

    /// Back to a fresh install's preferences ("Delete all my data"). Themes and mood
    /// packs she owns are kept: they're things she unlocked, not her data.
    func resetToDefaults() {
        colourMode = .system
        themeID = ThemeCatalog.defaultID
        moodPackID = MoodPacks.defaultID
        pushNotifications = true
        haptics = true
        analytics = false
        icloudBackup = true
        autoBackup = true
        enabledReminderIDs = ["dailyCheckIn", "medication"]
        disabledHealthItemIDs = []
        reminderConfig = ReminderConfig()
        showsSensitiveSymptoms = false
        notesAlcohol = true
        medicineNamesInReminders = false
        notificationExplainerPending = false
        healthConnectOfferDeclined = false
        healthReconnectOfferAnswered = false
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
