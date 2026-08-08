import SwiftUI
import SwiftData

/// What she's taking, in two plain groups: prescribed treatments and her own
/// supplement stack. Keel records what she tells us. It never suggests a
/// treatment, never implies one product is better than another, and never infers
/// what she should be on.
struct MedicationsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @Query(filter: #Predicate<Medication> { $0.deletedAt == nil && $0.isActive == true }, sort: \Medication.createdAt)
    private var medications: [Medication]

    @State private var showAdd = false
    @State private var editing: Medication?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // The longest title in the app: held to one line, scaling to fit.
                ScreenHeader(title: "Medications & Supplements") { dismiss() }

                if medications.isEmpty {
                    emptyState
                } else {
                    ForEach(TreatmentKind.allCases) { kind in
                        let items = medications.filter { $0.kind == kind }
                        if !items.isEmpty {
                            section(kind.label, items: items)
                        }
                    }
                }

                KeelPrimaryButton("Add Medication or Supplement", systemImage: "plus") { showAdd = true }

                InfoNoteCard(lead: "The tick:",
                             message: "Ticked medicines show in your home Medicines log, where you record each day whether you took them.")

                InfoNoteCard(lead: "Reminders:",
                             message: "Add a time to anything here and we'll send a gentle nudge on the days it's due. Leave it off and nothing will chase you.")
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .sheet(isPresented: $showAdd) {
            TreatmentPickerSheet { draft in
                let med = env.medications.add(draft)
                reschedule(med)
                env.autoLogTodaysDoses() // if she added it with auto-log on, tick today now
                env.requestSync()
            }
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $editing) { med in
            TreatmentDetailForm(
                draft: TreatmentDraft(med),
                title: "Edit",
                onRemove: {
                    let id = med.id
                    env.medications.archive(med)
                    Task { await env.notifications.cancelMedicationReminders(medicationID: id) }
                    editing = nil
                    env.requestSync()
                },
                onCancel: { editing = nil },
                onSave: { updated in
                    env.medications.update(med, with: updated)
                    reschedule(med)
                    env.autoLogTodaysDoses() // enabling auto-log ticks today's log straight away
                    editing = nil
                    env.requestSync()
                }
            )
            .presentationDragIndicator(.visible)
        }
        #if DEBUG
        .onAppear { if DebugHarness.showTreatmentPicker { showAdd = true } }
        #endif
    }

    private func section(_ title: String, items: [Medication]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).keelEyebrow()
            ForEach(items) { med in row(med) }
        }
    }

    private func row(_ med: Medication) -> some View {
        HStack(spacing: Spacing.md) {
            Button { editing = med } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text(med.name).font(KeelFont.serif(17, weight: .semibold)).foregroundStyle(theme.heading)
                        .multilineTextAlignment(.leading)
                    if !med.detailLine.isEmpty {
                        Text(med.detailLine).font(KeelFont.caption).foregroundStyle(theme.muted)
                            .multilineTextAlignment(.leading)
                    }
                    if med.autoLogDoses {
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark.circle.fill").font(.system(size: 10))
                            Text("Auto-logged").font(KeelFont.sans(11, weight: .medium))
                        }
                        .foregroundStyle(theme.sage)
                        .padding(.top, 2)
                        .accessibilityLabel("Doses are marked taken automatically")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(med.name), edit")
            // The tick decides whether this medicine shows in the home Medicines
            // log, where she records each day whether she took it. Not a dose tick.
            trackToggle(med)
        }
        .padding(20)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
        .contextMenu {
            Button { editing = med } label: { Label("Edit", systemImage: "pencil") }
            Button(role: .destructive) { remove(med) } label: { Label("Remove", systemImage: "trash") }
        }
    }

    /// Remove a treatment/supplement: archives it (past logs keep their history)
    /// and cancels any of its reminders.
    private func remove(_ med: Medication) {
        let id = med.id
        env.medications.archive(med)
        Task { await env.notifications.cancelMedicationReminders(medicationID: id) }
        env.requestSync()
        Haptics.success()
    }

    /// The tick that includes this medicine in the home Medicines log. Filled
    /// means tracked (it shows in the log, where she records each day's dose);
    /// empty means it stays off the log. This is not a "taken today" tick.
    private func trackToggle(_ med: Medication) -> some View {
        Button {
            env.medications.setTracked(med, !med.isTracked)
            env.requestSync()
            Haptics.selection()
        } label: {
            ZStack {
                Circle().fill(med.isTracked ? theme.sage : .clear).frame(width: 44, height: 44)
                Circle().strokeBorder(med.isTracked ? .clear : theme.border, lineWidth: 2)
                    .frame(width: 44, height: 44)
                if med.isTracked {
                    Image(systemName: "checkmark").font(.system(size: 18, weight: .bold)).foregroundStyle(.white)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(med.name), show in the home Medicines log")
        .accessibilityValue(med.isTracked ? "Tracked" : "Not tracked")
    }

    private func reschedule(_ med: Medication) {
        let (id, name, schedule) = (med.id, med.name, med.schedule)
        let horizon = NotificationService.cycleHorizon(activeMedications: medications.count)
        Task {
            await env.notifications.rescheduleMedication(id: id, name: name, schedule: schedule,
                                                         cycleHorizon: horizon)
        }
    }

    private var emptyState: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "pills").font(.system(size: 40)).foregroundStyle(theme.sage)
            Text("Nothing here yet").font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
            Text("Add what you're taking, prescribed or otherwise, to keep track of it alongside how you're feeling.")
                .font(KeelFont.body).foregroundStyle(theme.muted).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.vertical, Spacing.xl)
    }

}
