import SwiftUI

struct ConnectView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var showNoMailApp = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Connect with Us", titleSize: 28, subtitle: "We'd love to hear from you") { dismiss() }

                Text("Keel is built by a very small team who care deeply about this community. Follow along for updates, or just say hello.")
                    .font(KeelFont.bodyLarge).foregroundStyle(theme.text.opacity(0.7)).lineSpacing(4)

                VStack(spacing: 12) {
                    socialCard(logo: "SocialInstagram", name: "Instagram", handle: "@keelperiapp",
                               blurb: "Gentle reminders, tips and a glimpse behind the product.",
                               url: KeelLinks.instagram)
                    socialCard(logo: "SocialFacebook", name: "Facebook", handle: "@KeelPeriApp",
                               blurb: "Updates, stories and a look at what we're building.",
                               url: KeelLinks.facebook)
                }

                Button(action: emailUs) {
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Get in touch directly").font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                            Text("\(Text("For support, feedback or anything else, reach us at ").foregroundColor(theme.muted))\(Text(FeedbackMail.address).foregroundColor(theme.accent))")
                                .font(KeelFont.body).lineSpacing(3)
                                .multilineTextAlignment(.leading)
                            Text("We read every message and reply as soon as we can.")
                                .font(KeelFont.caption).foregroundStyle(theme.muted)
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "envelope").font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(theme.accent)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(18)
                    .background(theme.card)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
                    .contentShape(Rectangle())   // the whole card is the tap target
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Email us at \(FeedbackMail.address)")
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .alert("No email app set up", isPresented: $showNoMailApp) {
            Button("Copy email address") { UIPasteboard.general.string = FeedbackMail.address }
            Button("OK", role: .cancel) {}
        } message: {
            Text("We couldn't find an email app to open. You can copy our address and write to us from anywhere: \(FeedbackMail.address)")
        }
    }

    /// One social account. The whole card opens it (the app if installed, else Safari).
    /// The logo is the brand's official single-colour glyph (black, or white in dark
    /// mode), as its guidelines allow; it isn't recoloured to the theme.
    private func socialCard(logo: String, name: String, handle: String, blurb: String, url: URL) -> some View {
        Button { Haptics.light(); openURL(url) } label: {
            HStack(spacing: 14) {
                Image(logo)
                    .resizable().scaledToFit()
                    .frame(width: 26, height: 26)
                    .frame(width: 48, height: 48)
                    .background(theme.background)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                    Text(handle).font(KeelFont.body).foregroundStyle(theme.accent)
                    Text(blurb).font(KeelFont.caption).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: "arrow.up.right").font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(theme.muted)
            }
            .padding(16)
            .background(theme.card)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
            .contentShape(Rectangle())   // the whole card is the tap target
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(name), \(handle)")
        .accessibilityHint("Opens \(name)")
    }

    /// Email us from her own mail app; if she has none set up, the address to copy.
    private func emailUs() {
        guard let url = FeedbackMail.url(kind: .support, version: DeviceContext.appVersion,
                                         os: DeviceContext.osVersion, device: DeviceContext.deviceModel) else {
            showNoMailApp = true
            return
        }
        openURL(url) { accepted in
            if !accepted { showNoMailApp = true }
        }
    }
}
