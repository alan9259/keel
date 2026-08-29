import SwiftUI
import SwiftData

/// Two-step sheet for adding something she's taking: pick it from the catalog
/// (or type her own), then fill in the details.
///
/// The catalog is a list of what exists, the way a pharmacy shelf is. Nothing is
/// recommended, ranked, or badged, products sort alphabetically, and no dose is
/// ever pre-filled.
struct TreatmentPickerSheet: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    let onAdd: (TreatmentDraft) -> Void

    @Query(filter: #Predicate<Medication> { $0.deletedAt == nil && $0.isActive == true })
    private var current: [Medication]

    @State private var kind: TreatmentKind = .treatment
    @State private var draft: TreatmentDraft?
    /// The group whose "add your own" composer is open.
    @State private var composingGroup: String?
    @State private var customName = ""
    @FocusState private var composerFocused: Bool
    /// Filters the list for anyone who already knows what she's on (beats scrolling).
    @State private var searchText = ""
    @FocusState private var searchFocused: Bool

    var body: some View {
        Group {
            if let draft {
                TreatmentDetailForm(
                    draft: draft,
                    title: alreadyTaking(draft.name) == nil ? "Add details" : "Already on your list",
                    onCancel: { self.draft = nil },
                    onSave: { finished in
                        onAdd(finished)
                        dismiss()
                    }
                )
            } else {
                picker
            }
        }
        .background(theme.background.ignoresSafeArea())
        #if DEBUG
        .onAppear {
            if DebugHarness.supplementsTab { kind = .supplement }
            if DebugHarness.showTreatmentDetail {
                // Pair with -uitSupplements to see the supplement form instead.
                var seed = DebugHarness.supplementsTab
                    ? TreatmentDraft(name: "Magnesium", kind: .supplement,
                                     catalogGroupID: "supplements-common")
                    : TreatmentDraft(name: "Testogel", kind: .treatment,
                                     catalogGroupID: "testosterone", method: .gel, isOffLabel: true)
                if DebugHarness.supplementsTab {
                    // Two shared times and one narrowed to weekends.
                    seed.schedule.slots = [
                        DoseSlot(hour: 8, minute: 0),
                        DoseSlot(hour: 20, minute: 0),
                        DoseSlot(weekdays: [1, 7], hour: 10, minute: 30),
                    ]
                }
                if DebugHarness.cycleScheduleDemo {
                    seed.schedule.kind = .cycle
                    seed.schedule.anchor = Calendar.current.startOfDay(for: .now)
                    seed.schedule.slots = [DoseSlot(hour: 8, minute: 0)]
                }
                draft = seed
            }
        }
        #endif
    }

    // MARK: Picker

    private var picker: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    KeelSegmented(options: TreatmentKind.allCases.map(\.shortLabel),
                                  selection: kindSelection)
                    searchField
                    ForEach(visibleGroups) { group in
                        groupSection(group)
                    }
                    if isSearching { addSearchedButton }
                }
                .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 32)
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").font(.system(size: 14, weight: .medium))
                .foregroundStyle(theme.muted)
            TextField("Search what you're taking", text: $searchText)
                .font(KeelFont.body).foregroundStyle(theme.text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($searchFocused)
                .submitLabel(.search)
            if !searchText.isEmpty {
                Button {
                    searchText = ""
                    searchFocused = false
                } label: {
                    Image(systemName: "xmark.circle.fill").font(.system(size: 15))
                        .foregroundStyle(theme.muted)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(theme.inputBackground)
        .clipShape(Capsule())
    }

    /// When she's searched and it isn't in the list, let her add it by that name
    /// (private to her). Also the only affordance when there are no matches.
    private var addSearchedButton: some View {
        let q = searchText.trimmingCharacters(in: .whitespaces)
        return Button {
            Haptics.selection()
            // A searched name isn't tied to a section, so record it as her own
            // (no catalog group) rather than mis-bucketing it into the first group.
            draft = alreadyTaking(q).map(TreatmentDraft.init) ?? TreatmentDraft(
                name: q, kind: kind, catalogGroupID: nil, method: nil
            )
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "plus").font(.system(size: 12, weight: .semibold))
                Text("Add \u{201C}\(q)\u{201D}").font(KeelFont.body)
            }
            .foregroundStyle(theme.accent)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .overlay(Capsule().stroke(theme.accent.opacity(0.5),
                                      style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
        }
        .buttonStyle(.plain)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text("What are you taking?").font(KeelFont.serif(20, weight: .semibold))
                    .foregroundStyle(theme.heading)
                Text("Pick it from the list, or add your own.")
                    .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            }
            Spacer()
            Button { dismiss() } label: {
                Text("Cancel").font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.accent)
                    .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
            }
        }
        .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 12)
        .overlay(Divider().background(theme.border), alignment: .bottom)
    }

    private func groupSection(_ group: TreatmentCatalog.Group) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if showsSectionHeading(for: group) {
                Text(group.section).keelEyebrow()
            }
            if group.title != group.section {
                Text(group.title).font(KeelFont.serif(16, weight: .semibold)).foregroundStyle(theme.text)
            }
            if let note = group.note {
                Text(note).font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            FlowLayout(spacing: 6) {
                ForEach(group.sortedItems) { item in
                    itemChip(item, in: group)
                }
                // The searched "Add …" affordance sits once at the bottom instead.
                if !isSearching { addYourOwnChip(group) }
            }
            if composingGroup == group.id {
                composer(group)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The section heading only leads the first group that carries it, so
    /// "Oestrogen" isn't repeated above every form of it.
    private func showsSectionHeading(for group: TreatmentCatalog.Group) -> Bool {
        visibleGroups.first { $0.section == group.section }?.id == group.id
    }

    private func itemChip(_ item: TreatmentCatalog.Item, in group: TreatmentCatalog.Group) -> some View {
        let existing = alreadyTaking(item.name)
        return Button {
            Haptics.selection()
            // Already on her list: open what she has rather than starting a
            // second copy of the same thing.
            draft = existing.map(TreatmentDraft.init) ?? TreatmentDraft(
                name: item.name,
                kind: group.kind,
                catalogGroupID: group.id,
                method: item.method ?? group.defaultMethod,
                isOffLabel: item.isOffLabel,
                isCompounded: item.isCompounded
            )
        } label: {
            HStack(spacing: 5) {
                if existing != nil {
                    Image(systemName: "checkmark").font(.system(size: 11, weight: .semibold))
                }
                Text(item.name).font(KeelFont.body)
            }
            .foregroundStyle(existing != nil ? theme.accent : theme.text.opacity(0.85))
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(existing != nil ? theme.accent.opacity(0.1) : theme.card)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(existing != nil ? theme.accent.opacity(0.4) : theme.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityValue(existing != nil ? "Already on your list" : "")
    }

    private func addYourOwnChip(_ group: TreatmentCatalog.Group) -> some View {
        Button {
            Haptics.selection()
            customName = ""
            withAnimation(.easeOut(duration: 0.18)) { composingGroup = group.id }
            composerFocused = true
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "plus").font(.system(size: 12, weight: .semibold))
                Text("Add your own").font(KeelFont.body)
            }
            .foregroundStyle(theme.accent)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .overlay(Capsule().stroke(theme.accent.opacity(0.5),
                                      style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Add your own to \(group.title)")
    }

    private func composer(_ group: TreatmentCatalog.Group) -> some View {
        HStack(spacing: 8) {
            TextField("Name it as it's written on the box…", text: $customName)
                .font(KeelFont.body).foregroundStyle(theme.text)
                .textInputAutocapitalization(.words)
                .focused($composerFocused)
                .submitLabel(.next)
                .onSubmit { commitCustom(in: group) }
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(theme.inputBackground).clipShape(Capsule())
            Button { commitCustom(in: group) } label: {
                Image(systemName: "arrow.up.circle.fill").font(.system(size: 28))
                    .foregroundStyle(canAddCustom ? theme.accent : theme.muted.opacity(0.4))
            }
            .disabled(!canAddCustom)
            .accessibilityLabel("Continue")
            Button {
                customName = ""
                composerFocused = false
                withAnimation(.easeOut(duration: 0.18)) { composingGroup = nil }
            } label: {
                Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(theme.muted).frame(width: 30, height: 30)
            }
            .accessibilityLabel("Cancel")
        }
    }

    // MARK: Logic

    private var groups: [TreatmentCatalog.Group] { env.treatments.groups(for: kind) }

    private var isSearching: Bool { !searchText.trimmingCharacters(in: .whitespaces).isEmpty }

    /// The list, filtered to the search. Groups with no matching item drop out, so
    /// only sections that actually contain a match keep their heading.
    private var visibleGroups: [TreatmentCatalog.Group] {
        TreatmentCatalog.filter(groups, matching: searchText)
    }

    private var kindSelection: Binding<Int> {
        Binding(
            get: { TreatmentKind.allCases.firstIndex(of: kind) ?? 0 },
            set: { index in
                kind = TreatmentKind.allCases[index]
                composingGroup = nil
            }
        )
    }

    private var canAddCustom: Bool {
        !customName.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func commitCustom(in group: TreatmentCatalog.Group) {
        let name = customName.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        composerFocused = false
        composingGroup = nil
        customName = ""
        // Typing the name of something she already takes edits that entry.
        draft = alreadyTaking(name).map(TreatmentDraft.init) ?? TreatmentDraft(
            name: name,
            kind: group.kind,
            catalogGroupID: group.id,
            method: group.defaultMethod
        )
    }

    /// The active entry with this name, if she's already taking it.
    private func alreadyTaking(_ name: String) -> Medication? {
        current.first { $0.name.localizedCaseInsensitiveCompare(name) == .orderedSame }
    }

}

/// The details step, shared by adding and editing. Everything past the name is
/// optional, and no dose is ever suggested.
struct TreatmentDetailForm: View {
    @Environment(\.keelTheme) private var theme

    @State var draft: TreatmentDraft
    let title: String
    /// Shown on the edit path only.
    var onRemove: (() -> Void)?
    let onCancel: () -> Void
    let onSave: (TreatmentDraft) -> Void

    @FocusState private var amountFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    if draft.isOffLabel { offLabelNote }
                    if draft.isCompounded { compoundedNote }
                    if !draft.isCompounded {
                        dosePicker
                    }
                    // Patch, gel, pessary: a prescription idea. A supplement is
                    // just taken, so the question doesn't arise.
                    if draft.kind == .treatment { methodPicker }
                    scheduleSection
                    autoLogSection
                    datesSection
                    notesSection
                    if onRemove != nil { stoppedSection }   // editing an existing entry
                    actions
                }
                .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 32)
            }
        }
        .background(theme.background.ignoresSafeArea())
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(draft.name).font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                Text(title).font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            }
            Spacer()
            Button { onCancel() } label: {
                Text("Back").font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.accent)
                    .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
            }
        }
        .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 12)
        .overlay(Divider().background(theme.border), alignment: .bottom)
    }

    /// Factual, not a warning: it's recorded so her GP summary is accurate.
    private var offLabelNote: some View {
        note("Recorded as off-label, which means it's prescribed outside its approved use. Your GP will have explained why.")
    }

    private var compoundedNote: some View {
        note("Compounded preparations aren't standardised, so Keel records this as compounded rather than a set strength.")
    }

    private func note(_ text: String) -> some View {
        Text(text)
            .font(KeelFont.sans(12)).foregroundStyle(theme.text.opacity(0.75))
            .fixedSize(horizontal: false, vertical: true)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    /// A number and a unit. Nothing is pre-selected: she reads it off her own box.
    private var dosePicker: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(doseLabel).font(KeelFont.body).fontWeight(.medium).foregroundStyle(theme.text)
            HStack(spacing: Spacing.sm) {
                TextField("Amount", text: amountText)
                    .font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                    .keyboardType(.decimalPad)
                    .focused($amountFocused)
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(theme.inputBackground)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.input, style: .continuous))
                unitMenu
            }
            if !draft.legacyDosage.isEmpty && draft.doseAmount == nil {
                Text("Previously recorded as \(draft.legacyDosage).")
                    .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            }
        }
    }

    private var unitMenu: some View {
        Menu {
            ForEach(DoseUnit.allCases) { unit in
                Button { draft.doseUnit = unit } label: {
                    if draft.doseUnit == unit {
                        Label(unit.label, systemImage: "checkmark")
                    } else {
                        Text(unit.label)
                    }
                }
            }
            if draft.doseUnit != nil {
                Divider()
                Button("Clear") { draft.doseUnit = nil }
            }
        } label: {
            HStack(spacing: 6) {
                Text(draft.doseUnit?.label ?? "Unit")
                    .font(KeelFont.bodyLarge)
                    .foregroundStyle(draft.doseUnit == nil ? theme.muted : theme.text)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(theme.muted)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(minWidth: 96, minHeight: 44)
            .background(theme.inputBackground)
            .clipShape(RoundedRectangle(cornerRadius: Radius.input, style: .continuous))
        }
        .accessibilityLabel("Unit")
        .accessibilityValue(draft.doseUnit?.label ?? "Not set")
    }

    private var doseLabel: String {
        draft.kind == .treatment ? "Strength" : "Dose"
    }

    /// Text binding over the numeric amount, so an empty field means "not set"
    /// rather than zero.
    private var amountText: Binding<String> {
        Binding(
            get: {
                guard let amount = draft.doseAmount else { return "" }
                return amount == amount.rounded() ? String(Int(amount)) : String(amount)
            },
            set: { text in
                let cleaned = text.replacingOccurrences(of: ",", with: ".")
                draft.doseAmount = cleaned.isEmpty ? nil : Double(cleaned)
            }
        )
    }

    private var methodPicker: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("How you take it").font(KeelFont.body).fontWeight(.medium).foregroundStyle(theme.text)
            FlowLayout(spacing: 6) {
                ForEach(MedicationMethod.allCases) { method in
                    optionChip(method.label, isSelected: draft.method == method) {
                        draft.method = draft.method == method ? nil : method
                    }
                }
            }
        }
    }

    // MARK: Schedule

    /// A weekly pattern, or a cycle with a pause, plus an optional time. The
    /// summary underneath says it back in plain words, so what she's set is never
    /// something she has to work out from the controls.
    private var scheduleSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("When you take it").font(KeelFont.body).fontWeight(.medium).foregroundStyle(theme.text)
            FlowLayout(spacing: 6) {
                ForEach(DoseSchedule.Kind.allCases) { kind in
                    optionChip(kind.label, isSelected: draft.schedule.kind == kind) {
                        setScheduleKind(kind)
                    }
                }
            }
            if draft.schedule.kind == .cycle { cycleControls }
            if draft.schedule.kind != .asNeeded { slotList }
            Text(draft.schedule.summary)
                .font(KeelFont.sans(12)).foregroundStyle(theme.accent)
                .padding(.top, 2)
        }
    }

    /// Opt-in convenience: mark this medicine's doses taken without tapping. The
    /// reminder's "Always mark taken" button turns the same thing on.
    private var autoLogSection: some View {
        Toggle(isOn: $draft.autoLogDoses) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Mark taken automatically")
                    .font(KeelFont.body).fontWeight(.medium).foregroundStyle(theme.text)
                Text("Logs this medicine's doses as taken on the days you open the app, without tapping.")
                    .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .tint(theme.accent)
    }

    /// One card per dose, each leading with its own time and carrying its own
    /// days, so adding an evening dose can't silently take the morning's days.
    private var slotList: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ForEach(draft.schedule.sortedSlots) { slot in
                slotCard(slot)
            }
            Button {
                Haptics.selection()
                addSlot()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "plus").font(.system(size: 12, weight: .semibold))
                    Text(draft.schedule.slots.count > 1 ? "Add another time" : "Add a second time")
                        .font(KeelFont.body)
                }
                .foregroundStyle(theme.accent)
                .padding(.horizontal, 14).padding(.vertical, 10)
                .overlay(Capsule().stroke(theme.accent.opacity(0.5),
                                          style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
            }
            .buttonStyle(.plain)
        }
    }

    private func slotCard(_ slot: DoseSlot) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: Spacing.sm) {
                if slot.hasTime {
                    DatePicker("Time", selection: timeBinding(for: slot), displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .datePickerStyle(.compact)
                        .tint(theme.accent)
                    Button {
                        Haptics.light()
                        update(slot) { $0.date = nil }
                    } label: {
                        Text("Clear time").font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                            .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Remove the time from this dose")
                } else {
                    Button {
                        Haptics.selection()
                        update(slot) { $0.hour = 9; $0.minute = 0 }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "bell").font(.system(size: 13))
                            Text("Add a time").font(KeelFont.body)
                        }
                        .foregroundStyle(theme.accent)
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
                if draft.schedule.slots.count > 1 {
                    Button {
                        Haptics.light()
                        draft.schedule.slots.removeAll { $0.id == slot.id }
                    } label: {
                        Image(systemName: "xmark").font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(theme.muted).frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Remove the \(slot.label) dose")
                }
            }
            // A cycle already says which days it runs, so the week doesn't apply.
            if draft.schedule.kind == .weekly {
                dayRow(slot)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
            .stroke(theme.border, lineWidth: 1))
    }

    private func dayRow(_ slot: DoseSlot) -> some View {
        HStack(spacing: 5) {
            ForEach(DoseSchedule.orderedWeekdays(), id: \.self) { weekday in
                let isOn = slot.applies(toWeekday: weekday)
                Button {
                    toggle(weekday, on: slot)
                } label: {
                    Text(DoseSchedule.initial(weekday))
                        .font(KeelFont.sans(12, weight: .medium))
                        .foregroundStyle(isOn ? .white : theme.text.opacity(0.7))
                        .frame(width: 36, height: 40)
                        .background(isOn ? theme.accent : theme.background)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(isOn ? .clear : theme.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(DoseSchedule.fullName(weekday))
                .accessibilityAddTraits(isOn ? [.isSelected, .isButton] : .isButton)
            }
            Spacer(minLength: 0)
        }
    }

    private func timeBinding(for slot: DoseSlot) -> Binding<Date> {
        Binding(
            get: { draft.schedule.slots.first { $0.id == slot.id }?.date ?? slot.date ?? .now },
            set: { newValue in update(slot) { $0.date = newValue } }
        )
    }

    private func update(_ slot: DoseSlot, _ change: (inout DoseSlot) -> Void) {
        guard let index = draft.schedule.slots.firstIndex(where: { $0.id == slot.id }) else { return }
        change(&draft.schedule.slots[index])
    }

    /// The last remaining day stays put: a dose with no days would never come
    /// round, which is never what she meant.
    private func toggle(_ weekday: Int, on slot: DoseSlot) {
        update(slot) { dose in
            var days = dose.weekdays.isEmpty ? Set(1...7) : dose.weekdays
            if days.contains(weekday) {
                guard days.count > 1 else { return }
                days.remove(weekday)
            } else {
                days.insert(weekday)
            }
            Haptics.selection()
            dose.weekdays = days
        }
    }

    /// A new dose starts every day at a fresh time rather than copying the last
    /// one's days, so nothing is inherited without her seeing it.
    private func addSlot() {
        let hour = draft.schedule.sortedSlots.last(where: \.hasTime).map { ($0.hour ?? 9) >= 12 ? 8 : 20 } ?? 9
        draft.schedule.slots.append(DoseSlot(hour: hour, minute: 0))
    }

    private var cycleControls: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            stepper("Cycle length", value: draft.schedule.cycleLength, unit: "days", range: 2...90) {
                draft.schedule.cycleLength = $0
                draft.schedule.pauseDays = min(draft.schedule.pauseDays, $0 - 1)
            }
            stepper("Pause at the end", value: draft.schedule.pauseDays, unit: "days",
                    range: 0...(draft.schedule.cycleLength - 1)) {
                draft.schedule.pauseDays = $0
            }
            stepper("Today is day", value: cycleDayToday, unit: "", range: 1...draft.schedule.cycleLength) {
                setCycleDayToday($0)
            }
        }
    }

    private func stepper(_ label: String, value: Int, unit: String,
                         range: ClosedRange<Int>, onChange: @escaping (Int) -> Void) -> some View {
        HStack(spacing: 12) {
            Text(label).font(KeelFont.body).foregroundStyle(theme.text)
            Spacer(minLength: 8)
            stepButton("minus", enabled: value > range.lowerBound) { onChange(value - 1) }
            Text(unit.isEmpty ? "\(value)" : "\(value) \(unit)")
                .font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.text)
                .frame(minWidth: 64)
            stepButton("plus", enabled: value < range.upperBound) { onChange(value + 1) }
        }
        .accessibilityElement(children: .combine)
        .accessibilityValue(unit.isEmpty ? "\(value)" : "\(value) \(unit)")
    }

    private func stepButton(_ symbol: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button {
            guard enabled else { return }
            Haptics.selection()
            action()
        } label: {
            Image(systemName: symbol).font(.system(size: 13, weight: .semibold))
                .foregroundStyle(enabled ? theme.text : theme.muted.opacity(0.4))
                .frame(width: 44, height: 44)
                .background(theme.track).clipShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(symbol == "minus" ? "Decrease" : "Increase")
    }

    /// Which day of the cycle today falls on, so she can set one up part way
    /// through instead of pretending it starts today.
    private var cycleDayToday: Int {
        draft.schedule.cycleDay(for: .now) ?? 1
    }

    private func setCycleDayToday(_ day: Int) {
        draft.schedule.anchor = Calendar.current.date(byAdding: .day, value: -(day - 1),
                                                      to: Calendar.current.startOfDay(for: .now))
    }

    private func setScheduleKind(_ kind: DoseSchedule.Kind) {
        draft.schedule.kind = kind
        if kind == .cycle, draft.schedule.anchor == nil {
            draft.schedule.anchor = Calendar.current.startOfDay(for: .now)
        }
    }

    /// Times sit under the days and apply to all of them unless she narrows one.
    /// Leaving this empty is a schedule with no reminder, which is what a patch
    /// changed on set days usually wants.
    @ViewBuilder
    private func optionChip(_ title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.selection()
            action()
        } label: {
            Text(title).font(KeelFont.body)
                .foregroundStyle(isSelected ? .white : theme.text.opacity(0.85))
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(isSelected ? theme.accent : theme.card)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(isSelected ? .clear : theme.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }

    /// Dates are kept for her, not asked for: the day she added it, and the day a
    /// dose last changed. Reflected back, never an input.
    @ViewBuilder
    private var datesSection: some View {
        if draft.date != nil || draft.doseChangedAt != nil {
            VStack(alignment: .leading, spacing: 4) {
                if let date = draft.date { dateLine("Added", date) }
                if let changed = draft.doseChangedAt { dateLine("Dose last changed", changed) }
            }
        }
    }

    private func dateLine(_ label: String, _ date: Date) -> some View {
        Text("\(label) \(date.formatted(.dateTime.day().month(.abbreviated).year()))")
            .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
    }

    /// "I'm still taking this" — turning it off keeps the treatment (not a delete) so
    /// the GP summary can show it. The stop date is left BLANK unless she adds it: it
    /// is never defaulted to today, because a system timestamp she didn't enter must
    /// not reach the summary (product-alignment note, item 1). She taps "Add the date"
    /// to enter one; the summary shows a date only if she does.
    private var stoppedSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Toggle(isOn: stillTakingBinding) {
                Text("I'm still taking this").font(KeelFont.body).foregroundStyle(theme.text)
            }
            .tint(theme.accent)
            if !draft.isActive {
                if draft.stoppedAt != nil {
                    DatePicker("Stopped on",
                               selection: Binding(get: { draft.stoppedAt ?? Date() },
                                                  set: { draft.stoppedAt = $0 }),
                               in: ...Date(), displayedComponents: .date)
                        .font(KeelFont.body).foregroundStyle(theme.text).tint(theme.accent)
                    Button { draft.stoppedAt = nil } label: {
                        Text("Remove the date").font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                    }
                } else {
                    Button { draft.stoppedAt = Date() } label: {
                        Label("Add the date you stopped (optional)", systemImage: "calendar.badge.plus")
                            .font(KeelFont.body).foregroundStyle(theme.accent)
                    }
                    Text("Left blank unless you add it. Your GP summary shows a stop date only if you enter one.")
                        .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var stillTakingBinding: Binding<Bool> {
        Binding(
            get: { draft.isActive },
            set: { stillTaking in
                draft.isActive = stillTaking
                // Stopping never stamps a date; reactivating clears any date she added.
                if stillTaking { draft.stoppedAt = nil }
            }
        )
    }

    private var notesSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text("Note").font(KeelFont.body).fontWeight(.medium).foregroundStyle(theme.text)
                Spacer()
                Text("Optional").font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            }
            KeelTextEditor(placeholder: "Anything worth remembering, like a brand swap at the chemist.",
                           text: $draft.note) { EmptyView() }
        }
    }

    private var actions: some View {
        VStack(spacing: Spacing.md) {
            KeelPrimaryButton("Save", systemImage: "checkmark") { onSave(draft) }
            if let onRemove {
                Button {
                    Haptics.light()
                    onRemove()
                } label: {
                    Text("Remove from my list").font(KeelFont.body).foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, Spacing.sm)
    }
}
