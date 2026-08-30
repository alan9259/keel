import SwiftUI
import PDFKit

/// The GP Visit Summary flow: choose a period, review what she recorded (remove any
/// row), add what matters to her, preview the exact PDF, then generate and share it.
/// The preview shows the very PDF that is shared, so the two never drift.
struct GPSummaryFlowView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var model: GPSummaryFlowModel?
    @State private var shareItem: ShareItem?

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            if let model {
                flow(model)
            } else {
                ProgressView().tint(theme.accent)
            }
        }
        .task { if model == nil { model = GPSummaryFlowModel(service: env.gpSummary) } }
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $shareItem, onDismiss: {
            // Temp file lives only for the share; remove it once the sheet closes.
            if let url = shareItem?.url { try? FileManager.default.removeItem(at: url) }
            shareItem = nil
        }) { item in
            ShareSheet(items: [item.url])
        }
    }

    @ViewBuilder
    private func flow(_ model: GPSummaryFlowModel) -> some View {
        VStack(spacing: 0) {
            ScreenHeader(title: title(model.step), subtitle: subtitle(model.step)) {
                if model.isFirst { dismiss() } else { model.back() }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch model.step {
                    case .period: GPPeriodStepView(model: model)
                    case .review: GPReviewStepView(model: model)
                    case .details: GPDetailsStepView(model: model)
                    case .preview: GPPreviewStepView(model: model)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }

            bottomBar(model)
        }
    }

    private func bottomBar(_ model: GPSummaryFlowModel) -> some View {
        VStack(spacing: 0) {
            Rectangle().fill(theme.border).frame(height: 1)
            KeelPrimaryButton(model.isLast ? "Generate and share" : "Continue",
                              systemImage: model.isLast ? "square.and.arrow.up" : nil) {
                if model.isLast {
                    let url = GPSummaryPDFRenderer(document: model.document()).writeTemporaryFile()
                    shareItem = ShareItem(url: url)
                } else {
                    model.next()
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 8)
        }
        .background(theme.background)
    }

    private func title(_ step: GPSummaryFlowModel.Step) -> String {
        switch step {
        case .period: "Choose a time period"
        case .review: "Review what you recorded"
        case .details: "Add what matters to you"
        case .preview: "Preview"
        }
    }

    private func subtitle(_ step: GPSummaryFlowModel.Step) -> String? {
        switch step {
        case .period: "The summary covers this window of your records."
        case .review: "Remove anything you would rather leave off."
        case .details: "Optional. All in your own words."
        case .preview: "This is exactly what will be shared."
        }
    }
}

/// Wraps the temp PDF URL so the share sheet presents with fresh identity.
private struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
}

// MARK: - Step 1: period

private struct GPPeriodStepView: View {
    @Bindable var model: GPSummaryFlowModel
    @Environment(\.keelTheme) private var theme

    var body: some View {
        StandardCard {
            VStack(spacing: 0) {
                ForEach(Array(GPPeriod.presets.enumerated()), id: \.offset) { index, period in
                    optionRow(period.label, selected: model.inputs.period == period) {
                        model.selectPreset(period)
                    }
                    Divider().overlay(theme.border)
                }
                optionRow("Custom range", selected: model.isCustom) { model.setCustomRange() }
            }
        }

        if model.isCustom {
            StandardCard {
                VStack(alignment: .leading, spacing: 14) {
                    DatePicker("From", selection: $model.customStart, in: ...model.customEnd, displayedComponents: .date)
                    DatePicker("To", selection: $model.customEnd, in: model.customStart...Date(), displayedComponents: .date)
                }
                .font(KeelFont.body)
                .foregroundStyle(theme.text)
                .tint(theme.accent)
                .onChange(of: model.customStart) { model.setCustomRange() }
                .onChange(of: model.customEnd) { model.setCustomRange() }
            }
        }
    }

    private func optionRow(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: { Haptics.selection(); action() }) {
            HStack {
                Text(label).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                Spacer()
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(selected ? theme.accent : theme.muted)
                    .font(.system(size: 20))
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Step 2: review

private struct GPReviewStepView: View {
    @Bindable var model: GPSummaryFlowModel
    @Environment(\.keelTheme) private var theme

    var body: some View {
        // Symptoms.
        sectionHeader(GPSummaryCopy.symptomsHeading)
        if model.candidateSymptoms.isEmpty {
            StandardCard { Text(GPSummaryCopy.noSymptoms).font(KeelFont.body).foregroundStyle(theme.muted) }
        } else {
            StandardCard {
                VStack(spacing: 0) {
                    ForEach(Array(model.candidateSymptoms.enumerated()), id: \.element.name) { index, stat in
                        row(stat.name, detail: "\(stat.daysThisPeriod) days",
                            included: model.isSymptomIncluded(stat.name)) { model.toggleSymptom(stat.name) }
                        if index < model.candidateSymptoms.count - 1 { Divider().overlay(theme.border) }
                    }
                }
            }
        }

        // Treatments.
        if !model.reviewMeds.isEmpty {
            sectionHeader("Treatments and supplements")
            StandardCard {
                VStack(spacing: 0) {
                    ForEach(Array(model.reviewMeds.enumerated()), id: \.element.id) { index, med in
                        row(med.name, detail: categoryLabel(med.category),
                            included: model.isMedIncluded(med.id)) { model.toggleMed(med.id) }
                        if index < model.reviewMeds.count - 1 { Divider().overlay(theme.border) }
                    }
                }
            }
        }

        CalloutCard(systemImage: "info.circle.fill") {
            Text("Your period dates, check-in counts, cycle details and sleep, energy and mood are always included.")
                .font(KeelFont.caption).foregroundStyle(theme.text)
        }
    }

    private func sectionHeader(_ text: String) -> some View {
        Text(text).font(KeelFont.serif(17, weight: .medium)).foregroundStyle(theme.accent)
            .padding(.top, 4)
    }

    private func categoryLabel(_ c: GPMedCategory) -> String {
        switch c {
        case .mht: "Hormonal treatment"
        case .otherPrescribed: "Prescription"
        case .supplement: "Supplement"
        }
    }

    private func row(_ name: String, detail: String, included: Bool, toggle: @escaping () -> Void) -> some View {
        Button(action: { Haptics.selection(); toggle() }) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).font(KeelFont.body).foregroundStyle(included ? theme.text : theme.muted)
                        .strikethrough(!included, color: theme.muted)
                    Text(detail).font(KeelFont.caption).foregroundStyle(theme.muted)
                }
                Spacer()
                Image(systemName: included ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(included ? theme.accent : theme.muted)
                    .font(.system(size: 22))
            }
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(name)
        .accessibilityValue(included ? "Included" : "Removed")
    }
}

// MARK: - Step 3: details

private struct GPDetailsStepView: View {
    @Bindable var model: GPSummaryFlowModel
    @Environment(\.keelTheme) private var theme

    var body: some View {
        // Priorities.
        block(GPSummaryCopy.prioritiesHeading, prompt: GPSummaryCopy.prioritiesPrompt) {
            ForEach(0..<GPSummaryCopy.maxPriorities, id: \.self) { i in
                limitedField(placeholder: "Priority \(i + 1)", text: $model.priorityDrafts[i],
                             limit: GPSummaryCopy.priorityCharLimit)
            }
        }

        // Impact.
        block(GPSummaryCopy.impactHeading, prompt: nil) {
            FlowLayout(spacing: 8) {
                ForEach(GPSummaryCopy.impactAreaOptions, id: \.self) { area in
                    chip(area, selected: model.isAreaSelected(area)) { model.toggleArea(area) }
                }
            }
            limitedField(placeholder: "Something else (optional)", text: $model.inputs.impactOther,
                         limit: GPSummaryCopy.impactOtherCharLimit)
            Text("Overall impact").font(KeelFont.caption).foregroundStyle(theme.muted).padding(.top, 2)
            HStack(spacing: 8) {
                ForEach(GPSummaryCopy.impactLevelOptions, id: \.self) { level in
                    chip(level, selected: model.inputs.impactOverall == level) {
                        model.inputs.impactOverall = model.inputs.impactOverall == level ? nil : level
                    }
                }
            }
        }

        // Questions.
        block(GPSummaryCopy.questionsHeading, prompt: GPSummaryCopy.questionsPrompt) {
            ForEach(0..<GPSummaryCopy.maxQuestions, id: \.self) { i in
                limitedField(placeholder: "Question \(i + 1)", text: $model.questionDrafts[i],
                             limit: GPSummaryCopy.questionCharLimit)
            }
        }

        // About me opt-in.
        if model.hasName || model.hasAge {
            block(GPSummaryCopy.aboutHeading, prompt: "Off unless you turn them on.") {
                if model.hasName {
                    Toggle("Include my name", isOn: $model.inputs.includeName)
                        .font(KeelFont.body).tint(theme.accent)
                }
                if model.hasAge {
                    Toggle("Include my age", isOn: $model.inputs.includeAge)
                        .font(KeelFont.body).tint(theme.accent)
                }
            }
        }
    }

    @ViewBuilder
    private func block<Content: View>(_ title: String, prompt: String?, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(KeelFont.serif(17, weight: .medium)).foregroundStyle(theme.accent)
            if let prompt { Text(prompt).font(KeelFont.caption).foregroundStyle(theme.muted) }
            content()
        }
        .padding(.top, 4)
    }

    private func limitedField(placeholder: String, text: Binding<String>, limit: Int) -> some View {
        TextField(placeholder, text: Binding(
            get: { text.wrappedValue },
            set: { text.wrappedValue = String($0.prefix(limit)) }
        ), axis: .vertical)
            .font(KeelFont.body)
            .foregroundStyle(theme.text)
            .padding(12)
            .background(theme.inputBackground)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func chip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: { Haptics.selection(); action() }) {
            Text(label).font(KeelFont.body)
                .foregroundStyle(selected ? .white : theme.text.opacity(0.85))
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(selected ? theme.accent : theme.card)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(selected ? Color.clear : theme.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Step 4: preview

private struct GPPreviewStepView: View {
    let model: GPSummaryFlowModel
    @Environment(\.keelTheme) private var theme
    @State private var data: Data?

    var body: some View {
        CalloutCard(systemImage: "hand.raised.fill") {
            Text(GPSummaryCopy.shareWarning).font(KeelFont.caption).foregroundStyle(theme.text)
        }

        Group {
            if let data {
                PDFKitView(data: data)
                    .frame(height: 520)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).stroke(theme.border, lineWidth: 1))
            } else {
                ProgressView().tint(theme.accent).frame(height: 520).frame(maxWidth: .infinity)
            }
        }
        .task { data = model.pdfData() }
    }
}

// MARK: - PDF + share wrappers

private struct PDFKitView: UIViewRepresentable {
    let data: Data
    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.backgroundColor = .clear
        view.document = PDFDocument(data: data)
        return view
    }
    func updateUIView(_ view: PDFView, context: Context) {
        if view.document?.dataRepresentation() != data { view.document = PDFDocument(data: data) }
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
