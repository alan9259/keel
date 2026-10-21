package com.therecalibrationyears.keel.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** Where voice notes are (port of iOS `SpeechRecognitionService.State`). */
enum class SpeechState { IDLE, RECORDING, UNAVAILABLE, DENIED }

/** The on-device rule and her messages, pure so they're tested (iOS `canDictateOnDevice`). */
object SpeechGate {
    /**
     * Dictation runs only when it stays on the phone: Android 12+ with on-device recognition,
     * and from Android 13 her language already installed (never downloaded for her). Otherwise
     * voice is unavailable rather than sent to a server.
     */
    fun canDictateOnDevice(sdk: Int, onDeviceAvailable: Boolean, languageInstalled: Boolean?): Boolean =
        sdk >= 31 && onDeviceAvailable && (sdk < 33 || languageInstalled == true)

    /** The installed recognition language matching her locale, if any (same tag, else same language). */
    fun matchingLanguage(installed: List<String>, locale: Locale): String? =
        installed.firstOrNull { it.equals(locale.toLanguageTag(), ignoreCase = true) }
            ?: installed.firstOrNull { Locale.forLanguageTag(it).language == locale.language }

    /** What to tell her when voice can't run, so she isn't left tapping a mic that does nothing. */
    fun unavailableMessage(state: SpeechState): String? = when (state) {
        SpeechState.UNAVAILABLE -> "Voice notes aren't available on this phone. You can type instead."
        SpeechState.DENIED -> "Voice notes need microphone access. You can type instead, or allow access in Settings."
        SpeechState.IDLE, SpeechState.RECORDING -> null
    }
}

/**
 * Live dictation for the check-in diary (port of iOS `SpeechRecognitionService`). Recognition
 * is on-device only, so the audio never leaves the phone. Use from the main thread.
 */
class SpeechRecognitionService(private val context: Context) {
    private val _state = MutableStateFlow(SpeechState.IDLE)
    val state: StateFlow<SpeechState> = _state
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript
    private var recognizer: SpeechRecognizer? = null

    val hasMicPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** She said no to the microphone on the system prompt. */
    fun permissionDenied() { _state.value = SpeechState.DENIED }

    /** Begin dictating, adding to any [seed] text already in the diary. */
    suspend fun start(seed: String) {
        stop()
        if (!hasMicPermission) { _state.value = SpeechState.DENIED; return }
        // Android 9 to 11 have no on-device recogniser: voice is unavailable, never sent away.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { _state.value = SpeechState.UNAVAILABLE; return }
        startOnDevice(seed)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun startOnDevice(seed: String) {
        val sdk = Build.VERSION.SDK_INT
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { _state.value = SpeechState.UNAVAILABLE; return }
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) installedLanguage(r) else Locale.getDefault().toLanguageTag()
        if (!SpeechGate.canDictateOnDevice(sdk, onDeviceAvailable = true, languageInstalled = language != null) || language == null) {
            r.destroy(); _state.value = SpeechState.UNAVAILABLE; return
        }
        recognizer = r
        val base = if (seed.isEmpty()) "" else "$seed "
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) = show(partialResults)
            override fun onResults(results: Bundle?) { show(results); stop() }
            override fun onError(error: Int) {
                stop()
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> _state.value = SpeechState.DENIED
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        _state.value = SpeechState.UNAVAILABLE
                    else -> Unit // silence, no match, busy: back to idle, she can tap again
                }
            }
            private fun show(bundle: Bundle?) {
                val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                _transcript.value = base + text
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        _transcript.value = seed
        _state.value = SpeechState.RECORDING
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun installedLanguage(r: SpeechRecognizer): String? = suspendCancellableCoroutine { cont ->
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        r.checkRecognitionSupport(intent, ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                if (cont.isActive) cont.resume(SpeechGate.matchingLanguage(support.installedOnDeviceLanguages, Locale.getDefault()))
            }
            override fun onError(error: Int) { if (cont.isActive) cont.resume(null) }
        })
    }

    fun stop() {
        recognizer?.run { stopListening(); destroy() }
        recognizer = null
        if (_state.value == SpeechState.RECORDING) _state.value = SpeechState.IDLE
    }

    /** Closing the check-in: stop and forget, so the next entry starts clean. */
    fun reset() {
        stop()
        _transcript.value = ""
        _state.value = SpeechState.IDLE
    }
}
