package com.salizzai.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps Android's built-in [TextToSpeech] engine so the assistant's replies can
 * be spoken out loud without any extra dependency.
 *
 * Persian is requested as the primary locale, with fallback to English.
 */
class TtsController(context: Context) {
    private var tts: TextToSpeech? = null
    private val _ready = kotlinx.coroutines.flow.MutableStateFlow(false)
    val ready: kotlinx.coroutines.flow.StateFlow<Boolean> = _ready

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English if Persian TTS data isn't installed
                    tts?.setLanguage(Locale.US)
                }
                _ready.value = true
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sali-zzai-${System.currentTimeMillis()}")
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
