package com.salizzai.app.feature.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.api.ZaiApiClient
import com.salizzai.app.data.db.AppDatabase
import com.salizzai.app.data.db.entity.Attachment
import com.salizzai.app.data.db.entity.Conversation
import com.salizzai.app.data.db.entity.Message
import com.salizzai.app.data.model.AvailableModels
import com.salizzai.app.data.model.ChatCompletionRequest
import com.salizzai.app.data.model.ChatMessage
import com.salizzai.app.data.model.ThinkingConfig
import com.salizzai.app.data.prefs.UserPreferences
import com.salizzai.app.util.ZipExtractor
import com.salizzai.app.util.TtsController
import com.salizzai.app.util.VoiceInputController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

data class ChatUiState(
    val conversationId: String? = null,
    val conversationTitle: String = "گفتگوی جدید",
    val messages: List<Message> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val pendingImageUrls: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val error: String? = null,
    val draftInput: String = "",
    val model: String = AvailableModels.DEFAULT_MODEL,
    val thinkingEnabled: Boolean = false,
    val temperature: Float = 0.7f,
    val systemPrompt: String = "",
    val pendingReasoning: String = "",
    val isListening: Boolean = false,
    val voicePartial: String = "",
    val isSpeaking: Boolean = false,
    val zipCompareMode: Boolean = false
)

class ChatViewModel(
    private val database: AppDatabase,
    private val zaiClient: ZaiApiClient,
    private val userPrefs: UserPreferences,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** The active streaming request, so the user can cancel it with the Stop button. */
    private var streamJob: kotlinx.coroutines.Job? = null

    val voiceController = VoiceInputController(appContext)
    val ttsController = TtsController(appContext)

    init {
        // Subscribe to preference changes
        viewModelScope.launch {
            userPrefs.model.collectLatest { m ->
                _uiState.update { it.copy(model = m) }
            }
        }
        viewModelScope.launch {
            userPrefs.temperature.collectLatest { t ->
                _uiState.update { it.copy(temperature = t) }
            }
        }
        viewModelScope.launch {
            userPrefs.thinkingEnabled.collectLatest { e ->
                _uiState.update { it.copy(thinkingEnabled = e) }
            }
        }
        viewModelScope.launch {
            userPrefs.systemPrompt.collectLatest { p ->
                _uiState.update { it.copy(systemPrompt = p) }
            }
        }
        // Observe voice input
        viewModelScope.launch {
            voiceController.listening.collectLatest { listening ->
                _uiState.update { it.copy(isListening = listening) }
            }
        }
        viewModelScope.launch {
            voiceController.partial.collectLatest { partial ->
                if (partial.isNotBlank()) {
                    _uiState.update { it.copy(voicePartial = partial) }
                }
            }
        }
        viewModelScope.launch {
            voiceController.final.collectLatest { finalText ->
                if (!finalText.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            draftInput = if (it.draftInput.isBlank()) finalText
                            else "${it.draftInput} $finalText",
                            voicePartial = ""
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            voiceController.error.collectLatest { e ->
                if (!e.isNullOrBlank()) {
                    _uiState.update { it.copy(error = e) }
                }
            }
        }
    }

    fun setConversationId(id: String?) {
        _uiState.update { it.copy(conversationId = id) }
        if (id != null) {
            viewModelScope.launch {
                val conv = database.conversationDao().getById(id)
                if (conv != null) {
                    _uiState.update { it.copy(conversationTitle = conv.title, model = conv.model) }
                }
                val msgs = database.messageDao().getForConversation(id)
                _uiState.update { it.copy(messages = msgs) }
                // Only not-yet-sent attachments should show as pending chips.
                val atts = database.attachmentDao().getPendingForConversation(id)
                _uiState.update { it.copy(attachments = atts) }
            }
        } else {
            _uiState.update {
                it.copy(messages = emptyList(), attachments = emptyList(), conversationTitle = "گفتگوی جدید")
            }
        }
    }

    fun updateDraftInput(text: String) {
        _uiState.update { it.copy(draftInput = text) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun newChat() {
        setConversationId(null)
    }

    fun toggleZipCompareMode() {
        _uiState.update { it.copy(zipCompareMode = !it.zipCompareMode) }
    }

    // ─── Voice input ───────────────────────────────────────────────────

    fun startVoiceInput() {
        voiceController.start()
    }

    fun stopVoiceInput() {
        voiceController.stop()
    }

    // ─── TTS ───────────────────────────────────────────────────────────

    fun speakReply(text: String) {
        _uiState.update { it.copy(isSpeaking = true) }
        ttsController.speak(text)
        // Note: for simplicity, isSpeaking stays true until the user stops manually
    }

    fun stopSpeaking() {
        ttsController.stop()
        _uiState.update { it.copy(isSpeaking = false) }
    }

    // ─── Stop generation ────────────────────────────────────────────────

    /**
     * Cancels the in-flight streaming response (Stop button) and stops TTS.
     * Everything received so far is flushed/persisted by the collectors
     * before cancellation propagates.
     */
    fun stopGeneration() {
        streamJob?.cancel()
        streamJob = null
        ttsController.stop()
        _uiState.update { it.copy(isStreaming = false, isSpeaking = false, pendingReasoning = "") }
    }

    // ─── File / image / ZIP processing ─────────────────────────────────

    suspend fun processZipFile(filename: String, mimeType: String, bytes: ByteArray): Attachment {
        return withContext(Dispatchers.IO) {
            val result = ZipExtractor.extract(bytes, filename)
            val fileTreeJson = Json.encodeToString(
                ListSerializer(com.salizzai.app.data.model.FileTreeNode.serializer()),
                result.fileTree
            )
            val attachment = Attachment(
                id = UUID.randomUUID().toString(),
                messageId = null,
                conversationId = _uiState.value.conversationId,
                filename = "zip_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.zip",
                originalName = filename,
                mimeType = mimeType,
                size = bytes.size.toLong(),
                fileType = "zip",
                extractedText = result.extractedText,
                fileTree = fileTreeJson,
                fileCount = result.fileCount,
                totalSize = result.totalSize
            )
            database.attachmentDao().insert(attachment)
            _uiState.update { state ->
                state.copy(attachments = state.attachments + attachment)
            }
            attachment
        }
    }

    suspend fun processRegularFile(filename: String, mimeType: String, bytes: ByteArray): Attachment {
        return withContext(Dispatchers.IO) {
            val isText = filename.substringAfterLast(".").lowercase() in ZipExtractor.TEXT_EXTENSIONS
            val extractedText = if (isText && bytes.size < 1024 * 1024) {
                val content = bytes.toString(Charsets.UTF_8)
                val truncated = if (content.length > 8000) content.take(8000) + "\n... [متوقف شد]" else content
                "### `$filename`\n\n```\n$truncated\n```\n"
            } else null

            val attachment = Attachment(
                id = UUID.randomUUID().toString(),
                messageId = null,
                conversationId = _uiState.value.conversationId,
                filename = "${System.currentTimeMillis()}_$filename",
                originalName = filename,
                mimeType = mimeType,
                size = bytes.size.toLong(),
                fileType = "file",
                extractedText = extractedText,
                fileCount = 1,
                totalSize = bytes.size.toLong()
            )
            database.attachmentDao().insert(attachment)
            _uiState.update { state ->
                state.copy(attachments = state.attachments + attachment)
            }
            attachment
        }
    }

    /**
     * Processes an image attachment: stores metadata in Room and keeps the base64
     * data URL in [ChatUiState.pendingImageUrls] so that the next outgoing message
     * carries it as a multimodal `image_url` part (vision models, e.g. GLM-4.5V).
     */
    suspend fun processImageFile(filename: String, mimeType: String, bytes: ByteArray): Attachment {
        return withContext(Dispatchers.IO) {
            val dataUrl = zaiClient.encodeImageAsDataUrl(bytes, mimeType)
            val attachment = Attachment(
                id = UUID.randomUUID().toString(),
                messageId = null,
                conversationId = _uiState.value.conversationId,
                filename = "${System.currentTimeMillis()}_$filename",
                originalName = filename,
                mimeType = mimeType,
                size = bytes.size.toLong(),
                fileType = "image",
                extractedText = null,
                fileCount = 1,
                totalSize = bytes.size.toLong()
            )
            database.attachmentDao().insert(attachment)
            _uiState.update { state ->
                state.copy(
                    attachments = state.attachments + attachment,
                    pendingImageUrls = state.pendingImageUrls + dataUrl
                )
            }
            attachment
        }
    }

    /** Removes an image attachment together with its pending data URL (matched by index). */
    fun removeImageAttachment(id: String, dataUrl: String) {
        viewModelScope.launch {
            database.attachmentDao().deleteById(id)
            _uiState.update { state ->
                state.copy(
                    attachments = state.attachments.filterNot { it.id == id },
                    pendingImageUrls = state.pendingImageUrls.filterNot { it == dataUrl }
                )
            }
        }
    }

    fun removeAttachment(id: String) {
        viewModelScope.launch {
            database.attachmentDao().deleteById(id)
            _uiState.update { state ->
                state.copy(attachments = state.attachments.filterNot { it.id == id })
            }
        }
    }

    // ─── Send message ──────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank() && _uiState.value.attachments.isEmpty()) return
        if (_uiState.value.isStreaming) return

        // Capture data we need at the moment of send
        val initialAttachments = _uiState.value.attachments
        val userMsgContent = text
        val attachmentIds = initialAttachments.map { it.id }
        val pendingImageUrls = _uiState.value.pendingImageUrls

        val job = viewModelScope.launch {
            val currentState = _uiState.value

            // Build context for attachments
            val zipAttachments = initialAttachments.filter { it.fileType == "zip" }
            val fileAttachments = initialAttachments.filter { it.fileType == "file" }
            val imageAttachments = initialAttachments.filter { it.fileType == "image" }

            // Multi-ZIP comparison: if 2+ ZIPs are attached, build a comparison prompt
            val attachmentContext = buildString {
                if (fileAttachments.isNotEmpty()) {
                    fileAttachments.filter { !it.extractedText.isNullOrEmpty() }.forEach { att ->
                        append("--- فایل پیوست: ${att.originalName} ---\n${att.extractedText}\n--- پایان ---\n\n")
                    }
                }
                if (zipAttachments.size >= 2) {
                    val snapshots = zipAttachments.mapNotNull { att ->
                        // Re-extract snapshot from stored text isn't reliable; rebuild from bytes isn't available.
                        // Instead, rely on the stored fileTree + extractedText to compose a comparison block.
                        com.salizzai.app.data.model.ZipSnapshot(
                            originalName = att.originalName,
                            fileCount = att.fileCount,
                            totalSize = att.totalSize,
                            topExtensions = emptyMap(),
                            topLevelDirs = emptyList(),
                            filesByPath = emptyMap(),
                            textPreviewByPath = emptyMap()
                        )
                    }
                    append("--- تحلیل مقایسه‌ای چند ZIP (${zipAttachments.size} فایل) ---\n")
                    snapshots.forEachIndexed { i, s ->
                        append("نسخه ${i + 1}: ${s.originalName} — ${s.fileCount} فایل، حجم ${com.salizzai.app.util.ZipExtractor.formatBytes(s.totalSize)}\n")
                    }
                    append("\nمحتوای متنی هر ZIP در بخش زیر آمده است.\n\n")
                    zipAttachments.forEach { att ->
                        append("=== ${att.originalName} ===\n${att.extractedText ?: "(بدون متن استخراج‌شده)"}\n\n")
                    }
                    append("--- پایان تحلیل مقایسه‌ای ---\n\n")
                } else if (zipAttachments.size == 1) {
                    val att = zipAttachments.first()
                    append("--- فایل ZIP: ${att.originalName} (شامل ${att.fileCount} فایل) ---\n${att.extractedText ?: "(بدون متن استخراج‌شده)"}\n--- پایان ---\n\n")
                }
            }

            // Create conversation if not exists
            var convId = currentState.conversationId
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                val title = userMsgContent.take(50).ifBlank {
                    zipAttachments.firstOrNull()?.originalName ?: "گفتگوی جدید"
                }
                // Scope conversation to the current logged-in user
                val currentUserId = userPrefs.loggedInUserIdSnapshot()
                val conv = Conversation(
                    id = convId,
                    title = title,
                    model = currentState.model,
                    userId = currentUserId
                )
                database.conversationDao().insert(conv)
                _uiState.update { it.copy(conversationId = convId, conversationTitle = conv.title) }
            }

            // Save user message
            val userMsgId = UUID.randomUUID().toString()
            val userMsg = Message(
                id = userMsgId,
                conversationId = convId,
                role = "user",
                content = userMsgContent
            )
            database.messageDao().insert(userMsg)

            if (attachmentIds.isNotEmpty()) {
                // Link attachments to the user message AND the conversation so they
                // are not orphaned when the chat is reopened later.
                database.attachmentDao().linkToMessage(attachmentIds, userMsgId, convId)
                _uiState.update { it.copy(attachments = emptyList(), pendingImageUrls = emptyList()) }
            }

            _uiState.update {
                it.copy(messages = it.messages + userMsg, draftInput = "")
            }

            // Pick model — if there are image attachments, switch to a vision-capable model
            val effectiveModel = if (imageAttachments.isNotEmpty()) {
                AvailableModels.pickForVision(currentState.model)
            } else currentState.model

            // Placeholder for assistant message
            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = Message(
                id = assistantMsgId,
                conversationId = convId,
                role = "assistant",
                content = "",
                model = effectiveModel
            )
            database.messageDao().insert(assistantMsg)
            _uiState.update {
                it.copy(messages = it.messages + assistantMsg, isStreaming = true, pendingReasoning = "")
            }

            // Build messages array for Z.ai
            val historyMessages = mutableListOf<ChatMessage>()
            if (currentState.systemPrompt.isNotBlank()) {
                historyMessages.add(ChatMessage("system", currentState.systemPrompt))
            }
            historyMessages.add(ChatMessage("system", """
                تو دستیار هوشمند Z.ai هستی. به زبان کاربر (فارسی یا انگلیسی) پاسخ بده.
                برای تحلیل فایل‌های ZIP از ساختار استخراج‌شده و محتوای متنی استفاده کن و تحلیل عمیق ارائه بده.
                اگر چند نسخه از یک پروژه/مقاله/اختراع آپلود شده، تفاوت‌ها و بهبودها را به دقت مقایسه کن.
                برای تحلیل علمی، روش‌شناسی، نوآوری و ادعاهای فنی را به‌طور انتقادی بررسی کن.
                از Markdown برای قالب‌بندی پاسخ استفاده کن.
            """.trimIndent()))
            currentState.messages.takeLast(10).forEach { msg ->
                historyMessages.add(ChatMessage(msg.role, msg.content))
            }
            val finalContent = if (attachmentContext.isNotBlank()) {
                "$userMsgContent\n\n[محتوای فایل‌های پیوست برای تحلیل]:\n$attachmentContext"
            } else userMsgContent
            // Multimodal: attach pending image data URLs so vision models can read them.
            historyMessages.add(
                ChatMessage("user", finalContent, images = pendingImageUrls)
            )

            val request = ChatCompletionRequest(
                model = effectiveModel,
                messages = historyMessages,
                thinking = ThinkingConfig(if (currentState.thinkingEnabled) "enabled" else "disabled"),
                temperature = currentState.temperature.toDouble(),
                stream = true
            )

            // Stream response
            val responseBuffer = StringBuilder()
            val reasoningBuffer = StringBuilder()
            var lastResponseFlushAt = System.currentTimeMillis()
            var lastReasoningFlushAt = lastResponseFlushAt

            suspend fun flushResponseBuffer() {
                if (responseBuffer.isEmpty()) return
                val chunk = responseBuffer.toString()
                responseBuffer.setLength(0)
                database.messageDao().appendContent(assistantMsgId, chunk)
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) msg.copy(content = msg.content + chunk)
                            else msg
                        }
                    )
                }
            }

            fun queueReasoningChunk(content: String) {
                reasoningBuffer.append(content)
            }

            suspend fun flushReasoningBuffer() {
                if (reasoningBuffer.isEmpty()) return
                val chunk = reasoningBuffer.toString()
                reasoningBuffer.setLength(0)
                _uiState.update { state ->
                    state.copy(pendingReasoning = state.pendingReasoning + chunk)
                }
            }

            try {
                zaiClient.streamChatCompletion(request).collect { event ->
                    when (event) {
                        is ZaiApiClient.StreamEvent.Delta -> {
                            responseBuffer.append(event.content)
                            val now = System.currentTimeMillis()
                            if (responseBuffer.length >= 64 || now - lastResponseFlushAt >= 80L) {
                                flushResponseBuffer()
                                lastResponseFlushAt = now
                            }
                        }
                        is ZaiApiClient.StreamEvent.Reasoning -> {
                            queueReasoningChunk(event.content)
                            val now = System.currentTimeMillis()
                            if (reasoningBuffer.length >= 64 || now - lastReasoningFlushAt >= 80L) {
                                flushReasoningBuffer()
                                lastReasoningFlushAt = now
                            }
                        }
                        is ZaiApiClient.StreamEvent.Done -> {
                            flushResponseBuffer()
                            flushReasoningBuffer()
                            database.messageDao().markError(assistantMsgId, false)
                            _uiState.update { it.copy(isStreaming = false, pendingReasoning = "") }
                            database.conversationDao().touch(convId)
                        }
                        is ZaiApiClient.StreamEvent.Error -> {
                            flushResponseBuffer()
                            flushReasoningBuffer()
                            val errorContent = "❌ خطا: ${event.message}"
                            database.messageDao().updateContent(assistantMsgId, errorContent)
                            database.messageDao().markError(assistantMsgId, true)
                            _uiState.update {
                                it.copy(
                                    isStreaming = false,
                                    error = event.message,
                                    pendingReasoning = "",
                                    messages = it.messages.map { msg ->
                                        if (msg.id == assistantMsgId) msg.copy(content = errorContent, isError = true)
                                        else msg
                                    }
                                )
                            }
                        }
                        is ZaiApiClient.StreamEvent.Open -> { /* Stream opened */ }
                    }
                }
            } catch (e: Exception) {
                val errorContent = "❌ خطای غیرمنتظره: ${e.message}"
                database.messageDao().updateContent(assistantMsgId, errorContent)
                database.messageDao().markError(assistantMsgId, true)
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        error = e.message,
                        pendingReasoning = "",
                        messages = it.messages.map { msg ->
                            if (msg.id == assistantMsgId) msg.copy(content = errorContent, isError = true)
                            else msg
                        }
                    )
                }
            }
        }
        // Track the job so the Stop button can cancel an in-flight stream.
        streamJob = job
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.stop()
        ttsController.shutdown()
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = SaliZZaiApp.get()
                return ChatViewModel(app.database, app.zaiClient, app.userPrefs, app) as T
            }
        }
    }
}
