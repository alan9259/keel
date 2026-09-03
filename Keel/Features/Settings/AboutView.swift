import SwiftUI

struct AboutView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private let tagline = "Find your even keel, whatever that means to you."

    // Opening (no header). The first line is the hook and is given a little more weight.
    private let opening = [
        "You're not imagining it.",
        "Perimenopause and menopause affect millions of women, yet for too long the experience has been minimised, misunderstood or simply not talked about.",
        "The symptoms are real. The uncertainty can be too.",
        "Keel was built to change that: a calm, grounded companion for the years when your body can stop feeling familiar.",
    ]

    private let sections: [(String, [String])] = [
        ("What we do", [
            "Keel helps you track mood, energy, symptoms, sleep, cycle and medications, building a picture over time instead of turning your body into a data project.",
            "When you next see your GP, Keel turns that picture into a summary you can take with you, so you walk in ready to use the time you've got.",
        ]),
        ("Why we do it", [
            "Because the years of perimenopause and menopause deserve more than a pamphlet and a pat on the back.",
            "You deserve to understand what's changing, recognise your own patterns, and feel better equipped to ask questions and advocate for yourself.",
        ]),
        ("Built from lived experience", [
            "I built Keel while navigating perimenopause myself, and wishing I'd had something like this when the changes first began. Something to help connect the dots and make conversations with healthcare professionals a little easier.",
        ]),
        ("What Keel isn't", [
            "Keel notices patterns and helps you describe them. It doesn't diagnose, prescribe or replace your doctor. That's deliberate.",
            "Keel helps you make sense of what you're experiencing and prepare for conversations with the healthcare professionals who can help you decide what happens next.",
        ]),
    ]

    private let closing = "That's what Keel is here to be: a calm companion to help you find your even keel, whatever that means to you."

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "About Keel", titleSize: 28) { dismiss() }

                Text(tagline)
                    .font(KeelFont.serif(20, weight: .medium)).foregroundStyle(theme.heading).lineSpacing(3)

                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(opening.enumerated()), id: \.offset) { index, para in
                        Text(para)
                            .font(KeelFont.bodyLarge)
                            .foregroundStyle(theme.text.opacity(index == 0 ? 0.95 : 0.75))
                            .lineSpacing(4)
                    }
                }

                ForEach(sections, id: \.0) { section in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(section.0).font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                        ForEach(section.1, id: \.self) { para in
                            Text(para).font(KeelFont.bodyLarge).foregroundStyle(theme.text.opacity(0.75)).lineSpacing(4)
                        }
                    }
                }

                Text(closing)
                    .font(KeelFont.serif(17, weight: .regular)).foregroundStyle(theme.text.opacity(0.9)).lineSpacing(4)
                    .padding(.top, 4)

                footer
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Keel · Version \(DeviceContext.shortVersion)")
                Spacer()
                Text("© 2026 TRY Keel Pty Ltd")
            }
            Text("Emoji artwork © Twemoji, licensed under CC-BY 4.0")
            NavigationLink(value: MainRoute.privacy) {
                Text("Privacy Policy").foregroundStyle(theme.accent)
            }
            .padding(.top, 2)
        }
        .font(KeelFont.caption).foregroundStyle(theme.muted)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 12)
        .overlay(Divider().background(theme.border), alignment: .top)
        .padding(.top, 12)
    }
}
