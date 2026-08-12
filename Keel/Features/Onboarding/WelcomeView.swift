import SwiftUI

struct WelcomeView: View {
    @Environment(\.keelTheme) private var theme
    let onContinue: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            KeelMark()
                .frame(width: 72, height: 72)
                .padding(.bottom, Spacing.md)

            Text("Keel")
                .font(KeelFont.serif(24, weight: .medium))
                .tracking(3)
                .foregroundStyle(theme.heading)

            VStack(spacing: 0) {
                Text("You're not imagining it.")
                    .onboardingTitle()
                    .padding(.top, Spacing.lg)
                Text("Let's understand what's actually going on.")
                    .onboardingSubtitle()
                    .foregroundStyle(theme.heading.opacity(0.85))
                    .padding(.top, Spacing.md)
                Text("A calm companion for the years when your body stops feeling familiar, and you want to understand it again.")
                    .font(KeelFont.body)
                    .foregroundStyle(theme.muted)
                    .lineSpacing(4)
                    .padding(.top, 20)
                Text("Find your even keel, whatever that means to you.")
                    .font(KeelFont.body).italic()
                    .foregroundStyle(theme.muted)
                    .padding(.top, 18)
            }
            .multilineTextAlignment(.center)

            Spacer()

            // One CTA. Sign-in lives once, on the CreateAccount step, so it isn't
            // asked for twice.
            KeelPrimaryButton("Let's begin", action: onContinue)
        }
        .padding(.horizontal, Spacing.screenH)
        .padding(.vertical, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background.ignoresSafeArea())
    }
}
