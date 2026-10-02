import SwiftUI

/// Settings > My background (submission pack 3C). Personal background she can add when
/// she chooses, not during onboarding: the hysterectomy question (with an optional
/// year) and, when periods no longer apply, her note. Keel records these and changes
/// nothing because of them. They reach the GP visit summary only as described there
/// (the hysterectomy answer only if she turns it on).
struct MyBackgroundView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var answer: Hysterectomy?
    @State private var yearText = ""
    @State private var periodsNote = ""
    @State private var loaded = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ScreenHeader(title: "My background", subtitle: "For your own record") { dismiss() }

                Text("Answer only if you'd like to. Keel keeps this for your record and changes nothing because of it.")
                    .font(KeelFont.body).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)

                hysterectomyCard
                periodsCard
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .onAppear(perform: load)
    }

    // MARK: Hysterectomy

    private var hysterectomyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(Hysterectomy.question)
                .font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            VStack(spacing: 0) {
                ForEach(Hysterectomy.allCases) { option in
                    optionRow(option)
                    if option != Hysterectomy.allCases.last { Divider().background(theme.border) }
                }
            }
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))

            if answer?.allowsYear == true {
                KeelTextField(label: "Year (if you know it)", placeholder: "e.g. 2019",
                              text: $yearText, keyboard: .numberPad)
                    .onChange(of: yearText) { _, _ in saveAnswer() }
            }
            if answer == .yesNotSureAboutOvaries {
                Text("Not being sure is common. It can be a useful question to take to your GP.")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 4)
            }
            Text("It goes on your GP visit summary only if you turn it on when you make one.")
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
        }
    }

    private func optionRow(_ option: Hysterectomy) -> some View {
        let selected = answer == option
        return Button {
            Haptics.selection()
            // Tapping her current answer again clears it (she can always leave it blank).
            answer = selected ? nil : option
            saveAnswer()
        } label: {
            HStack(spacing: 12) {
                Text(option.label).font(KeelFont.body).foregroundStyle(theme.text)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 8)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20))
                    .foregroundStyle(selected ? theme.accent : theme.border)
            }
            .padding(14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    // MARK: Periods

    private var periodsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Periods").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
            KeelTextField(label: "Periods no longer apply (optional)",
                          placeholder: "e.g. after menopause or a hysterectomy",
                          text: $periodsNote, autocapitalization: .sentences)
                .onChange(of: periodsNote) { _, new in
                    guard loaded else { return }
                    env.users.setPeriodsNotApplicableReason(new)
                }
            Text("If you fill this in, your GP visit summary notes it instead of period details.")
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
        }
    }

    // MARK: Store

    private func load() {
        guard !loaded else { return }
        let profile = env.users.currentProfile()
        answer = profile?.hysterectomy
        yearText = profile?.hysterectomyYear.map(String.init) ?? ""
        periodsNote = profile?.periodsNotApplicableReason ?? ""
        loaded = true
    }

    private func saveAnswer() {
        guard loaded else { return }
        env.users.setHysterectomy(answer, year: Hysterectomy.validYear(yearText))
    }
}
