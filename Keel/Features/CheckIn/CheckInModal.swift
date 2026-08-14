import SwiftUI
import SwiftData

/// The check-in detail screen. Mood is captured up front in the entry slide
/// (`EntrySheet`) and passed in here, so this screen only gathers energy, an
/// optional diary (with voice), and symptoms — all on one scroll. Tapping
/// "Change" on the mood recap hands back to the slide via `onChangeMood`.
struct CheckInModal: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme

    let mood: Mood
    /// The day this entry belongs to (today by default, or a past day being added
    /// to / edited).
    let entryDate: Date
    /// When set, we're editing this existing entry rather than creating one.
    let editingID: UUID?
    /// Called on dismiss; `saved` is true when an entry was written.
    let onClose: (_ saved: Bool) -> Void
    /// Called when the user wants to re-pick their mood (reopens the slide).
    let onChangeMood: () -> Void
    /// Called after an existing entry is removed, so the caller can dismiss + confirm.
    let onDelete: () -> Void

    @State private var showDeleteConfirm = false

    @Query(filter: #Predicate<Symptom> { $0.deletedAt == nil && $0.isArchived == false }, sort: \Symptom.name)
    private var symptoms: [Symptom]

    @State private var energy: EnergyLevel?
    @State private var notes = ""
    /// Symptom id → severity level (1…3). Absent means unselected.
    @State private var selected: [UUID: Int] = [:]
    /// The "more" picker, where the full grouped list lives.
    @State private var showingPicker = false
    /// Default chip ids in display order, resolved once when the screen opens so
    /// the row doesn't reshuffle under her while she's tapping.
    @State private var defaultChipOrder: [UUID] = []

    /// Guards the one-time prefill so editing doesn't clobber her taps on re-render.
    @State private var didPrefill = false

    init(mood: Mood, entryDate: Date = .now, editingID: UUID? = nil,
         onClose: @escaping (_ saved: Bool) -> Void, onChangeMood: @escaping () -> Void,
         onDelete: @escaping () -> Void = {}) {
        self.mood = mood
        self.entryDate = entryDate
        self.editingID = editingID
        self.onClose = onClose
        self.onChangeMood = onChangeMood
        self.onDelete = onDelete
    }

    private var dateLabel: String {
        if entryDate.isSameDay(as: .now) { return "Today" }
        if entryDate.isSameDay(as: Date.now.adding(days: -1)) { return "Yesterday" }
        return entryDate.formatted(.dateTime.weekday(.wide).month(.abbreviated).day())
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    recap
                    energySection
                    diarySection
                    symptomsSection
                    if editingID != nil { removeButton }
                }
                .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 30)
            }
            saveBar
        }
        .background(theme.background.ignoresSafeArea())
        .confirmationDialog("Remove this entry?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("Remove entry", role: .destructive) { performDelete() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the whole check-in for \(dateLabel.lowercased()). It can't be undone.")
        }
        .sheet(isPresented: $showingPicker) {
            SymptomPickerSheet(selected: $selected)
                .presentationDragIndicator(.visible)
        }
        .onAppear {
            defaultChipOrder = env.symptoms.defaultChips().map(\.id)
            prefillIfEditing()
            #if DEBUG
            if DebugHarness.showSymptomPicker { showingPicker = true }
            #endif
        }
    }

    // MARK: Header

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(editingID == nil ? "New entry" : "Edit entry")
                    .font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                Text(dateLabel).font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            }
            Spacer()
            Button { env.speech.reset(); onClose(false) } label: {
                Image(systemName: "xmark").font(.system(size: 20, weight: .medium))
                    .foregroundStyle(theme.muted).frame(width: 36, height: 36)
            }
            .accessibilityLabel("Close")
        }
        .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 14)
        .overlay(Divider().background(theme.border), alignment: .bottom)
    }

    private var recap: some View {
        HStack(spacing: 12) {
            EmojiGlyph(emoji: env.settings.emoji(for: mood), size: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text(mood.label).font(KeelFont.body).foregroundStyle(theme.text)
                Button { env.speech.reset(); onChangeMood() } label: {
                    Text("Change").font(KeelFont.sans(12)).foregroundStyle(theme.accent)
                }
            }
            Spacer()
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(theme.accent.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.accent.opacity(0.2), lineWidth: 1))
    }

    private var energySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("Energy level").font(KeelFont.serif(16, weight: .semibold)).foregroundStyle(theme.text)
                Spacer()
                if let energy {
                    Text(energy.label).font(KeelFont.sans(14, weight: .medium)).foregroundStyle(theme.accent)
                }
            }
            HStack(spacing: 8) {
                ForEach(EnergyLevel.allCases) { level in
                    Button {
                        energy = level; Haptics.selection()
                    } label: {
                        VStack(spacing: 4) {
                            Text("\(level.rawValue)")
                                .font(KeelFont.sans(18, weight: .semibold))
                                .foregroundStyle(energy == level ? .white : theme.text.opacity(0.6))
                            Text(level.label)
                                .font(KeelFont.sans(10))
                                .foregroundStyle(energy == level ? .white.opacity(0.9) : theme.muted)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(energy == level ? level.color : theme.card)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                            .stroke(energy == level ? .clear : theme.border, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var diarySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Diary").font(KeelFont.serif(16, weight: .semibold)).foregroundStyle(theme.text)
                Spacer()
                Text("Optional").font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            }
            KeelTextEditor(placeholder: "Anything on your mind… a rough night, a small win, how you're feeling in words.", text: $notes) {
                VoiceInputButton(isRecording: env.speech.isRecording) { toggleVoice() }
            }
            if env.speech.isRecording {
                HStack(spacing: 6) {
                    Circle().fill(theme.accent).frame(width: 7, height: 7)
                    Text("Listening…").font(KeelFont.sans(12)).foregroundStyle(theme.accent)
                }
            }
        }
        .onChange(of: env.speech.transcript) { _, t in if env.speech.isRecording { notes = t } }
    }

    /// The common, low-sensitivity chips only. Everything else sits behind
    /// "more", so the check-in stays a 15-second exhale.
    private var symptomsSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text("Symptoms").font(KeelFont.serif(16, weight: .semibold)).foregroundStyle(theme.text)
                Spacer()
                Text(selected.isEmpty ? "None" : "\(selected.count) selected")
                    .font(KeelFont.sans(12)).foregroundStyle(theme.accent)
            }
            Text("Tap for severity: once mild, twice moderate, three times severe.")
                .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            severityLegend
            FlowLayout(spacing: 6) {
                ForEach(quickChips) { symptom in
                    SymptomChip(title: symptom.name, level: selected[symptom.id] ?? 0) { toggle(symptom) }
                }
                moreChip
            }
        }
    }

    /// Opens the full grouped picker.
    private var moreChip: some View {
        Button {
            Haptics.selection()
            showingPicker = true
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "plus").font(.system(size: 12, weight: .semibold))
                Text("More").font(KeelFont.body)
            }
            .foregroundStyle(theme.accent)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .overlay(Capsule().stroke(theme.accent.opacity(0.5),
                                      style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("More symptoms")
    }

    /// Small colour key for the three severity levels.
    private var severityLegend: some View {
        HStack(spacing: 14) {
            ForEach(SymptomSeverity.allCases) { level in
                HStack(spacing: 5) {
                    Circle().fill(level.color).frame(width: 8, height: 8)
                    Text(level.label).font(KeelFont.sans(11)).foregroundStyle(theme.muted)
                }
            }
        }
    }

    private var saveBar: some View {
        VStack(spacing: 0) {
            Divider().background(theme.border)
            KeelPrimaryButton("Save entry", systemImage: "checkmark", action: save)
                .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 24)
        }
        .background(theme.background)
    }

    /// Only on an existing entry: remove the whole check-in (with a confirmation).
    /// Uses the brand attention colour (amber), never red (brand rule).
    private var removeButton: some View {
        let warn = theme.attention
        return Button { showDeleteConfirm = true } label: {
            Label("Remove entry", systemImage: "trash")
                .font(KeelFont.body).foregroundStyle(warn)
                .frame(maxWidth: .infinity).padding(.vertical, 12)
                .overlay(RoundedRectangle(cornerRadius: Radius.input, style: .continuous)
                    .stroke(warn.opacity(0.4), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .padding(.top, 6)
    }

    private func performDelete() {
        if let id = editingID, let entry = fetchEntry(id) { env.checkIns.delete(entry) }
        env.speech.reset()
        Haptics.success()
        onDelete()
    }

    // MARK: Logic

    /// The default chips, plus anything she picked in the "more" picker so she
    /// can see and adjust every selection without reopening it.
    private var quickChips: [Symptom] {
        let byID = Dictionary(symptoms.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let defaults = defaultChipOrder.compactMap { byID[$0] }
        let defaultIDs = Set(defaultChipOrder)
        let extras = symptoms.filter { selected[$0.id] != nil && !defaultIDs.contains($0.id) }
        return defaults + extras
    }

    private func toggle(_ s: Symptom) {
        Haptics.selection()
        let next = SymptomSeverity.nextLevel(after: selected[s.id] ?? 0)
        if next == 0 { selected[s.id] = nil } else { selected[s.id] = next }
    }

    private func toggleVoice() {
        if env.speech.isRecording {
            env.speech.stop()
        } else {
            Task {
                let ok = await env.speech.requestAuthorization()
                guard ok else { return }
                try? env.speech.start(seed: notes)
            }
        }
    }

    /// Load an existing entry into the form, once, when editing.
    private func prefillIfEditing() {
        guard let id = editingID, !didPrefill else { return }
        didPrefill = true
        guard let entry = fetchEntry(id) else { return }
        energy = EnergyLevel.from(percent: entry.energy)
        notes = entry.notes ?? ""
        selected = Dictionary(uniqueKeysWithValues:
            (entry.symptomLinks ?? []).filter { !$0.isTombstoned }.compactMap { link in
                link.symptom.map { ($0.id, link.severity) }
            })
    }

    private func fetchEntry(_ id: UUID) -> CheckIn? {
        let descriptor = FetchDescriptor<CheckIn>(predicate: #Predicate { $0.id == id })
        return (try? env.context.fetch(descriptor))?.first
    }

    private func save() {
        let byID = Dictionary(uniqueKeysWithValues: env.symptoms.allActive().map { ($0.id, $0) })
        let picked: [(symptom: Symptom, severity: Int)] = selected.compactMap { id, level in
            guard level > 0, let s = byID[id] else { return nil }
            return (s, level)
        }
        if let id = editingID, let entry = fetchEntry(id) {
            env.checkIns.update(entry, mood: mood, energy: (energy ?? .okay).percent,
                                notes: notes, symptoms: picked)
        } else {
            // Stamp the entry with the actual time of day, on the day being logged.
            // The day selector hands us midnight (start of day), so without this
            // every entry in a day would read as the same time and lose their order.
            let cal = Calendar.current
            let t = cal.dateComponents([.hour, .minute, .second], from: .now)
            let stamp = cal.date(bySettingHour: t.hour ?? 0, minute: t.minute ?? 0,
                                 second: t.second ?? 0, of: entryDate) ?? .now
            env.checkIns.create(mood: mood, energy: (energy ?? .okay).percent,
                                notes: notes, symptoms: picked, date: stamp)
        }
        env.speech.reset()
        Haptics.success()
        onClose(true)
    }
}
