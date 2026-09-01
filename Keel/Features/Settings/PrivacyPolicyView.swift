import SwiftUI

/// In-app privacy policy. Renders `PrivacyPolicyContent.markdown` verbatim so the app
/// no longer depends on an external page that may not be live. The body is reconciled
/// to the shipping build (on-device AI, iCloud/CloudKit) with every placeholder filled;
/// keep it verbatim, as legal text I must not fabricate or rewrite.
struct PrivacyPolicyView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ScreenHeader(title: "Privacy Policy", titleSize: 28) { dismiss() }
                    .padding(.bottom, 4)

                ForEach(Array(PrivacyPolicyContent.lines.enumerated()), id: \.offset) { _, line in
                    row(for: line)
                }
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    @ViewBuilder
    private func row(for line: String) -> some View {
        if line.isEmpty {
            Color.clear.frame(height: 4)
        } else if line.hasPrefix("### ") {
            Text(String(line.dropFirst(4)))
                .font(KeelFont.serif(18, weight: .semibold)).foregroundStyle(theme.heading)
                .padding(.top, 8)
        } else if line.hasPrefix("## ") {
            Text(String(line.dropFirst(3)))
                .font(KeelFont.serif(22, weight: .semibold)).foregroundStyle(theme.heading)
                .padding(.top, 10)
        } else if line.hasPrefix("- ") {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\u{2022}").foregroundStyle(theme.accent)
                Text(inline(String(line.dropFirst(2))))
                    .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.leading, 4)
        } else {
            Text(inline(line))
                .font(KeelFont.body).foregroundStyle(theme.text.opacity(0.85)).lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Inline markdown (**bold**) within a single line; plain text on failure.
    /// SwiftUI won't synthesise a bold weight for the custom body font, so emphasised
    /// runs are given the medium Poppins face explicitly.
    private func inline(_ text: String) -> AttributedString {
        var attr = (try? AttributedString(
            markdown: text,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        )) ?? AttributedString(text)
        let boldRanges = attr.runs
            .filter { $0.inlinePresentationIntent?.contains(.stronglyEmphasized) == true }
            .map(\.range)
        for range in boldRanges { attr[range].font = KeelFont.sans(15, weight: .semibold) }
        return attr
    }
}
