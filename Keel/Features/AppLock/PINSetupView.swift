import SwiftUI

/// Two-step PIN setup: create a PIN, then confirm it. On a match it stores the PIN
/// (hashed) and turns the lock on. Used from Settings when she enables the app lock.
struct PINSetupView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme

    let onComplete: () -> Void
    let onCancel: () -> Void

    @State private var first = ""
    @State private var digits = ""
    @State private var confirming = false
    @State private var error: String?

    private var length: Int { env.lock.pinLength }

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: Spacing.lg) {
                HStack {
                    Button("Cancel", action: onCancel)
                        .font(KeelFont.body).foregroundStyle(theme.accent)
                    Spacer()
                }
                Spacer()
                Text(confirming ? "Confirm your PIN" : "Create a PIN")
                    .font(KeelFont.serif(24, weight: .semibold)).foregroundStyle(theme.heading)
                Text("Keel will ask for this to unlock, when Face ID isn't used.")
                    .font(KeelFont.body).foregroundStyle(theme.muted)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                PINDots(filled: digits.count, total: length).padding(.vertical, Spacing.md)
                Text(error ?? " ").font(KeelFont.caption).foregroundStyle(theme.attention)
                Spacer()
                PINPad(onDigit: add, onDelete: del)
            }
            .padding(.horizontal, Spacing.screenH).padding(.vertical, Spacing.lg)
            .frame(maxWidth: 420)
        }
    }

    private func add(_ digit: Int) {
        guard digits.count < length else { return }
        error = nil
        digits.append(String(digit))
        if digits.count == length { advance() }
    }

    private func del() { if !digits.isEmpty { digits.removeLast() } }

    private func advance() {
        Task {
            try? await Task.sleep(for: .milliseconds(120)) // let the last dot fill
            guard digits.count == length else { return }
            if !confirming {
                first = digits
                digits = ""
                confirming = true
            } else if digits == first {
                if env.lock.setPIN(digits) {
                    Haptics.success()
                    onComplete()
                } else {
                    reset("Please choose a \(length)-digit PIN.")
                }
            } else {
                reset("Those didn't match. Let's try again.")
            }
        }
    }

    private func reset(_ message: String) {
        error = message
        first = ""
        digits = ""
        confirming = false
    }
}
