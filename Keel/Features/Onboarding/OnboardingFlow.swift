import SwiftUI

/// Coordinates the six onboarding screens (KEEL_DESIGN_SPEC.md screens 1–6).
struct OnboardingFlow: View {
    enum Step: Int, CaseIterable {
        case welcome, rightPlace, createAccount, pathway, appleHealth, ready
    }

    @Environment(\.keelTheme) private var theme
    let onComplete: () -> Void
    @State private var step: Step = .welcome

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            content
                .transition(.asymmetric(
                    insertion: .move(edge: .trailing).combined(with: .opacity),
                    removal: .move(edge: .leading).combined(with: .opacity)
                ))
                .id(step)
        }
        .animation(.easeInOut(duration: 0.3), value: step)
        .onAppear {
            #if DEBUG
            if let raw = DebugHarness.onboardingStartStep, let s = Step(rawValue: raw) { step = s }
            #endif
        }
    }

    @ViewBuilder
    private var content: some View {
        switch step {
        case .welcome:
            WelcomeView { advance() }
        case .rightPlace:
            RightPlaceView { advance() }
        case .createAccount:
            CreateAccountView { advance() }
        case .pathway:
            PathwayView { advance() }
        case .appleHealth:
            AppleHealthView { advance() }
        case .ready:
            ReadyView { onComplete() }
        }
    }

    private func advance() {
        guard let next = Step(rawValue: step.rawValue + 1) else {
            onComplete()
            return
        }
        step = next
    }
}
