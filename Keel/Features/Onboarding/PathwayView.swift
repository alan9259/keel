import SwiftUI

struct PathwayView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    let onContinue: () -> Void
    @State private var selection: Pathway?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    Spacer().frame(height: Spacing.md)

                    Text("How are you approaching perimenopause?")
                        .onboardingTitle(.leading)

                    Text("There's no right answer. This helps Keel personalise your experience.")
                        .onboardingSubtitle(.leading)
                        .foregroundStyle(theme.muted)
                        .padding(.bottom, Spacing.md)

                    ForEach(Pathway.allCases) { pathway in
                        RadioCard(
                            title: pathway.title,
                            description: pathway.detail,
                            isSelected: selection == pathway
                        ) {
                            selection = pathway
                        }
                    }
                }
                .padding(.horizontal, Spacing.screenH)
                // Breathing room so the last option clears the pinned button.
                .padding(.bottom, Spacing.lg)
            }

            KeelPrimaryButton("Continue", isEnabled: selection != nil) {
                if let selection { env.users.setPathway(selection) }
                onContinue()
            }
            .padding(.horizontal, Spacing.screenH)
            .padding(.top, Spacing.sm)
            .padding(.bottom, Spacing.xl)
            .background(theme.background)
        }
        .keelScreenBackground()
    }
}

#Preview {
    PathwayView {}
        .environment(AppEnvironment(container: KeelSchema.makeContainer(inMemory: true), provider: NoopSyncProvider()))
}
