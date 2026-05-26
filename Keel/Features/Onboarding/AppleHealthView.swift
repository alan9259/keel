import SwiftUI

struct AppleHealthView: View {
    @Environment(AppEnvironment.self) private var env
    let onContinue: () -> Void
    @State private var connecting = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: Spacing.xl)

            // Icon pair — partnership
            HStack(spacing: Spacing.md) {
                iconTile(gradient: [Color(hex: 0xFF2D55), Color(hex: 0xFF3B30)]) {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 32))
                        .foregroundStyle(.white)
                }
                Image(systemName: "plus")
                    .font(.system(size: 24))
                    .foregroundStyle(KeelColor.mutedForeground)
                iconTile(gradient: [KeelColor.terracotta, KeelColor.terracotta]) {
                    Text("K")
                        .font(KeelFont.serif(36, weight: .semibold))
                        .foregroundStyle(KeelColor.cream)
                }
            }

            VStack(spacing: Spacing.lg) {
                Text("Let Keel learn from what you already track.")
                    .onboardingTitle()
                    .padding(.top, Spacing.xl)

                Text("Keel can read your sleep, activity, cycle, and even symptoms like hot flushes from Health, automatically. Less for you to log, more for Keel to learn. Keel only ever reads, it never writes anything back.")
                    .onboardingSubtitle()
                    .foregroundStyle(KeelColor.warmGrey.opacity(0.8))
            }

            Spacer()

            VStack(spacing: Spacing.md) {
                KeelPrimaryButton("Connect Apple Health", isEnabled: !connecting) {
                    connect()
                }
                KeelTextLink("Skip for now") { onContinue() }
                Text("We never sell your data. Ever.")
                    .font(KeelFont.caption)
                    .foregroundStyle(KeelColor.mutedForeground)
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
            let granted = await env.health.requestAuthorization()
            env.users.setHealthKitAuthorized(granted)
            if granted { env.syncHealthData() }
            connecting = false
            onContinue()
        }
    }
}

#Preview {
    AppleHealthView {}
        .environment(AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider()))
}
