import SwiftUI

/// Microphone toggle for the Diary field. Pulses while recording (honoring
/// Reduce Motion).
struct VoiceInputButton: View {
    @Environment(\.keelTheme) private var theme
    let isRecording: Bool
    let action: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false

    var body: some View {
        Button {
            Haptics.light()
            action()
        } label: {
            Image(systemName: "mic")
                .font(.system(size: 18))
                .foregroundStyle(isRecording ? theme.accent : theme.muted)
                .frame(width: 40, height: 40)
                .background(isRecording ? theme.accentTint : theme.inputBackground)
                .clipShape(Circle())
                .scaleEffect(pulse ? 1.12 : 1.0)
        }
        .onChange(of: isRecording) { _, recording in
            guard !reduceMotion else { return }
            if recording {
                withAnimation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true)) {
                    pulse = true
                }
            } else {
                withAnimation(.easeOut(duration: 0.2)) { pulse = false }
            }
        }
        .accessibilityLabel(isRecording ? "Stop recording" : "Voice input")
    }
}
