import SwiftUI
import UIKit

struct ConnectView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    /// Shown only if opening a draft failed (no mail app set up), so she can still
    /// reach us by copying the address.
    @State private var showNoMailApp = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "Connect with Us", titleSize: 28, subtitle: "We'd love to hear from you") { dismiss() }

                Text("Keel is built by a very small team who care deeply about this community. Follow along for updates, or just say hello.")
                    .font(KeelFont.bodyLarge).foregroundStyle(theme.text.opacity(0.7)).lineSpacing(4)

                VStack(spacing: 12) {
                    socialCard(color: Color(hex: 0xEC4899), emoji: "📷", name: "Instagram", handle: "@keel.app",
                               blurb: "Gentle reminders, tips and a glimpse behind the product.")
                    socialCard(color: Color(hex: 0x2563EB), emoji: "📘", name: "Facebook", handle: "@keelapp",
                               blurb: "Updates, stories and a look at what we're building.")
                }

                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Get in touch directly").font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                        Text("Have an idea, or something that isn't working? Send it straight to us. It opens in your own email app, and you can change anything before it sends.")
                            .font(KeelFont.body).foregroundStyle(theme.muted).lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    KeelPrimaryButton("Share feedback", systemImage: "envelope.fill") {
                        compose(.feedback)
                    }
                    KeelSecondaryButton("Request a feature", systemImage: "lightbulb") {
                        compose(.featureRequest)
                    }

                    Text("\(Text("Or reach us any time at ").foregroundColor(theme.muted))\(Text(FeedbackMail.address).foregroundColor(theme.accent))")
                        .font(KeelFont.caption).lineSpacing(2)
                    Text("We read every message and reply as soon as we can.")
                        .font(KeelFont.caption).foregroundStyle(theme.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .background(theme.card)
                .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
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

    /// Open a pre-filled draft in her default mail app. If nothing handles it
    /// (no mail account configured), fall back to offering the address to copy.
    private func compose(_ kind: FeedbackMail.Kind) {
        guard let url = FeedbackMail.url(kind: kind,
                                         version: DeviceContext.appVersion,
                                         os: DeviceContext.osVersion,
                                         device: DeviceContext.deviceModel) else {
            showNoMailApp = true
            return
        }
        openURL(url) { accepted in
            if !accepted { showNoMailApp = true }
        }
    }

    private func socialCard(color: Color, emoji: String, name: String, handle: String, blurb: String) -> some View {
        HStack(spacing: 14) {
            EmojiGlyph(emoji: emoji, size: 20)
                .frame(width: 48, height: 48)
                .background(color).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                Text(handle).font(KeelFont.body).foregroundStyle(theme.accent)
                Text(blurb).font(KeelFont.caption).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }
}
