package com.salizzai.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Android's built-in [SpeechRecognizer] (no extra dependencies).
 *
 * Exposes a [partialResult] and [finalResult] flow that the chat UI collects.
 * The recognizer uses the device's default offline/online speech engine,
 * so it works without any Z.ai network call.
 */
class VoiceInputController(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _final = MutableStateFlow<String?>(null)
    val final: StateFlow<String?> = _final.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _error.value = "تشخیص صوت روی این دستگاه پشتیبانی نمی‌شود"
            return
        }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _listening.value = true
                    _error.value = null
                    _final.value = null
                    _partial.value = ""
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _listening.value = false }
                override fun onError(errorCode: Int) {
                    _listening.value = false
                    _error.value = mapError(errorCode)
                }
                override fun onResults(results: Bundle?) {
                    _listening.value = false
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull()
                    _final.value = text
                    _partial.value = ""
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    _partial.value = list?.firstOrNull() ?: ""
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        _listening.value = false
    }

    fun consumeFinal(): String? {
        val v = _final.value
        _final.value = null
        return v
    }

    fun clearError() {
        _error.value = null
    }

    private fun mapError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK -> "خطای شبکه"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "تایم‌اوت شبکه"
        SpeechRecognizer.ERROR_AUDIO -> "خطای ضبط صدا"
        SpeechRecognizer.ERROR_NO_MATCH -> "صدا قابل تشخیص نبود"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "تشخیص صدا مشغول است"
        SpeechRecognizer.ERROR_SERVER -> "خطای سرور تشخیص صدا"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی دریافت نشد"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازه ضبط صدا داده نشده"
        else -> "خطای ناشناخته در تشخیص صدا"
    }
}
