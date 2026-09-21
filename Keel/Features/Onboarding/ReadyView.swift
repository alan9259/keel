import SwiftUI

struct ReadyView: View {
    @Environment(\.keelTheme) private var theme
    let onComplete: () -> Void
    @State private var appeared = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: Spacing.xl)

            Text("Let's start your record.")
                .onboardingTitle()
                .opacity(appeared ? 1 : 0)

            StandardCard(padding: Spacing.xl) {
                Text("Check in when it suits you. The more you record, the more there is to look back on. Nothing here is a test, and a missed day does not set you back.")
                    .font(KeelFont.bodyLarge)
                    .foregroundStyle(theme.text.opacity(0.9))
                    .lineSpacing(4)
            }
            .padding(.top, Spacing.xl)
            .opacity(appeared ? 1 : 0)

            Spacer()

            KeelPrimaryButton("Start with Keel", action: onComplete)
                .padding(.bottom, Spacing.xxxl)
                .opacity(appeared ? 1 : 0)
        }
        .padding(.horizontal, Spacing.screenH)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .keelScreenBackground()
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) { appeared = true }
        }
    }
}

#Preview {
    ReadyView {}
}
