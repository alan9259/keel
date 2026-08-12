import SwiftUI

struct AboutView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private let sections: [(String, [String])] = [
        ("You're not imagining it.", [
            "Perimenopause and menopause affect millions of women, yet for too long the experience has been minimised, misunderstood or simply not talked about. The symptoms are real. The confusion is real.",
            "Keel was built to change that: a calm, grounded companion for the years when your body stops feeling familiar.",
        ]),
        ("What we do", [
            "Keel helps you track mood, energy, symptoms, sleep, cycle and medications, not to turn your body into a data project, but to build a picture over time that helps you and your healthcare team understand what's actually going on.",
        ]),
        ("Why we do it", [
            "Because the years of perimenopause and menopause deserve more than a pamphlet and a pat on the back. Every woman navigating this transition deserves a tool that meets her where she is.",
            "You deserve to understand what's changing, recognise your own patterns, and feel better equipped to ask questions and advocate for yourself.",
        ]),
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

                VStack(spacing: 12) {
                    Text("Keel")
                        .font(KeelFont.sans(44, weight: .regular)).tracking(8).textCase(.uppercase)
                        .foregroundStyle(theme.heading.opacity(0.5))
                    Text("Find your even keel, whatever that means to you.")
                        .font(KeelFont.body).italic().foregroundStyle(theme.muted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)

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
                        Text("© 2026 Keel Health Ltd.")
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
