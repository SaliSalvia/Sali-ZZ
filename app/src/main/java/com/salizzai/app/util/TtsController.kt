package com.salizzai.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Wraps Android's built-in [TextToSpeech] engine so the assistant's replies can
 * be spoken out loud without any extra dependency.
 *
 * Persian is requested as the primary locale, with fallback to English.
 * [speaking] flips back to false as soon as the utterance really finishes, so the
 * play/stop button in the chat never gets stuck in the "playing" state.
 */
class TtsController(context: Context) {
    private var tts: TextToSpeech? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** Id of the message currently being read, so the UI highlights the right bubble. */
    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English if Persian TTS data isn't installed
                    tts?.setLanguage(Locale.US)
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _speaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        finish(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finish(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        finish(utteranceId)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        finish(utteranceId)
                    }

                    private fun finish(utteranceId: String?) {
                        if (utteranceId == _activeUtterance) {
                            _speaking.value = false
                            _speakingId.value = null
                            _activeUtterance = null
                        }
                    }
                })
                _ready.value = true
            }
        }
    }

    private var _activeUtterance: String? = null

    /** Speaks [text]; when [messageId] is provided the UI can highlight that bubble. */
    fun speak(text: String, messageId: String? = null) {
        if (text.isBlank()) return
        val utteranceId = "sali-zzai-${System.currentTimeMillis()}"
        _activeUtterance = utteranceId
        _speakingId.value = messageId
        _speaking.value = true
        tts?.stop()
        tts?.speak(stripMarkdown(text), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _speaking.value = false
        _speakingId.value = null
        _activeUtterance = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _speaking.value = false
        _speakingId.value = null
    }

    /**
     * Reading raw markdown aloud produces "asterisk asterisk" noise, so the
     * structural characters are stripped before handing the text to the engine.
     */
    private fun stripMarkdown(text: String): String =
        text.replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("[*_~>]{1,3}"), "")
            .replace(Regex("\\|"), " ")
            .trim()
}
