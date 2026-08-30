import SwiftUI

struct ReminderDef: Identifiable {
    let id: String
    let symbol: String
    let label: String
    let desc: String
}

enum ReminderCatalog {
    static let all: [ReminderDef] = [
        ReminderDef(id: "dailyCheckIn", symbol: "sunrise.fill", label: "Daily check-in",
                    desc: "A gentle nudge to log how you're feeling"),
        ReminderDef(id: "medication", symbol: "pills.fill", label: "Medication reminders",
                    desc: "On the days each one is due"),
        ReminderDef(id: "hydration", symbol: "drop.fill", label: "Hydration",
                    desc: "Stay on top of water through the day"),
        ReminderDef(id: "movement", symbol: "figure.walk", label: "Movement",
                    desc: "A little daily activity"),
        ReminderDef(id: "winddown", symbol: "moon.stars.fill", label: "Wind-down",
                    desc: "Prepare for a better night's sleep"),
    ]
}

struct RemindersView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ScreenHeader(title: "Reminders", titleSize: 28,
                             subtitle: "Gentle nudges, on your terms") { dismiss() }
                    .padding(.bottom, 4)

                ForEach(ReminderCatalog.all) { reminder in
                    card(reminder)
                }

                Text("Reminders respect your device's Do Not Disturb settings.")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity).multilineTextAlignment(.center)
                    .padding(.top, 8)
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        // Any edit to the timing reschedules the reminders she has switched on.
        .onChange(of: env.settings.reminderConfig) { _, _ in rescheduleEnabled() }
    }

    private func card(_ reminder: ReminderDef) -> some View {
        let isOn = env.settings.enabledReminderIDs.contains(reminder.id)
        return VStack(spacing: 0) {
            HStack(spacing: 14) {
                Image(systemName: reminder.symbol).font(.system(size: 16))
                    .foregroundStyle(isOn ? theme.accent : theme.muted)
                    .frame(width: 40, height: 40)
                    .background(isOn ? theme.accent.opacity(0.12) : theme.inputBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(reminder.label).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                    Text(reminder.desc).font(KeelFont.caption).foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 8)
                Toggle("", isOn: Binding(get: { isOn }, set: { setEnabled(reminder, $0) }))
                    .labelsHidden().tint(theme.accent)
            }
            .padding(14)

            if isOn {
                VStack(spacing: 12) {
                    editor(for: reminder.id)
                }
                .padding(14)
                .background(theme.track.opacity(0.35))
                .overlay(Divider().background(theme.border), alignment: .top)
            }
        }
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    // MARK: Per-reminder editors

    @ViewBuilder
    private func editor(for id: String) -> some View {
        switch id {
        case "dailyCheckIn":
            timeRow("Reminder time", hour: \.checkInHour, minute: \.checkInMinute)
        case "hydration":
            hourRow("Start", \.hydrationStartHour)
            hourRow("End", \.hydrationEndHour)
            HStack {
                Text("Remind me").font(KeelFont.body).foregroundStyle(theme.muted)
                Spacer()
                Stepper(value: bind(\.hydrationIntervalHours), in: 1...6) {
                    let n = env.settings.reminderConfig.hydrationIntervalHours
                    Text("Every \(n) hour\(n == 1 ? "" : "s")").font(KeelFont.body).foregroundStyle(theme.text)
                }
                .fixedSize()
            }
        case "movement":
            timeRow("Reminder time", hour: \.movementHour, minute: \.movementMinute)
            HStack {
                Text("Days").font(KeelFont.body).foregroundStyle(theme.muted)
                Spacer()
                KeelSegmented(options: ["Weekdays", "Every day"], selection: Binding(
                    get: { env.settings.reminderConfig.movementWeekdaysOnly ? 0 : 1 },
                    set: { env.settings.reminderConfig.movementWeekdaysOnly = ($0 == 0) }
                ))
                .frame(width: 190)
            }
        case "winddown":
            timeRow("Reminder time", hour: \.windDownHour, minute: \.windDownMinute)
        case "medication":
            detailRow("Reminder time", "Per item")
            detailRow("Set in", "Each entry")
        default:
            EmptyView()
        }
    }

    private func timeRow(_ label: String, hour: WritableKeyPath<ReminderConfig, Int>, minute: WritableKeyPath<ReminderConfig, Int>) -> some View {
        HStack {
            Text(label).font(KeelFont.body).foregroundStyle(theme.muted)
            Spacer()
            DatePicker("", selection: timeBinding(hour: hour, minute: minute), displayedComponents: .hourAndMinute)
                .labelsHidden()
        }
    }

    /// Whole-hour picker (for the hydration window, which steps by the hour).
    private func hourRow(_ label: String, _ keyPath: WritableKeyPath<ReminderConfig, Int>) -> some View {
        HStack {
            Text(label).font(KeelFont.body).foregroundStyle(theme.muted)
            Spacer()
            Picker("", selection: bind(keyPath)) {
                ForEach(0..<24, id: \.self) { Text(String(format: "%02d:00", $0)).tag($0) }
            }
            .labelsHidden().pickerStyle(.menu).tint(theme.accent)
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(KeelFont.body).foregroundStyle(theme.muted)
            Spacer()
            Text(value).font(KeelFont.body).foregroundStyle(theme.text)
                .padding(.horizontal, 12).padding(.vertical, 5)
                .background(theme.card)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(theme.border, lineWidth: 1))
        }
    }

    // MARK: Bindings into the persisted config

    private func bind<T>(_ keyPath: WritableKeyPath<ReminderConfig, T>) -> Binding<T> {
        Binding(
            get: { env.settings.reminderConfig[keyPath: keyPath] },
            set: { env.settings.reminderConfig[keyPath: keyPath] = $0 }
        )
    }

    private func timeBinding(hour: WritableKeyPath<ReminderConfig, Int>, minute: WritableKeyPath<ReminderConfig, Int>) -> Binding<Date> {
        Binding(
            get: {
                let c = env.settings.reminderConfig
                return Calendar.current.date(from: DateComponents(hour: c[keyPath: hour], minute: c[keyPath: minute])) ?? Date()
            },
            set: {
                let comps = Calendar.current.dateComponents([.hour, .minute], from: $0)
                env.settings.reminderConfig[keyPath: hour] = comps.hour ?? 0
                env.settings.reminderConfig[keyPath: minute] = comps.minute ?? 0
            }
        )
    }

    // MARK: Scheduling

    private func setEnabled(_ reminder: ReminderDef, _ on: Bool) {
        if on {
            env.settings.enabledReminderIDs.insert(reminder.id)
            Task {
                let granted = await env.notifications.requestAuthorization()
                guard granted else { return }
                schedule(reminder.id)
            }
        } else {
            env.settings.enabledReminderIDs.remove(reminder.id)
            cancel(reminder.id)
        }
        Haptics.selection()
    }

    private func rescheduleEnabled() {
        for id in ["dailyCheckIn", "hydration", "movement", "winddown"]
        where env.settings.enabledReminderIDs.contains(id) {
            schedule(id)
        }
    }

    private func schedule(_ id: String) {
        guard env.settings.pushNotifications else { return } // master switch off
        let c = env.settings.reminderConfig
        switch id {
        case "dailyCheckIn": env.notifications.scheduleDailyCheckInReminder(hour: c.checkInHour, minute: c.checkInMinute)
        // Lifestyle nudges keep their Apple-Intelligence tip across an edit (reused
        // from cache, no fresh model call on every picker change).
        case "hydration", "movement", "winddown": env.rescheduleLifestyleReminder(id)
        default: break // medication reminders are set per item
        }
    }

    private func cancel(_ id: String) {
        switch id {
        case "dailyCheckIn": env.notifications.cancelDailyCheckIn()
        case "hydration": env.notifications.cancelHydration()
        case "movement": env.notifications.cancelMovement()
        case "winddown": env.notifications.cancelWindDown()
        default: break
        }
    }
}
