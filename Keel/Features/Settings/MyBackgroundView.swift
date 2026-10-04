import SwiftUI

/// Profile > My background (submission pack 3C). Personal background she can add when
/// she chooses, not during onboarding: the hysterectomy question (with an optional
/// year) and, when periods no longer apply, her note. Keel records these and changes
/// nothing because of them. They reach the GP visit summary only as described there
/// (the hysterectomy answer only if she turns it on).
///
/// Shown inside Profile, above "Save changes", and saved with her details by that
/// button (`ProfileView.save`), so the fields only bind to the profile form's state.
struct MyBackgroundFields: View {
    @Environment(\.keelTheme) private var theme

    @Binding var answer: Hysterectomy?
    @Binding var yearText: String
    @Binding var periodsNote: String

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            VStack(alignment: .leading, spacing: 6) {
                Text("My background").font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
                Text("Answer only if you'd like to. Keel keeps this for your record and changes nothing because of it.")
                    .font(KeelFont.body).foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            hysterectomyCard
            periodsCard
        }
    }

    // MARK: Hysterectomy

    private var hysterectomyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(Hysterectomy.question)
                .font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.text)
                .fixedSize(horizontal: false, vertical: true)
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
            Text("Periods").font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.text)
            KeelTextField(label: "Periods no longer apply (optional)",
                          placeholder: "e.g. after menopause or a hysterectomy",
                          text: $periodsNote, autocapitalization: .sentences)
            Text("If you fill this in, your GP visit summary notes it instead of period details.")
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
        }
    }
}
