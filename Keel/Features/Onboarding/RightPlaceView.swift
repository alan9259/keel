import SwiftUI

struct RightPlaceView: View {
    @Environment(\.keelTheme) private var theme
    let onContinue: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    Spacer().frame(height: Spacing.xxl)

                    Text("You're in the right place.")
                        .onboardingTitle(.leading)

                    // Pull quote
                    StandardCard {
                        Text("\u{201C}Ohhh\u{2026} that explains a lot.\u{201D}")
                            .font(KeelFont.h3)
                            .italic()
                            .foregroundStyle(theme.text.opacity(0.8))
                    }
                    .overlay(alignment: .leading) {
                        Rectangle().fill(theme.accent).frame(width: 4)
                            .clipShape(RoundedRectangle(cornerRadius: 2))
                    }

                    Text("Maybe I'm not losing myself after all. Perimenopause can make you feel like you're piecing yourself together from scattered clues. Keel gives you one place to record what you're noticing: sleep, mood, energy, cycle, symptoms, treatments and how it's affecting your days. Whatever you record is there for you to look back on later, in your own words.")
                        .font(KeelFont.bodyLarge)
                        .foregroundStyle(theme.text.opacity(0.9))
                        .lineSpacing(4)

                    CalloutCard {
                        Text("One thing to know: there is no minimum. Even a few entries give you something to look back on, and the more you record, the fuller your record becomes.")
                            .font(KeelFont.body)
                            .foregroundStyle(theme.text.opacity(0.8))
                            .lineSpacing(3)
                    }

                    Spacer().frame(height: Spacing.sm)
                }
                .padding(.horizontal, Spacing.screenH)
            }

            KeelPrimaryButton("Continue", action: onContinue)
                .padding(.horizontal, Spacing.screenH)
                .padding(.bottom, Spacing.xxl)
        }
        .keelScreenBackground()
    }
}

#Preview {
    RightPlaceView {}
}
