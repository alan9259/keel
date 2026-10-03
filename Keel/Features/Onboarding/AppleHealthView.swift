import SwiftUI

struct AppleHealthView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    let onContinue: () -> Void
    @State private var connecting = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: Spacing.xl)

            // Icon pair — partnership
            HStack(spacing: Spacing.md) {
                iconTile(gradient: [theme.plum, theme.plum]) {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 32))
                        .foregroundStyle(theme.onFill)
                }
                Image(systemName: "plus")
                    .font(.system(size: 24))
                    .foregroundStyle(theme.muted)
                // The v2 brand mark, matching the Health tile's size and corners.
                KeelMark(cornerRadius: Radius.lg)
                    .frame(width: 64, height: 64)
            }

            VStack(spacing: Spacing.lg) {
                Text("Bring in what you already record.")
                    .onboardingTitle()
                    .padding(.top, Spacing.xl)

                Text("Keel can read your sleep, activity and vitals from Apple Health, so there is less for you to enter by hand. Keel shows these alongside your own record and labels where each one came from. Keel only ever reads. It never writes anything back.")
                    .onboardingSubtitle()
                    .foregroundStyle(theme.text.opacity(0.8))
            }

            Spacer()

            VStack(spacing: Spacing.md) {
                KeelPrimaryButton("Connect Apple Health", isEnabled: !connecting) {
                    connect()
                }
                // Skipping is a choice: don't follow it with a Connect offer on next launch.
                KeelTextLink("Skip for now") { env.declineHealthConnectOffer(); onContinue() }
                Text("We never sell your data. Ever.")
                    .font(KeelFont.caption)
                    .foregroundStyle(theme.muted)
            }
            .padding(.bottom, Spacing.xxxl)
        }
        .padding(.horizontal, Spacing.screenH)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .keelScreenBackground()
    }

    private func iconTile<Content: View>(gradient: [Color], @ViewBuilder content: () -> Content) -> some View {
        content()
            .frame(width: 64, height: 64)
            .background(LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing))
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
    }

    private func connect() {
        connecting = true
        Task {
            await env.connectAppleHealth()
            connecting = false
            onContinue()
        }
    }
}

#Preview {
    AppleHealthView {}
        .environment(AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider()))
}
