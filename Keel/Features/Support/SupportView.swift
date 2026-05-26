import SwiftUI

/// Always-available crisis and support lines, matched to her region (AU/NZ, or
/// both when the region is neither). Reads `CrisisResources`, the same source the
/// companion prompt uses, so the app and the model never disagree on a number.
/// Reachable from the companion chat and from More, per the safety design.
struct SupportView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    private var regions: [SupportRegion] { CrisisResources.matching() }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Get support",
                             subtitle: "You are not alone. These lines are free, confidential, and there for you.") { dismiss() }

                ForEach(regions) { region in
                    regionCard(region)
                }

                Text("Keel is a companion, not a crisis service. If you or someone else is in immediate danger, please call your local emergency number now.")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity).multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)
            }
            .padding(.horizontal, Spacing.screenH).padding(.vertical, Spacing.md)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    private func regionCard(_ region: SupportRegion) -> some View {
        VStack(spacing: 0) {
            callRow(name: "Emergency", contact: region.emergency,
                    note: region.name, tint: theme.accent, emphasised: true)
            ForEach(region.contacts) { contact in
                Divider().background(theme.border)
                callRow(name: contact.name, contact: contact.contact,
                        note: contact.note, tint: theme.sage, emphasised: false)
            }
        }
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func callRow(name: String, contact: String, note: String?, tint: Color, emphasised: Bool) -> some View {
        Button {
            call(contact)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: emphasised ? "phone.fill" : "phone")
                    .font(.system(size: 15, weight: .semibold)).foregroundStyle(tint)
                    .frame(width: 38, height: 38)
                    .background(tint.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                    if let note { Text(note).font(KeelFont.caption).foregroundStyle(theme.muted) }
                }
                Spacer(minLength: 8)
                Text(contact).font(KeelFont.sans(15, weight: .semibold)).foregroundStyle(tint)
            }
            .padding(.horizontal, 16).padding(.vertical, 13)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Call \(name), \(contact)")
    }

    private func call(_ contact: String) {
        let digits = contact.filter { $0.isNumber || $0 == "+" }
        guard !digits.isEmpty, let url = URL(string: "tel://\(digits)") else { return }
        openURL(url)
        Haptics.light()
    }
}
