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

    /// Names the local emergency number when the region is unambiguous (AU or NZ);
    /// stays generic when both regions are shown, so no wrong number is implied.
    private var footerText: String {
        let base = "Keel is a companion, not a crisis service. If you or someone else is in immediate danger, "
        if regions.count == 1 {
            return base + "call \(regions[0].emergency) or go to your nearest emergency department."
        }
        return base + "call your local emergency number or go to your nearest emergency department."
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Get support",
                             subtitle: "You don't have to handle this alone. Free, confidential support is available.") { dismiss() }

                ForEach(regions) { region in
                    // A country label only when more than one region shows (the
                    // non-AU/NZ fallback), so a single-region user sees a clean card.
                    if regions.count > 1 {
                        Text(region.name).keelEyebrow().padding(.top, 4)
                    }
                    regionCard(region)
                }

                Text(footerText)
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
                    note: "If you or someone else is in immediate danger",
                    tint: theme.accent, emphasised: true)
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
