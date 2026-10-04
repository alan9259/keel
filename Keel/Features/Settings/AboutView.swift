import SwiftUI

/// About Keel. The wording is the owner's (4 Oct 2026); keep it verbatim.
struct AboutView: View {
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var showNoMailApp = false

    // Opening (no header). The first line is the hook and is given a little more weight.
    static let opening = [
        "You're not imagining it.",
        "Perimenopause and menopause affect millions of women, yet for too long the experience has been minimised, misunderstood or simply not talked about.",
        "The symptoms are real. The uncertainty can be too.",
        "Keel was built for this: a calm, grounded companion for the years when your body can stop feeling familiar.",
    ]

    static let sections: [(String, [String])] = [
        ("What we do", [
            "Keel helps you track mood, energy, symptoms, sleep, cycle and medications, building a picture over time instead of turning your body into a data project.",
            "When you next see your GP, Keel turns that picture into a summary you can take with you, so you walk in ready to use the time you've got.",
        ]),
        ("Why we do it", [
            "Because the years of perimenopause and menopause deserve more than a pamphlet and a pat on the back.",
            "You deserve to see what's changing, recognise your own patterns, and feel better equipped to ask questions and advocate for yourself.",
        ]),
        ("Built from lived experience", [
            "I created Keel while navigating perimenopause myself, and wishing I'd had something like this when the changes first began. Something to keep everything in one place and make conversations with healthcare professionals a little easier.",
        ]),
        ("What Keel isn't", [
            "Keel keeps your record and helps you describe it. It doesn't diagnose, prescribe or replace your doctor. That's deliberate.",
            "Keel helps you see what you've been experiencing and prepare for conversations with the healthcare professionals who can help you decide what happens next.",
        ]),
    ]

    static let closing = "That's what Keel is here to be: a calm companion to help you find your even keel, whatever that means to you."

    static var versionLine: String { "Keel · Version \(DeviceContext.shortVersion) · © 2026 TRY Keel Pty Ltd" }
    static let emojiCredit = "Emoji artwork by Twemoji, © Twitter, Inc and other contributors, licensed under CC-BY 4.0"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                ScreenHeader(title: "About Keel", titleSize: 28) { dismiss() }

                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(Self.opening.enumerated()), id: \.offset) { index, para in
                        Text(para)
                            .font(KeelFont.bodyLarge)
                            .foregroundStyle(theme.text.opacity(index == 0 ? 0.95 : 0.75))
                            .lineSpacing(4)
                    }
                }

                ForEach(Self.sections, id: \.0) { section in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(section.0).font(KeelFont.serif(20, weight: .semibold)).foregroundStyle(theme.heading)
                        ForEach(section.1, id: \.self) { para in
                            Text(para).font(KeelFont.bodyLarge).foregroundStyle(theme.text.opacity(0.75)).lineSpacing(4)
                        }
                    }
                }

                Text(Self.closing)
                    .font(KeelFont.serif(17, weight: .regular)).foregroundStyle(theme.text.opacity(0.9)).lineSpacing(4)
                    .padding(.top, 4)

                footer
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

    private var footer: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(Self.versionLine)
            Text(Self.emojiCredit)
            HStack(spacing: 6) {
                NavigationLink(value: MainRoute.privacy) {
                    Text(PrivacySummary.title).foregroundStyle(theme.accent)
                        .frame(minHeight: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Text("·")
                Button(action: emailSupport) {
                    Text("Support").foregroundStyle(theme.accent)
                        .frame(minHeight: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .font(KeelFont.caption).foregroundStyle(theme.muted)
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 12)
        .overlay(Divider().background(theme.border), alignment: .top)
        .padding(.top, 12)
    }

    /// A support email in her default mail app; if she has none set up, the address
    /// to copy (same as Support on Your privacy).
    private func emailSupport() {
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
