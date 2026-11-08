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
import com.salizzai.app.data.model.FileTreeNode
import com.salizzai.app.data.model.ReasoningEffort
import com.salizzai.app.data.model.ThinkingConfig
import com.salizzai.app.data.model.WebSearchResult
import com.salizzai.app.data.model.ZipSnapshot
import com.salizzai.app.data.prefs.UserPreferences
import com.salizzai.app.util.TtsController
import com.salizzai.app.util.VoiceInputController
import com.salizzai.app.util.ZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
    val thinkingEnabled: Boolean = true,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MAX,
    val temperature: Float = 0.7f,
    val systemPrompt: String = "",
    /** Reasoning that is still streaming for the current answer. */
    val pendingReasoning: String = "",
    /** Milliseconds spent thinking on the in-flight answer. */
    val pendingReasoningMs: Long = 0,
    val isListening: Boolean = false,
    val voicePartial: String = "",
    val isSpeaking: Boolean = false,
    val speakingMessageId: String? = null,
    val webSearchEnabled: Boolean = false,
    val isSearchingWeb: Boolean = false,
    val attachmentWarning: String? = null,
    val zipCompareMode: Boolean = false
) {
    val canSend: Boolean get() = !isStreaming && (draftInput.isNotBlank() || attachments.isNotEmpty())
}

class ChatViewModel(
    private val database: AppDatabase,
    private val zaiClient: ZaiApiClient,
    private val userPrefs: UserPreferences,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** The active streaming request, so the user can cancel it with the Stop button. */
    private var streamJob: Job? = null

    val voiceController = VoiceInputController(appContext)
    val ttsController = TtsController(appContext)

    init {
        viewModelScope.launch {
            userPrefs.model.collectLatest { m -> _uiState.update { it.copy(model = m) } }
        }
        viewModelScope.launch {
            userPrefs.temperature.collectLatest { t ->
                _uiState.update { it.copy(temperature = t.coerceIn(0f, 1f)) }
            }
        }
        viewModelScope.launch {
            userPrefs.thinkingEnabled.collectLatest { e ->
                _uiState.update { it.copy(thinkingEnabled = e) }
            }
        }
        viewModelScope.launch {
            userPrefs.reasoningEffort.collectLatest { e ->
                _uiState.update { it.copy(reasoningEffort = ReasoningEffort.from(e)) }
            }
        }
        viewModelScope.launch {
            userPrefs.systemPrompt.collectLatest { p ->
                _uiState.update { it.copy(systemPrompt = p) }
            }
        }
        viewModelScope.launch {
            userPrefs.webSearchEnabled.collectLatest { e ->
                _uiState.update { it.copy(webSearchEnabled = e) }
            }
        }
        // Voice input
        viewModelScope.launch {
            voiceController.listening.collectLatest { listening ->
                _uiState.update { it.copy(isListening = listening) }
            }
        }
        viewModelScope.launch {
            voiceController.partial.collectLatest { partial ->
                if (partial.isNotBlank()) _uiState.update { it.copy(voicePartial = partial) }
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
                if (!e.isNullOrBlank()) _uiState.update { it.copy(error = e) }
            }
        }
        // Speech state is driven by the engine, so the button resets by itself.
        viewModelScope.launch {
            ttsController.speaking.collectLatest { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }
        viewModelScope.launch {
            ttsController.speakingId.collectLatest { id ->
                _uiState.update { it.copy(speakingMessageId = id) }
            }
        }
    }

    fun setConversationId(id: String?) {
        // Never swap conversations underneath an active stream.
        if (_uiState.value.isStreaming) stopGeneration()
        _uiState.update { it.copy(conversationId = id) }
        if (id != null) {
            viewModelScope.launch {
                database.conversationDao().getById(id)?.let { conv ->
                    _uiState.update { it.copy(conversationTitle = conv.title, model = conv.model) }
                }
                val msgs = database.messageDao().getForConversation(id)
                val atts = database.attachmentDao().getPendingForConversation(id)
                _uiState.update { it.copy(messages = msgs, attachments = atts) }
            }
        } else {
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    attachments = emptyList(),
                    pendingImageUrls = emptyList(),
                    conversationTitle = "گفتگوی جدید",
                    pendingReasoning = "",
                    pendingReasoningMs = 0
                )
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

    fun toggleWebSearch() {
        viewModelScope.launch {
            val next = !_uiState.value.webSearchEnabled
            userPrefs.setWebSearchEnabled(next)
        }
    }

    fun setModel(modelId: String) {
        viewModelScope.launch { userPrefs.setModel(modelId) }
    }

    fun setReasoningEffort(effort: ReasoningEffort) {
        viewModelScope.launch { userPrefs.setReasoningEffort(effort.apiValue) }
    }

    // ─── Voice input ───────────────────────────────────────────────────

    fun startVoiceInput() = voiceController.start()

    fun stopVoiceInput() = voiceController.stop()

    // ─── TTS ───────────────────────────────────────────────────────────

    fun speakReply(text: String, messageId: String? = null) {
        ttsController.speak(text, messageId)
    }

    fun stopSpeaking() = ttsController.stop()

    // ─── Stop generation ────────────────────────────────────────────────

    /**
     * Cancels the in-flight streaming response (Stop button) and stops TTS.
     * Partial output already flushed by the collector is kept in the database.
     */
    fun stopGeneration() {
        streamJob?.cancel()
        streamJob = null
        ttsController.stop()
        _uiState.update {
            it.copy(
                isStreaming = false,
                pendingReasoning = "",
                pendingReasoningMs = 0,
                isSearchingWeb = false
            )
        }
    }

    // ─── File / image / ZIP processing ─────────────────────────────────

    suspend fun processZipFile(filename: String, mimeType: String, bytes: ByteArray): Attachment {
        return withContext(Dispatchers.IO) {
            val result = ZipExtractor.extract(bytes, filename)
            val fileTreeJson = Json.encodeToString(
                ListSerializer(FileTreeNode.serializer()),
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
            _uiState.update { it.copy(attachments = it.attachments + attachment) }
            attachment
        }
    }

    suspend fun processRegularFile(filename: String, mimeType: String, bytes: ByteArray): Attachment {
        return withContext(Dispatchers.IO) {
            val ext = filename.substringAfterLast(".", "").lowercase()
            val isText = ext in ZipExtractor.TEXT_EXTENSIONS && bytes.size < 1024 * 1024
            val extractedText = if (isText) {
                val content = bytes.toString(Charsets.UTF_8)
                val truncated = if (content.length > 8_000) content.take(8_000) + "\n... [متوقف شد]" else content
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
            _uiState.update { it.copy(attachments = it.attachments + attachment) }
            attachment
        }
    }

    /**
     * Stores image metadata and keeps the base64 data URL in memory so the next
     * outgoing message carries it as a multimodal `image_url` part.
     */
    suspend fun processImageFile(filename: String, mimeType: String, bytes: ByteArray): Attachment {
        return withContext(Dispatchers.IO) {
            // Z.ai rejects images above ~5 MB and 6000x6000 px. Modern phone
            // photos easily exceed that, so they are downscaled before encoding.
            val prepared = downscaleForUpload(bytes)
            val dataUrl = zaiClient.encodeImageAsDataUrl(prepared.bytes, prepared.mimeType)
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
            _uiState.update {
                it.copy(
                    attachments = it.attachments + attachment,
                    pendingImageUrls = it.pendingImageUrls + dataUrl
                )
            }
            attachment
        }
    }

    private data class PreparedImage(val bytes: ByteArray, val mimeType: String)

    /**
     * Downscales a photo until it comfortably fits Z.ai's vision limits.
     * Falls back to the original bytes when it is already small or undecodable.
     */
    private fun downscaleForUpload(bytes: ByteArray): PreparedImage {
        val maxSide = 1600
        val maxBytes = 3_500_000
        if (bytes.size <= maxBytes && bytes.size <= 1_500_000) {
            return PreparedImage(bytes, "image/jpeg")
        }
        return runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching PreparedImage(bytes, "image/jpeg")

            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2
            }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@runCatching PreparedImage(bytes, "image/jpeg")

            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            val compressed = out.toByteArray()
            if (compressed.isNotEmpty() && compressed.size < bytes.size) {
                PreparedImage(compressed, "image/jpeg")
            } else {
                PreparedImage(bytes, "image/jpeg")
            }
        }.getOrDefault(PreparedImage(bytes, "image/jpeg"))
    }

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
        val snapshot = _uiState.value
        if (snapshot.isStreaming) return
        if (text.isBlank() && snapshot.attachments.isEmpty()) return

        val attachments = snapshot.attachments
        val userText = text
        val imageUrls = snapshot.pendingImageUrls
        val attachmentIds = attachments.map { it.id }

        streamJob = viewModelScope.launch {
            runTurn(
                userText = userText,
                attachments = attachments,
                attachmentIds = attachmentIds,
                imageUrls = imageUrls
            )
        }
    }

    /** Drops the last answer and asks again with the same prompt. */
    fun regenerate() {
        val state = _uiState.value
        if (state.isStreaming) return
        val lastAssistant = state.messages.lastOrNull { it.role == "assistant" } ?: return
        val lastUser = state.messages.lastOrNull { it.role == "user" } ?: return
        if (lastAssistant.createdAt < lastUser.createdAt) return

        streamJob = viewModelScope.launch {
            database.messageDao().deleteById(lastAssistant.id)
            _uiState.update { it.copy(messages = it.messages.filterNot { m -> m.id == lastAssistant.id }) }
            runTurn(
                userText = lastUser.content,
                attachments = emptyList(),
                attachmentIds = emptyList(),
                imageUrls = emptyList(),
                insertUserMessage = false
            )
        }
    }

    private suspend fun runTurn(
        userText: String,
        attachments: List<Attachment>,
        attachmentIds: List<String>,
        imageUrls: List<String>,
        insertUserMessage: Boolean = true
    ) {
        val state = _uiState.value

        val zipAttachments = attachments.filter { it.fileType == "zip" }
        val fileAttachments = attachments.filter { it.fileType == "file" }
        val hasImages = imageUrls.isNotEmpty()

        // Create the conversation lazily so an abandoned empty chat leaves no row.
        var convId = state.conversationId
        if (convId == null) {
            convId = UUID.randomUUID().toString()
            val title = userText.take(48).trim().ifBlank {
                zipAttachments.firstOrNull()?.originalName ?: "گفتگوی جدید"
            }
            val conv = Conversation(
                id = convId,
                title = title,
                model = state.model,
                userId = userPrefs.loggedInUserIdSnapshot()
            )
            database.conversationDao().insert(conv)
            _uiState.update { it.copy(conversationId = convId, conversationTitle = conv.title) }
        }

        val userMsgId = UUID.randomUUID().toString()
        if (insertUserMessage) {
            val userMsg = Message(
                id = userMsgId,
                conversationId = convId,
                role = "user",
                content = userText
            )
            database.messageDao().insert(userMsg)
            if (attachmentIds.isNotEmpty()) {
                database.attachmentDao().linkToMessage(attachmentIds, userMsgId, convId)
            }
            _uiState.update {
                it.copy(
                    messages = it.messages + userMsg,
                    draftInput = "",
                    attachments = if (attachmentIds.isEmpty()) it.attachments else emptyList(),
                    pendingImageUrls = if (hasImages) emptyList() else it.pendingImageUrls
                )
            }
        }

        // GLM-5.3 / 5.2 are text-only: an image forces the multimodal Flash model.
        val effectiveModel = if (hasImages) {
            AvailableModels.pickForVision(state.model)
        } else state.model

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
            it.copy(
                messages = it.messages + assistantMsg,
                isStreaming = true,
                pendingReasoning = "",
                pendingReasoningMs = 0
            )
        }

        // ── Optional web-search grounding ──────────────────────────────
        var sources: List<WebSearchResult> = emptyList()
        if (state.webSearchEnabled && userText.isNotBlank()) {
            _uiState.update { it.copy(isSearchingWeb = true) }
            sources = zaiClient.webSearch(userText, count = 6).getOrElse { emptyList() }
            _uiState.update { it.copy(isSearchingWeb = false) }
            if (sources.isNotEmpty()) {
                database.messageDao().setSources(
                    assistantMsgId,
                    Json.encodeToString(ListSerializer(WebSearchResult.serializer()), sources)
                )
            }
        }

        val attachmentContext = buildAttachmentContext(zipAttachments, fileAttachments)
        val searchContext = buildSearchContext(sources)

        val requestMessages = mutableListOf<ChatMessage>()
        if (state.systemPrompt.isNotBlank()) {
            requestMessages.add(ChatMessage("system", state.systemPrompt))
        }
        requestMessages.add(ChatMessage("system", DEFAULT_SYSTEM_PROMPT))
        // On a retry the previous user turn is already part of the history, so it
        // must not be appended a second time as a new user message.
        val historySource = if (insertUserMessage) {
            state.messages
        } else {
            state.messages.dropLastWhile { it.role == "user" }
        }
        requestMessages.addAll(buildHistory(historySource))

        val prompt = buildString {
            append(userText)
            if (attachmentContext.isNotBlank()) {
                append("\n\n[محتوای فایل‌های پیوست برای تحلیل]:\n")
                append(attachmentContext)
            }
            if (searchContext.isNotBlank()) {
                append("\n\n[نتایج جستجوی وب — امروز]:\n")
                append(searchContext)
                append("\nاز این منابع استفاده کن و در پایان پاسخ، منابع را با شماره ذکر کن.")
            }
        }
        requestMessages.add(ChatMessage("user", prompt, images = imageUrls))

        val request = ChatCompletionRequest(
            model = effectiveModel,
            messages = requestMessages,
            thinking = ThinkingConfig(
                type = if (state.thinkingEnabled) "enabled" else "disabled",
                clearThinking = false
            ),
            temperature = state.temperature.toDouble(),
            stream = true
        )

        val responseBuffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        var lastFlush = System.currentTimeMillis()
        var lastReasoningFlush = lastFlush
        var reasoningStartedAt = 0L
        var reasoningEndedAt = 0L
        // `Done` can arrive twice ([DONE] event + onClosed); finishing must be idempotent.
        var finished = false

        suspend fun flushResponse() {
            if (responseBuffer.isEmpty()) return
            val chunk = responseBuffer.toString()
            responseBuffer.setLength(0)
            database.messageDao().appendContent(assistantMsgId, chunk)
            _uiState.update { st ->
                st.copy(
                    messages = st.messages.map { m ->
                        if (m.id == assistantMsgId) m.copy(content = m.content + chunk) else m
                    }
                )
            }
        }

        suspend fun flushReasoning() {
            if (reasoningBuffer.isEmpty()) return
            val chunk = reasoningBuffer.toString()
            reasoningBuffer.setLength(0)
            database.messageDao().appendReasoning(assistantMsgId, chunk)
            _uiState.update { st ->
                st.copy(
                    pendingReasoning = st.pendingReasoning + chunk,
                    pendingReasoningMs = if (reasoningStartedAt > 0) {
                        ((reasoningEndedAt.takeIf { it > 0 } ?: System.currentTimeMillis()) - reasoningStartedAt)
                    } else 0L
                )
            }
        }

        try {
            zaiClient.streamChatCompletion(
                request = request,
                reasoningEffort = state.reasoningEffort,
                thinkingEnabled = state.thinkingEnabled
            ).collect { event ->
                when (event) {
                    is ZaiApiClient.StreamEvent.Reasoning -> {
                        if (reasoningStartedAt == 0L) reasoningStartedAt = System.currentTimeMillis()
                        reasoningBuffer.append(event.content)
                        val now = System.currentTimeMillis()
                        if (reasoningBuffer.length >= 96 || now - lastReasoningFlush >= 90L) {
                            flushReasoning()
                            lastReasoningFlush = now
                        }
                    }
                    is ZaiApiClient.StreamEvent.Delta -> {
                        if (reasoningStartedAt > 0 && reasoningEndedAt == 0L) {
                            reasoningEndedAt = System.currentTimeMillis()
                            flushReasoning()
                        }
                        responseBuffer.append(event.content)
                        val now = System.currentTimeMillis()
                        if (responseBuffer.length >= 64 || now - lastFlush >= 80L) {
                            flushResponse()
                            lastFlush = now
                        }
                    }
                    is ZaiApiClient.StreamEvent.Usage -> {
                        database.messageDao().setTokens(assistantMsgId, event.totalTokens)
                    }
                    is ZaiApiClient.StreamEvent.Finished -> Unit
                    is ZaiApiClient.StreamEvent.Done -> {
                        if (finished) return@collect
                        finished = true
                        if (reasoningEndedAt == 0L && reasoningStartedAt > 0) {
                            reasoningEndedAt = System.currentTimeMillis()
                        }
                        flushReasoning()
                        flushResponse()
                        if (reasoningStartedAt > 0) {
                            database.messageDao().setReasoningDuration(
                                assistantMsgId,
                                (reasoningEndedAt.takeIf { it > 0 } ?: System.currentTimeMillis()) - reasoningStartedAt
                            )
                        }
                        database.messageDao().markError(assistantMsgId, false)
                        database.conversationDao().touch(convId)
                        val finalReasoningMs = if (reasoningStartedAt > 0) {
                            (reasoningEndedAt.takeIf { it > 0 } ?: System.currentTimeMillis()) - reasoningStartedAt
                        } else 0L
                        _uiState.update { st ->
                            val transcript = st.pendingReasoning
                            st.copy(
                                isStreaming = false,
                                pendingReasoning = "",
                                pendingReasoningMs = 0,
                                isSearchingWeb = false,
                                messages = st.messages.map { m ->
                                    if (m.id == assistantMsgId) {
                                        // Keep the transcript on the message: clearing
                                        // pendingReasoning must not erase the card.
                                        m.copy(
                                            reasoning = transcript,
                                            reasoningMs = finalReasoningMs,
                                            isError = false
                                        )
                                    } else m
                                }
                            )
                        }
                        maybeAutoSpeak(assistantMsgId)
                    }
                    is ZaiApiClient.StreamEvent.Error -> {
                        flushReasoning()
                        flushResponse()
                        val errorContent = "❌ خطا: ${event.message}"
                        database.messageDao().updateContent(assistantMsgId, errorContent)
                        database.messageDao().markError(assistantMsgId, true)
                        _uiState.update { st ->
                            val transcript = st.pendingReasoning
                            st.copy(
                                isStreaming = false,
                                error = event.message,
                                pendingReasoning = "",
                                pendingReasoningMs = 0,
                                isSearchingWeb = false,
                                messages = st.messages.map { m ->
                                    if (m.id == assistantMsgId) {
                                        m.copy(
                                            content = errorContent,
                                            reasoning = transcript,
                                            isError = true
                                        )
                                    } else m
                                }
                            )
                        }
                    }
                    is ZaiApiClient.StreamEvent.Open -> Unit
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Flush partial work, then let cancellation propagate. Whatever reached
            // the database is mirrored back into the UI so a stopped answer keeps
            // its partial text and reasoning instead of looking empty.
            withContext(kotlinx.coroutines.NonCancellable) {
                flushReasoning()
                flushResponse()
                val persisted = database.messageDao().getById(assistantMsgId)
                _uiState.update { st ->
                    st.copy(
                        isStreaming = false,
                        isSearchingWeb = false,
                        messages = if (persisted == null) st.messages else st.messages.map { m ->
                            if (m.id == assistantMsgId) {
                                m.copy(
                                    content = persisted.content,
                                    reasoning = persisted.reasoning,
                                    reasoningMs = persisted.reasoningMs
                                )
                            } else m
                        }
                    )
                }
            }
            throw e
        } catch (e: Exception) {
            val errorContent = "❌ خطای غیرمنتظره: ${e.message}"
            database.messageDao().updateContent(assistantMsgId, errorContent)
            database.messageDao().markError(assistantMsgId, true)
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    error = e.message,
                    pendingReasoning = "",
                    pendingReasoningMs = 0,
                    isSearchingWeb = false,
                    messages = it.messages.map { m ->
                        if (m.id == assistantMsgId) m.copy(content = errorContent, isError = true) else m
                    }
                )
            }
        }
    }

    private suspend fun maybeAutoSpeak(messageId: String) {
        val message = database.messageDao().getById(messageId) ?: return
        if (message.content.isBlank() || message.isError) return
        if (!userPrefs.ttsEnabled.first()) return
        ttsController.speak(message.content, messageId)
    }

    /**
     * Multi-ZIP comparison context.
     *
     * Earlier builds rebuilt [ZipSnapshot]s with empty maps, so the "automatic
     * comparison" section never contained anything. The stored file tree and
     * per-archive text are now parsed back into real snapshots, which makes
     * `ZipExtractor.compareZips` produce an actual diff.
     */
    private fun buildAttachmentContext(
        zips: List<Attachment>,
        files: List<Attachment>
    ): String = buildString {
        files.filter { !it.extractedText.isNullOrEmpty() }.forEach { att ->
            append("--- فایل پیوست: ${att.originalName} ---\n${att.extractedText}\n--- پایان ---\n\n")
        }
        when {
            zips.size >= 2 -> {
                val snapshots = zips.map { it.toSnapshot() }
                val comparison = ZipExtractor.compareZips(snapshots)
                append("--- مقایسهٔ ${zips.size} بستهٔ ZIP ---\n\n")
                append(comparison.summary)
                append("\n### محتوای متنی هر بسته\n\n")
                zips.forEach { att ->
                    append("=== ${att.originalName} (${att.fileCount} فایل) ===\n")
                    append(att.extractedText ?: "(بدون متن استخراج‌شده)")
                    append("\n\n")
                }
                append("--- پایان مقایسه ---\n\n")
            }
            zips.size == 1 -> {
                val att = zips.first()
                append("--- فایل ZIP: ${att.originalName} (شامل ${att.fileCount} فایل) ---\n")
                append(att.extractedText ?: "(بدون متن استخراج‌شده)")
                append("\n--- پایان ---\n\n")
            }
        }
    }

    private fun Attachment.toSnapshot(): ZipSnapshot {
        val nodes = runCatching {
            Json.decodeFromString(ListSerializer(FileTreeNode.serializer()), fileTree ?: "[]")
        }.getOrDefault(emptyList())

        val filesByPath = mutableMapOf<String, Long>()
        val extCounts = mutableMapOf<String, Int>()
        fun walk(list: List<FileTreeNode>) {
            list.forEach { node ->
                if (node.isDirectory) {
                    walk(node.children)
                } else {
                    filesByPath[node.path] = node.size
                    val ext = node.ext?.removePrefix(".")?.lowercase().orEmpty().ifBlank { "(بدون پسوند)" }
                    extCounts[ext] = (extCounts[ext] ?: 0) + 1
                }
            }
        }
        walk(nodes)

        return ZipSnapshot(
            originalName = originalName,
            fileCount = fileCount.takeIf { it > 0 } ?: filesByPath.size,
            totalSize = totalSize,
            topExtensions = extCounts,
            topLevelDirs = filesByPath.keys.map { it.substringBefore('/') }.distinct().sorted(),
            filesByPath = filesByPath,
            textPreviewByPath = emptyMap()
        )
    }

    private fun buildSearchContext(sources: List<WebSearchResult>): String {
        if (sources.isEmpty()) return ""
        return sources.take(6).mapIndexed { i, s ->
            val host = s.hostName.ifBlank { s.url.substringAfter("//").substringBefore('/') }
            "[${i + 1}] ${s.name}\n$host\n${s.snippet.take(700)}\n${s.url}"
        }.joinToString("\n\n")
    }

    /**
     * Character-budgeted history: keeps the whole recent tail and trims older
     * turns so long conversations do not silently lose their last messages.
     */
    private fun buildHistory(messages: List<Message>): List<ChatMessage> {
        val budget = MAX_HISTORY_CHARS
        val picked = ArrayDeque<ChatMessage>()
        var used = 0
        for (message in messages.asReversed()) {
            if (message.content.isBlank()) continue
            if (message.isError) continue
            val cost = message.content.length + 24
            if (used + cost > budget && picked.isNotEmpty()) break
            picked.addFirst(ChatMessage(message.role, message.content))
            used += cost
        }
        return picked.toList()
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        voiceController.stop()
        ttsController.shutdown()
    }

    companion object {
        /** Roughly 24k characters ≈ 8k tokens of conversation context. */
        private const val MAX_HISTORY_CHARS = 24_000

        private val DEFAULT_SYSTEM_PROMPT = """
            تو Sali-ZZai هستی، دستیار هوشمند Z.ai روی اندروید.
            - به زبان کاربر (فارسی یا انگلیسی) پاسخ بده.
            - برای کد از بلوک‌های Markdown با نام زبان استفاده کن.
            - برای تحلیل ZIP از ساختار درختی و محتوای استخراج‌شده استفاده کن.
            - اگر چند نسخه از یک پروژه/مقاله آپلود شده، تفاوت‌ها و بهبودها را دقیق مقایسه کن.
            - اگر خروجی تو را قطع کردند، از همان‌جا ادامه بده و تکرار نکن.
        """.trimIndent()

        fun provideFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = SaliZZaiApp.get()
                return ChatViewModel(app.database, app.zaiClient, app.userPrefs, app) as T
            }
        }
    }
}
