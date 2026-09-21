import SwiftUI

/// Full-screen cover shown while the app is locked. When Face ID / Touch ID unlock
/// is on, it leads with that (auto-prompts, "only needs Face ID"), keeping the PIN
/// tucked behind "Enter PIN instead". Otherwise it shows the PIN entry directly:
/// dots that fill as she types, plus a number pad. Nothing behind it is visible
/// until she authenticates.
struct LockGate: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme

    @State private var digits = ""
    @State private var error: String?
    @State private var triedBiometrics = false
    @State private var showPINPad = false
    @State private var now = Date()

    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var length: Int { env.lock.pinLength }
    private var lockedOut: Bool { env.lock.isLockedOut(now: now) }
    private var useBiometrics: Bool { env.lock.useBiometrics }
    /// Show the dots + number pad: always in PIN-only mode, or once she chooses it.
    private var padVisible: Bool { showPINPad || !useBiometrics }

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: Spacing.lg) {
                Spacer()
                Image(systemName: useBiometrics ? env.lock.biometry.symbol : "lock.fill")
                    .font(.system(size: 42)).foregroundStyle(theme.accent)
                Text("Keel is locked")
                    .font(KeelFont.serif(24, weight: .semibold)).foregroundStyle(theme.heading)
                Text(subtitle)
                    .font(KeelFont.body).foregroundStyle(theme.muted)
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                if padVisible {
                    PINDots(filled: digits.count, total: length)
                        .padding(.vertical, Spacing.sm).opacity(lockedOut ? 0.4 : 1)
                    Text(error ?? " ").font(KeelFont.caption).foregroundStyle(theme.attention)
                }
                Spacer()
                VStack(spacing: Spacing.md) {
                    if useBiometrics {
                        Button { Task { await tryBiometrics(force: true) } } label: {
                            Label("Use \(env.lock.biometry.label)", systemImage: env.lock.biometry.symbol)
                                .font(KeelFont.body).foregroundStyle(theme.accent)
                        }
                        .disabled(lockedOut)
                    }
                    if padVisible {
                        PINPad(enabled: !lockedOut, onDigit: add, onDelete: del)
                    } else {
                        Button("Enter PIN instead") { showPINPad = true }
                            .font(KeelFont.body).foregroundStyle(theme.muted)
                    }
                }
            }
            .padding(.horizontal, Spacing.screenH).padding(.vertical, Spacing.lg)
            .frame(maxWidth: 420)
        }
        .task { await tryBiometrics(force: false) }
        .onReceive(ticker) { now = $0 }
    }

    private var subtitle: String {
        if lockedOut {
            let seconds = Int(env.lock.lockoutRemaining(now: now).rounded(.up))
            return "Too many attempts. Try again in \(seconds)s."
        }
        if useBiometrics {
            return showPINPad
                ? "Use \(env.lock.biometry.label), or enter your PIN."
                : "Unlock with \(env.lock.biometry.label)."
        }
        return "Enter your PIN to unlock."
    }

    private func add(_ digit: Int) {
        guard !lockedOut, digits.count < length else { return }
        error = nil
        digits.append(String(digit))
        if digits.count == length { verify() }
    }

    private func del() { if !digits.isEmpty { digits.removeLast() } }

    private func verify() {
        Task {
            try? await Task.sleep(for: .milliseconds(120)) // let the last dot fill
            guard digits.count == length else { return }
            if env.lock.verifyPIN(digits) {
                Haptics.success() // gate disappears as isLocked flips
            } else {
                digits = ""
                error = env.lock.isLockedOut() ? "Too many attempts." : "Incorrect PIN. Try again."
                Haptics.error()
            }
        }
    }

    private func tryBiometrics(force: Bool) async {
        #if DEBUG
        if DebugHarness.forceLocked { return } // keep the gate up for screenshots
        #endif
        guard env.lock.isLocked, useBiometrics else { return }
        if !force && triedBiometrics { return }
        triedBiometrics = true
        _ = await env.lock.unlockWithBiometrics()
    }
}
