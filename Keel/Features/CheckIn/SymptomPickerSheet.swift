import SwiftUI
import SwiftData

/// The full symptom picker, opened from "More" on the check-in. The check-in
/// itself stays a short set of common chips; everything else lives here, grouped
/// gently, so nothing is in her face until she goes looking.
///
/// Selections write straight back to the check-in's `selected` map, so a chip
/// picked here shows up on the check-in with its severity intact.
struct SymptomPickerSheet: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    /// Symptom id → severity level (1…3). Absent means unselected.
    @Binding var selected: [UUID: Int]

    @Query(filter: #Predicate<Symptom> { $0.deletedAt == nil && $0.isArchived == false }, sort: \Symptom.name)
    private var symptoms: [Symptom]

    /// The category (raw value) currently showing its inline composer.
    @State private var composingCategory: String?
    @State private var draftName = ""
    @FocusState private var composerFocused: Bool
    /// Edit mode for the list (rename / remove; moving is drag-and-drop).
    @State private var editing = false
    @State private var renaming: Symptom?
    @State private var renameText = ""
    /// The category raw currently highlighted as a drag drop target.
    @State private var dropTarget: String?

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    guidance
                    ForEach(groups, id: \.raw) { group in
                        symptomGroup(group)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 32)
            }
        }
        .background(theme.background.ignoresSafeArea())
        .alert("Rename symptom", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Name", text: $renameText)
            Button("Save") { commitRename() }
            Button("Cancel", role: .cancel) { renaming = nil }
        }
        #if DEBUG
        .onAppear {
            if DebugHarness.openSymptomComposer { composingCategory = SymptomCategory.sleep.rawValue }
            if DebugHarness.editSymptoms { editing = true }
        }
        #endif
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("More symptoms").font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                Text(selected.isEmpty ? "Nothing selected yet" : "\(selected.count) selected")
                    .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
            }
            Spacer()
            editToggle
            Button { dismiss() } label: {
                Text("Done").font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.accent)
                    .frame(minWidth: 44, minHeight: 44, alignment: .trailing)
            }
        }
        .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 12)
        .overlay(Divider().background(theme.border), alignment: .bottom)
    }

    private var guidance: some View {
        Text(editing
             ? "Press and hold a symptom to drag it into another group. Tap it to rename or remove it."
             : "Tap for severity: once mild, twice moderate, three times severe.")
            .font(KeelFont.sans(12)).foregroundStyle(theme.muted)
    }

    /// The "Edit" widget (rename / remove; moving is drag-and-drop).
    private var editToggle: some View {
        Button {
            withAnimation(.easeOut(duration: 0.18)) {
                editing.toggle()
                if editing { composingCategory = nil }
            }
            Haptics.selection()
        } label: {
            HStack(spacing: 4) {
                Image(systemName: editing ? "checkmark" : "slider.horizontal.3")
                    .font(.system(size: 11, weight: .semibold))
                // Not "Done": that's the sheet's own dismiss, sitting right beside it.
                Text(editing ? "Finish" : "Edit").font(KeelFont.sans(12, weight: .medium))
            }
            .foregroundStyle(theme.accent)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(theme.accent.opacity(0.1)).clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(editing ? "Finish editing symptoms" : "Edit symptoms")
    }

    // MARK: Groups

    private func symptomGroup(_ group: SymptomGroup) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text(group.label).keelEyebrow()
                if dropTarget == group.raw {
                    Image(systemName: "arrow.down.to.line").font(.system(size: 10, weight: .bold))
                        .foregroundStyle(theme.accent)
                }
            }
            if group.isSensitive, !env.settings.showsSensitiveSymptoms {
                sensitiveInvitation(group)
            } else {
                if let intro = group.intro {
                    Text(intro).font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                FlowLayout(spacing: 6) {
                    ForEach(group.items) { symptom in
                        chip(symptom)
                    }
                    if !editing {
                        addChip(group)
                    }
                    if group.items.isEmpty && editing {
                        Text("Drop here").font(KeelFont.sans(12)).foregroundStyle(theme.muted)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                    }
                }
                if !editing, composingCategory == group.raw {
                    composer(group)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(
            RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                .fill(dropTarget == group.raw ? theme.accent.opacity(0.08) : Color.clear)
                .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                    .stroke(dropTarget == group.raw ? theme.accent.opacity(0.6) : Color.clear,
                            style: StrokeStyle(lineWidth: 1.5, dash: [5, 3])))
        )
        .dropDestination(for: String.self) { ids, _ in
            moveDropped(ids, toRaw: group.raw)
        } isTargeted: { targeted in
            withAnimation(.easeOut(duration: 0.12)) {
                if targeted { dropTarget = group.raw }
                else if dropTarget == group.raw { dropTarget = nil }
            }
        }
    }

    /// A sensitive group stays folded away until she asks for it, with a soft
    /// framing rather than a wall of chips.
    private func sensitiveInvitation(_ group: SymptomGroup) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if let intro = group.intro {
                Text(intro).font(KeelFont.body).foregroundStyle(theme.text.opacity(0.8))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button {
                Haptics.selection()
                withAnimation(.easeOut(duration: 0.2)) { env.settings.showsSensitiveSymptoms = true }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "eye").font(.system(size: 13))
                    Text("Show these").font(KeelFont.body)
                }
                .foregroundStyle(theme.accent)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .overlay(Capsule().stroke(theme.accent.opacity(0.5), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Show intimacy and bladder symptoms")
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    /// A symptom pill. Tap selects (or, in edit mode, opens rename/remove); press
    /// and hold drags it to another group.
    private func chip(_ s: Symptom) -> some View {
        let content: AnyView = editing
            ? AnyView(editChip(s))
            : AnyView(SymptomChip(title: s.name, level: selected[s.id] ?? 0) { toggle(s) })
        return content
            .draggable(s.id.uuidString) {
                Text(s.name).font(KeelFont.body).foregroundStyle(theme.text)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(theme.card).clipShape(Capsule())
                    .overlay(Capsule().stroke(theme.accent, lineWidth: 1.5))
            }
    }

    /// A symptom pill in edit mode: tapping opens a menu to rename (custom only)
    /// or remove it. Moving between groups is drag-and-drop.
    private func editChip(_ s: Symptom) -> some View {
        Menu {
            if s.isCustom {
                Button { startRename(s) } label: { Label("Rename", systemImage: "pencil") }
            }
            Button(role: .destructive) { remove(s) } label: {
                Label("Remove from list", systemImage: "trash")
            }
        } label: {
            HStack(spacing: 6) {
                Text(s.name).font(KeelFont.body)
                Image(systemName: "ellipsis").font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(theme.text)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(theme.card)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(theme.accent.opacity(0.45), lineWidth: 1.5))
        }
        .accessibilityLabel("Options for \(s.name)")
    }

    /// Dashed "+ Add your own" pill that opens the inline composer for this group.
    private func addChip(_ group: SymptomGroup) -> some View {
        Button { startComposing(group.raw) } label: {
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
        .accessibilityLabel("Add your own symptom to \(group.label)")
    }

    /// Inline text field scoped to one group. Adding creates the chip
    /// unselected — she taps it afterwards to include it in the check-in.
    private func composer(_ group: SymptomGroup) -> some View {
        HStack(spacing: 8) {
            TextField("Something else you're noticing…", text: $draftName)
                .font(KeelFont.body).foregroundStyle(theme.text)
                .textInputAutocapitalization(.sentences)
                .autocorrectionDisabled(false)
                .focused($composerFocused)
                .submitLabel(.done)
                .onSubmit { addDraft(toRaw: group.raw) }
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(theme.inputBackground).clipShape(Capsule())
            Button { addDraft(toRaw: group.raw) } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(canAddDraft ? theme.accent : theme.muted.opacity(0.4))
            }
            .disabled(!canAddDraft)
            .accessibilityLabel("Add symptom")
            Button { cancelComposing() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(theme.muted).frame(width: 30, height: 30)
            }
            .accessibilityLabel("Cancel")
        }
        .padding(.top, 2)
    }

    // MARK: Grouping

    struct SymptomGroup {
        let raw: String
        let label: String
        let items: [Symptom]
        let isSensitive: Bool
        let intro: String?
    }

    /// Symptoms grouped by their raw category string. The groups are the built-in
    /// ones, always shown so each stays a drop target and can be added to. A raw
    /// value from older data that no longer matches a group still renders under
    /// its own name, so nothing she logged goes missing.
    private var groups: [SymptomGroup] {
        let byRaw = Dictionary(grouping: symptoms, by: \.categoryRaw)
        var raws = Set(byRaw.keys)
        raws.formUnion(SymptomCategory.allCases.map(\.rawValue))
        return raws.sorted { a, b in
            let sa = SymptomCategory.sortOrder(forRaw: a), sb = SymptomCategory.sortOrder(forRaw: b)
            return sa == sb ? a < b : sa < sb
        }.map { raw in
            let category = SymptomCategory(rawValue: raw)
            let items = (byRaw[raw] ?? []).sorted {
                let a = SymptomCatalog.rank(of: $0.name), b = SymptomCatalog.rank(of: $1.name)
                return a == b ? $0.name < $1.name : a < b
            }
            return SymptomGroup(
                raw: raw,
                label: SymptomCategory.label(forRaw: raw),
                items: items,
                isSensitive: category?.isSensitive ?? false,
                intro: category?.intro
            )
        }
    }

    // MARK: Logic

    private func toggle(_ s: Symptom) {
        Haptics.selection()
        let next = SymptomSeverity.nextLevel(after: selected[s.id] ?? 0)
        if next == 0 { selected[s.id] = nil } else { selected[s.id] = next }
    }

    private var canAddDraft: Bool {
        !draftName.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func startComposing(_ raw: String) {
        Haptics.selection()
        draftName = ""
        withAnimation(.easeOut(duration: 0.18)) { composingCategory = raw }
        composerFocused = true
    }

    private func addDraft(toRaw raw: String) {
        let name = draftName.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        // Create it in the chosen group, but never auto-select — she taps the new
        // chip to include it. Keep the composer open for rapid multi-add.
        _ = env.symptoms.findOrCreateCustom(name: name, categoryRaw: raw)
        Haptics.success()
        draftName = ""
        composerFocused = true
    }

    private func cancelComposing() {
        draftName = ""
        composerFocused = false
        withAnimation(.easeOut(duration: 0.18)) { composingCategory = nil }
    }

    // MARK: Edit mode

    /// Drop handler: move the dragged symptom(s) into this group.
    private func moveDropped(_ ids: [String], toRaw raw: String) -> Bool {
        var moved = false
        for idString in ids {
            guard let id = UUID(uuidString: idString),
                  let s = symptoms.first(where: { $0.id == id }), s.categoryRaw != raw else { continue }
            s.categoryRaw = raw
            s.touch()
            moved = true
        }
        if moved { try? env.context.save(); Haptics.success() }
        dropTarget = nil
        return moved
    }

    /// Remove from the picker via archive (keeps history, reversible later).
    private func remove(_ s: Symptom) {
        s.isArchived = true
        s.touch()
        selected[s.id] = nil
        try? env.context.save()
        Haptics.success()
    }

    private func startRename(_ s: Symptom) {
        renameText = s.name
        renaming = s
    }

    private func commitRename() {
        guard let s = renaming else { renaming = nil; return }
        let name = renameText.trimmingCharacters(in: .whitespaces)
        if !name.isEmpty {
            s.name = name
            s.touch()
            try? env.context.save()
        }
        renaming = nil
    }
}
