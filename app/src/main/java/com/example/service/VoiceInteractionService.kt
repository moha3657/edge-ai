package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sin

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    MUTED,
    ERROR
}

class VoiceInteractionService(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceInteractionService"
    }

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _aiSpokenText = MutableStateFlow("")
    val aiSpokenText: StateFlow<String> = _aiSpokenText.asStateFlow()

    private val _audioLevels = MutableStateFlow(List(16) { 0.1f })
    val audioLevels: StateFlow<List<Float>> = _audioLevels.asStateFlow()

    private val _isSpeakingTts = MutableStateFlow(false)
    val isSpeakingTts: StateFlow<Boolean> = _isSpeakingTts.asStateFlow()

    private val _activeUtteranceId = MutableStateFlow<String?>(null)
    val activeUtteranceId: StateFlow<String?> = _activeUtteranceId.asStateFlow()

    private val _isMicrophoneAvailable = MutableStateFlow(true)
    val isMicrophoneAvailable: StateFlow<Boolean> = _isMicrophoneAvailable.asStateFlow()

    private var speechRate: Float = 1.0f
    private var isMuted: Boolean = false
    private var onSpeechResultCallback: ((String) -> Unit)? = null
    private var waveAnimationJob: Job? = null

    private var onCurrentSpeechFinished: (() -> Unit)? = null

    init {
        initializeTts()
        checkSpeechRecognizerAvailability()
    }

    private fun initializeTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale.getDefault())
                }
                textToSpeech?.setSpeechRate(speechRate)
                textToSpeech?.setPitch(1.0f)
                isTtsReady = true

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeakingTts.value = true
                        _voiceState.value = VoiceState.SPEAKING
                        startSimulatedSpeakingWaves()
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeakingTts.value = false
                        stopWaveAnimation()
                        _activeUtteranceId.value = null
                        val callback = onCurrentSpeechFinished
                        onCurrentSpeechFinished = null
                        callback?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeakingTts.value = false
                        stopWaveAnimation()
                        _activeUtteranceId.value = null
                        val callback = onCurrentSpeechFinished
                        onCurrentSpeechFinished = null
                        callback?.invoke()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeakingTts.value = false
                        stopWaveAnimation()
                        _activeUtteranceId.value = null
                        val callback = onCurrentSpeechFinished
                        onCurrentSpeechFinished = null
                        callback?.invoke()
                    }
                })
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
            }
        }
    }

    private fun checkSpeechRecognizerAvailability() {
        _isMicrophoneAvailable.value = SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        textToSpeech?.setSpeechRate(rate)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            stopListening()
            _voiceState.value = VoiceState.MUTED
        } else {
            if (_voiceState.value == VoiceState.MUTED) {
                startListening(onSpeechResultCallback ?: {})
            }
        }
    }

    fun isMuted(): Boolean = isMuted

    fun startListening(onResult: (String) -> Unit) {
        if (isMuted) return
        onSpeechResultCallback = onResult
        stopSpeaking()

        scope.launch(Dispatchers.Main) {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = VoiceState.LISTENING
                        _userTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        _voiceState.value = VoiceState.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (_voiceState.value == VoiceState.LISTENING) {
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                            updateListeningWaves(normalized)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceState.value = VoiceState.THINKING
                        stopWaveAnimation()
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "SpeechRecognizer error: $error")
                        // If speech error occurs (e.g. no match / timeout), return to listening if still active
                        if (_voiceState.value == VoiceState.LISTENING) {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            _userTranscript.value = text
                            _voiceState.value = VoiceState.THINKING
                            onSpeechResultCallback?.invoke(text)
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim()
                        if (!partial.isNullOrBlank()) {
                            _userTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
                _voiceState.value = VoiceState.LISTENING
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer", e)
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun stopListening() {
        scope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer", e)
            }
            if (_voiceState.value == VoiceState.LISTENING) {
                _voiceState.value = VoiceState.IDLE
            }
            stopWaveAnimation()
        }
    }

    fun speak(text: String, utteranceId: String = UUID.randomUUID().toString(), onFinished: (() -> Unit)? = null) {
        if (!isTtsReady || textToSpeech == null) {
            Log.w(TAG, "TTS not ready")
            onFinished?.invoke()
            return
        }

        // Clean text from markdown formatting (e.g. **bold**, # headings, backticks) for natural voice
        val cleanedText = cleanMarkdownForSpeech(text)
        if (cleanedText.isBlank()) {
            onFinished?.invoke()
            return
        }

        stopSpeaking()
        onCurrentSpeechFinished = onFinished
        _aiSpokenText.value = cleanedText
        _activeUtteranceId.value = utteranceId
        _voiceState.value = VoiceState.SPEAKING
        _isSpeakingTts.value = true

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        textToSpeech?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        try {
            if (_isSpeakingTts.value) {
                textToSpeech?.stop()
                _isSpeakingTts.value = false
                _activeUtteranceId.value = null
                stopWaveAnimation()
                if (_voiceState.value == VoiceState.SPEAKING) {
                    _voiceState.value = VoiceState.IDLE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun setVoiceState(state: VoiceState) {
        _voiceState.value = state
    }

    fun simulateSpokenQuery(query: String) {
        _userTranscript.value = query
        _voiceState.value = VoiceState.THINKING
        onSpeechResultCallback?.invoke(query)
    }

    private fun updateListeningWaves(amplitude: Float) {
        val levels = List(16) { i ->
            val phase = (i * 0.4f)
            val variation = (abs(sin(phase)) * 0.4f) + 0.1f
            (amplitude * variation).coerceIn(0.1f, 1.0f)
        }
        _audioLevels.value = levels
    }

    private fun startSimulatedSpeakingWaves() {
        stopWaveAnimation()
        waveAnimationJob = scope.launch(Dispatchers.Default) {
            var step = 0f
            while (isActive && _isSpeakingTts.value) {
                step += 0.25f
                val levels = List(16) { i ->
                    val wave1 = abs(sin(step + i * 0.4f))
                    val wave2 = abs(sin(step * 0.7f + i * 0.2f))
                    ((wave1 * 0.6f + wave2 * 0.4f) * 0.85f + 0.15f).coerceIn(0.15f, 1.0f)
                }
                _audioLevels.value = levels
                delay(70)
            }
            _audioLevels.value = List(16) { 0.1f }
        }
    }

    private fun stopWaveAnimation() {
        waveAnimationJob?.cancel()
        waveAnimationJob = null
        _audioLevels.value = List(16) { 0.1f }
    }

    fun cleanMarkdownForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```"), "Code snippet omitted.")
            .replace(Regex("`[^`]+`"), "code block")
            .replace(Regex("#{1,6}\\s*"), "")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            .replace(Regex("[>\\-•*]\\s*"), "")
            .replace(Regex("\\n{2,}"), ". ")
            .replace(Regex("\\n"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    fun shutdown() {
        stopSpeaking()
        stopListening()
        textToSpeech?.shutdown()
        textToSpeech = null
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
