import SwiftUI

struct ReadyView: View {
    let onComplete: () -> Void
    @State private var appeared = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: Spacing.xl)

            Text("Let's start seeing the pattern.")
                .onboardingTitle()
                .opacity(appeared ? 1 : 0)

            StandardCard(padding: Spacing.xl) {
                Text("Check in daily. The more you share, the clearer your picture becomes. Keel will take a couple of weeks to learn you, so be patient with it. The patterns are already there. We're just going to help you see them.")
                    .font(KeelFont.bodyLarge)
                    .foregroundStyle(KeelColor.warmGrey.opacity(0.9))
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
