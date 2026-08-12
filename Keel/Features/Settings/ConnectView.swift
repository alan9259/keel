import SwiftUI

struct ConnectView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

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

                VStack(alignment: .leading, spacing: 8) {
                    Text("Get in touch directly").font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                    Text("\(Text("For support, feedback or anything else, reach us at ").foregroundColor(theme.muted))\(Text("keel@therecalibrationyears.com").foregroundColor(theme.accent))")
                        .font(KeelFont.body).lineSpacing(3)
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
