import SwiftUI

struct RightPlaceView: View {
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
                            .foregroundStyle(KeelColor.warmGrey.opacity(0.8))
                    }
                    .overlay(alignment: .leading) {
                        Rectangle().fill(KeelColor.terracotta).frame(width: 4)
                            .clipShape(RoundedRectangle(cornerRadius: 2))
                    }

                    Text("Maybe I'm not losing myself after all. Perimenopause can make you feel like you're piecing yourself together from scattered clues. Keel helps you notice patterns across sleep, mood, energy, cycle, symptoms and lifestyle, so you can understand what may be affecting how you feel.")
                        .font(KeelFont.bodyLarge)
                        .foregroundStyle(KeelColor.warmGrey.opacity(0.9))
                        .lineSpacing(4)

                    CalloutCard {
                        Text("One thing to know: Keel needs a couple of weeks of data before it can start surfacing meaningful patterns. The more you check in, the clearer your picture becomes.")
                            .font(KeelFont.body)
                            .foregroundStyle(KeelColor.warmGrey.opacity(0.8))
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
