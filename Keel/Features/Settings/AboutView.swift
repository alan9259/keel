import SwiftUI

struct AboutView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private let lead = "You deserve to understand what's changing, recognise your own patterns, and feel better equipped to ask questions and advocate for yourself."

    private let sections: [(String, [String])] = [
        ("Built from lived experience", [
            "I built Keel while navigating perimenopause myself, and wishing I'd had something like this when the changes first began. Something to help connect the dots and make conversations with healthcare professionals a little easier.",
        ]),
        ("What Keel isn't", [
            "Keel notices patterns and helps you describe them. It doesn't diagnose, prescribe or replace your doctor. That's deliberate. The person who can act on what you're seeing is the one sitting across from you at your appointment.",
        ]),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "About Keel", titleSize: 28) { dismiss() }

                Text(lead)
                    .font(KeelFont.bodyLarge).foregroundStyle(theme.text.opacity(0.75)).lineSpacing(4)

                ForEach(sections, id: \.0) { section in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(section.0).font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                        ForEach(section.1, id: \.self) { para in
                            Text(para).font(KeelFont.bodyLarge).foregroundStyle(theme.text.opacity(0.75)).lineSpacing(4)
                        }
                    }
                }

                VStack(spacing: 6) {
                    HStack {
                        Text("Keel · Version \(DeviceContext.shortVersion)")
                        Spacer()
                        Text("© 2026 The Recalibration Years")
                    }
                    Text("Emoji artwork © Twemoji, licensed under CC-BY 4.0")
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .font(KeelFont.caption).foregroundStyle(theme.muted)
                .padding(.top, 12)
                .overlay(Divider().background(theme.border), alignment: .top)
                .padding(.top, 12)
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }
}
