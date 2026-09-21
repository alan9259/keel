import Foundation
import Speech
import AVFoundation

/// Live, on-the-fly dictation for the check-in Notes step (SFSpeechRecognizer +
/// AVAudioEngine). Partial results stream into `transcript` as the user speaks;
/// the text stays fully editable afterward.
///
/// Transcription is forced **on-device** (`requiresOnDeviceRecognition = true`): the
/// audio never leaves the phone. If a device/locale can't recognise on-device, voice
/// is reported `unavailable` rather than falling back to Apple's servers, so she just
/// types instead and nothing is sent off-device.
@MainActor
@Observable
final class SpeechRecognitionService {
    enum State: Equatable {
        case idle
        case recording
        case unavailable
        case denied
    }

    private(set) var state: State = .idle
    private(set) var transcript = ""

    private let recognizer = SFSpeechRecognizer()
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    var isRecording: Bool { state == .recording }

    /// Whether dictation can run fully on-device. We only dictate when both are true,
    /// so the audio never goes to Apple's servers. Pure, so it's unit-testable.
    nonisolated static func canDictateOnDevice(isAvailable: Bool, supportsOnDevice: Bool) -> Bool {
        isAvailable && supportsOnDevice
    }

    /// Request speech + microphone permission.
    func requestAuthorization() async -> Bool {
        let speechAuthorized = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
        guard speechAuthorized else {
            state = .denied
            return false
        }
        let micGranted = await AVAudioApplication.requestRecordPermission()
        if !micGranted { state = .denied }
        return micGranted
    }

    /// Begin recording, appending to any existing `seed` text.
    func start(seed: String) throws {
        // Only dictate when it can run entirely on-device, so the audio never leaves
        // the phone. Otherwise report unavailable (she types) rather than send it off.
        guard let recognizer,
              Self.canDictateOnDevice(isAvailable: recognizer.isAvailable,
                                      supportsOnDevice: recognizer.supportsOnDeviceRecognition) else {
            state = .unavailable
            return
        }
        stop() // ensure clean slate

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: .duckOthers)
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.requiresOnDeviceRecognition = true // audio stays on-device
        request.taskHint = .dictation
        self.request = request

        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.request?.append(buffer)
        }
        audioEngine.prepare()
        try audioEngine.start()
        state = .recording

        let base = seed.isEmpty ? "" : seed + " "
        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self else { return }
            if let result {
                let text = base + result.bestTranscription.formattedString
                Task { @MainActor in self.transcript = text }
            }
            if error != nil || (result?.isFinal ?? false) {
                Task { @MainActor in self.stop() }
            }
        }
    }

    func stop() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        if state == .recording { state = .idle }
    }

    func reset() {
        stop()
        transcript = ""
        state = .idle
    }
}
