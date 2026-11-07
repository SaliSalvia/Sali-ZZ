package com.salizzai.app.data.api

import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import com.salizzai.app.data.model.AvailableModels
import com.salizzai.app.data.model.ChatCompletionRequest
import com.salizzai.app.data.model.ImageGenerationRequest
import com.salizzai.app.data.model.ImageGenerationResponse
import com.salizzai.app.data.model.WebSearchResult
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Z.ai API client — makes direct HTTPS calls to https://api.z.ai.
 *
 * Supported endpoints:
 *  - `POST /chat/completions` (streaming + non-streaming, text + image attachments → VLM)
 *  - `POST /images/generations`
 *  - `POST /functions/web_search`
 *  - `POST /audio/speech`           (TTS, returns MP3 bytes)
 *  - `POST /audio/transcriptions`   (ASR, accepts audio file → returns text)
 *
 * No server/proxy needed; calls Z.ai from the device itself.
 */
class ZaiApiClient(
    private val securePrefs: SharedPreferences,
    private val cacheDir: File? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS) // overall cap per request
            .pingInterval(20, TimeUnit.SECONDS) // keep-alive for long streams
            .retryOnConnectionFailure(true)
            // Reuse connections across requests for lower latency on follow-up API calls.
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            // Optional HTTP cache (10 MB) when the device cache directory is provided.
            .apply {
                cacheDir?.let { dir ->
                    val cacheFile = File(dir, "zai-http-cache")
                    cache(okhttp3.Cache(cacheFile, 10L * 1024 * 1024))
                }
            }
            .build()
    }

    private val sseClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS) // no timeout for SSE
            .callTimeout(0, TimeUnit.SECONDS)  // SSE streams have no overall cap
            .build()
    }

    private val eventSourceFactory = EventSources.createFactory(sseClient)

    init {
        // Older builds stored the Codex endpoint (/api/v1), which does not serve
        // OpenAI-style /chat/completions. Move that exact default to the general
        // API endpoint once; user-customised URLs are left untouched.
        val saved = securePrefs.getString(KEY_BASE_URL, null)
        if (saved != null && saved.trim().trimEnd('/') == LEGACY_DEFAULT_BASE_URL) {
            securePrefs.edit().putString(KEY_BASE_URL, DEFAULT_BASE_URL).apply()
        }
    }

    // ─── Credentials ───────────────────────────────────────────────────

    private fun getBaseUrl(): String =
        normalizeBaseUrl(securePrefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL))

    private fun getApiKey(): String? =
        securePrefs.getString(KEY_API_KEY, null)

    private fun getChatId(): String? =
        securePrefs.getString(KEY_CHAT_ID, null)

    private fun getUserId(): String? =
        securePrefs.getString(KEY_USER_ID, null)

    fun getGoogleWebClientId(): String? =
        securePrefs.getString(KEY_GOOGLE_CLIENT_ID, null)

    private fun buildAuthHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${getApiKey() ?: ""}",
            "X-Z-AI-From" to "Z",
            "Accept" to "application/json"
        )
        getChatId()?.let { headers["X-Chat-Id"] = it }
        getUserId()?.let { headers["X-User-Id"] = it }
        return headers
    }

    fun hasCredentials(): Boolean = !getApiKey().isNullOrBlank()

    /** Public read of the persisted base URL so Settings can show the saved value. */
    fun getSavedBaseUrl(): String = getBaseUrl()

    /** Public read of the persisted key (used for masked display and re-checks). */
    fun getSavedApiKey(): String? = getApiKey()

    fun setCredentials(
        baseUrl: String?,
        apiKey: String?,
        chatId: String?,
        userId: String?,
        googleWebClientId: String? = null
    ) {
        securePrefs.edit().apply {
            baseUrl?.let { putString(KEY_BASE_URL, normalizeBaseUrl(it)) }
            apiKey?.let { putString(KEY_API_KEY, it.trim()) }
            chatId?.let { putString(KEY_CHAT_ID, it) }
            userId?.let { putString(KEY_USER_ID, it) }
            googleWebClientId?.let { putString(KEY_GOOGLE_CLIENT_ID, it) }
        }.apply()
    }

    fun clearCredentials() {
        securePrefs.edit().apply {
            remove(KEY_API_KEY)
            remove(KEY_CHAT_ID)
            remove(KEY_USER_ID)
        }.apply()
    }

    // ─── API key validation & live model discovery ──────────────────────

    /** Outcome of validating a Z.ai API key against the live API. */
    sealed class KeyCheckResult {
        data class Success(
            val modelIds: List<String>,
            val modelListFromApi: Boolean,
            val workingModel: String,
            val latencyMs: Long
        ) : KeyCheckResult()

        data class Invalid(val message: String) : KeyCheckResult()
        data class Failed(val message: String) : KeyCheckResult()
    }

    /**
     * Verifies [apiKey] before it is persisted and, when the account exposes the
     * `/models` endpoint, returns every model the key can actually use.
     *
     * Strategy:
     *  1. `GET {base}/models` → authoritative key + model list.
     *  2. If that endpoint is missing (404/405) fall back to a 1-token
     *     `POST /chat/completions` probe, walking the preferred models until
     *     one is accepted.
     *
     * Nothing is written to disk here; callers persist only on [Success].
     */
    suspend fun validateKey(
        baseUrl: String = getSavedBaseUrl(),
        apiKey: String = getSavedApiKey().orEmpty(),
        preferredModel: String = AvailableModels.DEFAULT_MODEL
    ): KeyCheckResult = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(baseUrl)
        val key = apiKey.trim()
        if (key.isBlank()) {
            return@withContext KeyCheckResult.Invalid(
                "کلید API خالی است. از chat.z.ai → API Keys یک کلید بسازید."
            )
        }
        if (!base.startsWith("http")) {
            return@withContext KeyCheckResult.Invalid("آدرس Base URL معتبر نیست.")
        }

        val startedAt = System.currentTimeMillis()
        val models = runCatching { requestModels(base, key) }
            .getOrElse { ModelsFetch.Unavailable(it.message ?: "خطای شبکه") }

        when (models) {
            is ModelsFetch.Ok -> {
                if (models.ids.isNotEmpty()) {
                    return@withContext KeyCheckResult.Success(
                        modelIds = models.ids,
                        modelListFromApi = true,
                        workingModel = AvailableModels.bestAvailable(models.ids) ?: preferredModel,
                        latencyMs = System.currentTimeMillis() - startedAt
                    )
                }
                // Key accepted but no model list → still probe a chat call below.
            }
            is ModelsFetch.Unauthorized -> return@withContext KeyCheckResult.Invalid(models.message)
            is ModelsFetch.Unavailable -> Unit
        }

        val candidates = (listOf(AvailableModels.normalizeId(preferredModel)) + AvailableModels.PRIORITY)
            .distinct()
            .take(4)

        var lastError = "اتصال به Z.ai برقرار نشد."
        for (model in candidates) {
            val probe = runCatching { probeChat(base, key, model) }
                .getOrElse { ProbeResult.Failed(it.message ?: "خطای شبکه", true) }
            when (probe) {
                is ProbeResult.Ok -> return@withContext KeyCheckResult.Success(
                    modelIds = emptyList(),
                    modelListFromApi = false,
                    workingModel = model,
                    latencyMs = System.currentTimeMillis() - startedAt
                )
                is ProbeResult.Unauthorized -> return@withContext KeyCheckResult.Invalid(probe.message)
                is ProbeResult.ModelUnavailable -> lastError = probe.message
                is ProbeResult.Failed -> {
                    lastError = probe.message
                    if (!probe.retryable) break
                }
            }
        }
        KeyCheckResult.Failed(lastError)
    }

    /**
     * Fetches the model ids the given key can use. Throws (wrapped in [Result])
     * when the endpoint is unavailable so the UI can keep the built-in list.
     */
    suspend fun fetchModelIds(
        baseUrl: String = getSavedBaseUrl(),
        apiKey: String = getSavedApiKey().orEmpty()
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("کلید API تنظیم نشده است."))
        }
        runCatching {
            when (val models = requestModels(normalizeBaseUrl(baseUrl), key)) {
                is ModelsFetch.Ok -> models.ids
                is ModelsFetch.Unauthorized -> throw IllegalStateException(models.message)
                is ModelsFetch.Unavailable -> throw IllegalStateException(models.reason)
            }
        }
    }

    private val probeClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private sealed class ModelsFetch {
        data class Ok(val ids: List<String>) : ModelsFetch()
        data class Unauthorized(val message: String) : ModelsFetch()
        data class Unavailable(val reason: String) : ModelsFetch()
    }

    private sealed class ProbeResult {
        data object Ok : ProbeResult()
        data class Unauthorized(val message: String) : ProbeResult()
        data class ModelUnavailable(val message: String) : ProbeResult()
        data class Failed(val message: String, val retryable: Boolean) : ProbeResult()
    }

    private fun requestModels(base: String, key: String): ModelsFetch {
        val request = Request.Builder()
            .url("$base/models")
            .header("Authorization", "Bearer $key")
            .header("X-Z-AI-From", "Z")
            .header("Accept", "application/json")
            .get()
            .build()

        probeClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return when {
                response.isSuccessful -> ModelsFetch.Ok(parseModelIds(body))
                response.code == 401 || response.code == 403 ->
                    ModelsFetch.Unauthorized(authErrorMessage(response.code))
                else -> ModelsFetch.Unavailable(httpErrorMessage(response.code, body))
            }
        }
    }

    private fun probeChat(base: String, key: String, model: String): ProbeResult {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "ping")
                }
            }
            put("stream", false)
            put("max_tokens", 1)
            put("temperature", 0.0)
            put("thinking", buildJsonObject { put("type", "disabled") })
        }.toString()

        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("X-Z-AI-From", "Z")
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        probeClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            return when {
                response.isSuccessful -> ProbeResult.Ok
                response.code == 401 || response.code == 403 ->
                    ProbeResult.Unauthorized(authErrorMessage(response.code))
                isModelError(response.code, text) ->
                    ProbeResult.ModelUnavailable("مدل $model با این کلید در دسترس نیست.")
                response.code == 429 || response.code >= 500 ->
                    ProbeResult.Failed(httpErrorMessage(response.code, text), true)
                else -> ProbeResult.Failed(httpErrorMessage(response.code, text), false)
            }
        }
    }

    private fun parseModelIds(body: String): List<String> {
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val array = when (parsed) {
            is JsonArray -> parsed
            is JsonObject -> (parsed["data"] as? JsonArray)
                ?: (parsed["models"] as? JsonArray)
                ?: (parsed["result"] as? JsonArray)
                ?: JsonArray(emptyList())
            else -> return emptyList()
        }
        return array.mapNotNull { element ->
            (element as? JsonObject)?.let { obj ->
                obj["id"]?.jsonPrimitive?.contentOrNull
                    ?: obj["model"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
            }
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun readErrorMessage(body: String): String? {
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val error = parsed["error"]
        if (error is JsonObject) {
            error["message"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank()) return it }
        }
        return parsed["message"]?.jsonPrimitive?.contentOrNull
    }

    private fun httpErrorMessage(code: Int, body: String): String {
        val detail = readErrorMessage(body)
        val base = when {
            code == 401 || code == 403 -> authErrorMessage(code)
            code == 404 -> "مسیر یا مدل پیدا نشد (HTTP 404). Base URL را بررسی کنید."
            code == 429 -> "محدودیت درخواست یا پایان سهمیه (HTTP 429). کمی بعد تلاش کنید."
            code in 500..599 -> "سرور Z.ai در دسترس نیست (HTTP $code). بعداً تلاش کنید."
            else -> "خطای Z.ai (HTTP $code)."
        }
        return if (detail.isNullOrBlank()) base else "$base — $detail"
    }

    private fun authErrorMessage(code: Int): String =
        "کلید API نامعتبر، منقضی یا بدون دسترسی است (HTTP $code). از chat.z.ai → API Keys یک کلید تازه بسازید."

    private fun isModelError(code: Int, body: String): Boolean {
        if (code == 404) return true
        val text = body.lowercase()
        return text.contains("model_not_found") ||
            text.contains("model not found") ||
            text.contains("does not exist") ||
            text.contains("no available model") ||
            text.contains("invalid model") ||
            (text.contains("model") && text.contains("not exist"))
    }

    // ─── Chat completion (streaming) ────────────────────────────────────

    /**
     * Streams chat completion from Z.ai. Emits each token delta as it arrives.
     *
     * Each message's `content` may be a plain string or a list of typed parts
     * (text + image_url) to enable vision (VLM) input on models that support it.
     */
    fun streamChatCompletion(
        request: ChatCompletionRequest
    ): Flow<StreamEvent> = callbackFlow {
        if (!hasCredentials()) {
            trySend(StreamEvent.Error("Z.ai API Key تنظیم نشده. به تنظیمات برو."))
            awaitClose()
            return@callbackFlow
        }

        val requestBody = buildChatRequestBody(request)
        val httpRequest = Request.Builder()
            .url("${getBaseUrl()}/chat/completions")
            .apply {
                buildAuthHeaders().forEach { (k, v) -> header(k, v) }
            }
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val eventSource = eventSourceFactory.newEventSource(httpRequest, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                if (!response.isSuccessful) {
                    trySend(StreamEvent.Error("خطای HTTP ${response.code}: ${response.message}"))
                    eventSource.cancel()
                    return
                }
                trySend(StreamEvent.Open)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    trySend(StreamEvent.Done)
                    return
                }
                try {
                    val parsed = json.parseToJsonElement(data) as JsonObject
                    // Some Z.ai responses embed reasoning_content separately from content.
                    val delta = extractDelta(parsed)
                    val reasoning = extractReasoning(parsed)
                    if (delta != null) {
                        trySend(StreamEvent.Delta(delta))
                    }
                    if (reasoning != null) {
                        trySend(StreamEvent.Reasoning(reasoning))
                    }
                } catch (e: Exception) {
                    // ignore malformed JSON
                }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(StreamEvent.Done)
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = when {
                    response != null && response.code != 200 ->
                        "خطای سرور Z.ai (${response.code})"
                    t != null -> t.message ?: "خطای شبکه"
                    else -> "خطای ناشناخته"
                }
                trySend(StreamEvent.Error(errorMsg))
                channel.close()
            }
        })

        awaitClose {
            eventSource.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun extractDelta(parsed: JsonObject): String? {
        val choicesArr = parsed["choices"]?.let { it as? JsonArray }
        val firstChoice = choicesArr?.firstOrNull()?.let { it as? JsonObject }
        val delta = firstChoice?.get("delta")?.let { it as? JsonObject }
        return delta?.get("content")?.jsonPrimitive?.contentOrNull
    }

    private fun extractReasoning(parsed: JsonObject): String? {
        val choicesArr = parsed["choices"]?.let { it as? JsonArray }
        val firstChoice = choicesArr?.firstOrNull()?.let { it as? JsonObject }
        val delta = firstChoice?.get("delta")?.let { it as? JsonObject }
        return delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
    }

    /**
     * Builds the request body. If any of the user messages carry image attachments,
     * the message's `content` becomes a list of typed parts (text + image_url),
     * which is the OpenAI-compatible multimodal format Z.ai expects for VLM models.
     */
    private fun buildChatRequestBody(request: ChatCompletionRequest): String {
        return buildJsonObject {
            put("model", request.model)
            put("stream", request.stream)
            put("thinking", buildJsonObject {
                put("type", request.thinking?.type ?: "disabled")
            })
            request.temperature?.let { put("temperature", it) }
            putJsonArray("messages") {
                request.messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role)
                        if (msg.images.isEmpty()) {
                            put("content", msg.content)
                        } else {
                            // Multimodal content: text followed by image parts
                            putJsonArray("content") {
                                if (msg.content.isNotBlank()) {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", msg.content)
                                    }
                                }
                                msg.images.forEach { imagePart ->
                                    addJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") {
                                            put("url", imagePart)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    // ─── Image generation ───────────────────────────────────────────────

    suspend fun generateImage(request: ImageGenerationRequest): Result<ImageGenerationResponse> {
        if (!hasCredentials()) {
            return Result.failure(IllegalStateException("Z.ai API Key تنظیم نشده"))
        }
        return runCatching {
            val body = buildJsonObject {
                put("prompt", request.prompt)
                put("size", request.size)
                request.model?.let { put("model", it) }
            }.toString()

            val httpRequest = Request.Builder()
                .url("${getBaseUrl()}/images/generations")
                .apply {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw RuntimeException("HTTP ${response.code}: $errBody")
                }
                val responseBody = response.body?.string()
                    ?: throw RuntimeException("Empty response body")
                json.decodeFromString(ImageGenerationResponse.serializer(), responseBody)
            }
        }
    }

    // ─── Web search ────────────────────────────────────────────────────

    suspend fun webSearch(query: String, num: Int = 5): Result<List<WebSearchResult>> {
        if (!hasCredentials()) {
            return Result.failure(IllegalStateException("Z.ai API Key تنظیم نشده"))
        }
        return runCatching {
            val body = buildJsonObject {
                put("query", query)
                put("num", num)
            }.toString()

            val httpRequest = Request.Builder()
                .url("${getBaseUrl()}/functions/web_search")
                .apply {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw RuntimeException("HTTP ${response.code}: $errBody")
                }
                val responseBody = response.body?.string()
                    ?: throw RuntimeException("Empty response body")
                val parsed = json.parseToJsonElement(responseBody)
                val arr: JsonArray = when (parsed) {
                    is JsonArray -> parsed
                    is JsonObject -> (parsed["results"] as? JsonArray) ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
                arr.mapNotNull { el ->
                    (el as? JsonObject)?.let { obj ->
                        WebSearchResult(
                            url = obj["url"]?.jsonPrimitive?.contentOrNull ?: "",
                            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                            snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull ?: "",
                            hostName = obj["host_name"]?.jsonPrimitive?.contentOrNull ?: "",
                            rank = obj["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                            date = obj["date"]?.jsonPrimitive?.contentOrNull ?: "",
                            favicon = obj["favicon"]?.jsonPrimitive?.contentOrNull ?: ""
                        )
                    }
                }
            }
        }
    }

    // ─── Text-to-Speech (TTS) ──────────────────────────────────────────

    /**
     * Synthesizes audio for [text] using Z.ai's `/audio/speech` endpoint.
     * Returns the raw audio bytes (typically MP3). Caller is responsible for caching.
     */
    suspend fun synthesizeSpeech(
        text: String,
        voice: String = "default",
        format: String = "mp3"
    ): Result<ByteArray> {
        if (!hasCredentials()) {
            return Result.failure(IllegalStateException("Z.ai API Key تنظیم نشده"))
        }
        return runCatching {
            val body = buildJsonObject {
                put("input", text)
                put("voice", voice)
                put("response_format", format)
            }.toString()

            val httpRequest = Request.Builder()
                .url("${getBaseUrl()}/audio/speech")
                .apply {
                    buildAuthHeaders().forEach { (k, v) -> header(k, v) }
                }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw RuntimeException("HTTP ${response.code}: $errBody")
                }
                response.body?.bytes() ?: throw RuntimeException("Empty audio body")
            }
        }
    }

    // ─── Automatic Speech Recognition (ASR) ────────────────────────────

    /**
     * Transcribes an audio file using Z.ai's `/audio/transcriptions` endpoint.
     * Returns the recognized text on success.
     */
    suspend fun transcribeAudio(audioFile: File, model: String = "whisper-1"): Result<String> {
        if (!hasCredentials()) {
            return Result.failure(IllegalStateException("Z.ai API Key تنظیم نشده"))
        }
        return runCatching {
            val mediaType = "audio/*".toMediaType()
            val filePart = MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                audioFile.asRequestBody(mediaType)
            )
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addPart(filePart)
                .build()

            val httpRequest = Request.Builder()
                .url("${getBaseUrl()}/audio/transcriptions")
                .apply {
                    // For multipart, the OkHttp builder sets the Content-Type itself.
                    header("Authorization", "Bearer ${getApiKey() ?: ""}")
                    header("X-Z-AI-From", "Z")
                    getChatId()?.let { header("X-Chat-Id", it) }
                    getUserId()?.let { header("X-User-Id", it) }
                }
                .post(multipart)
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw RuntimeException("HTTP ${response.code}: $errBody")
                }
                val body = response.body?.string()
                    ?: throw RuntimeException("Empty transcription body")
                val parsed = json.parseToJsonElement(body) as JsonObject
                parsed["text"]?.jsonPrimitive?.contentOrNull ?: ""
            }
        }
    }

    // ─── Helper: build a base64 data URL for an image attachment ────────

    fun encodeImageAsDataUrl(bytes: ByteArray, mimeType: String = "image/jpeg"): String {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$b64"
    }

    // ─── Stream event sealed class ──────────────────────────────────────

    sealed class StreamEvent {
        object Open : StreamEvent()
        data class Delta(val content: String) : StreamEvent()
        data class Reasoning(val content: String) : StreamEvent()
        object Done : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }

    companion object {
        private const val KEY_BASE_URL = "zai_base_url"
        private const val KEY_API_KEY = "zai_api_key"
        private const val KEY_CHAT_ID = "zai_chat_id"
        private const val KEY_USER_ID = "zai_user_id"
        private const val KEY_GOOGLE_CLIENT_ID = "google_web_client_id"

        /** Z.ai's general OpenAI-compatible endpoint (see docs.z.ai API reference). */
        const val DEFAULT_BASE_URL = "https://api.z.ai/api/paas/v4"

        /** Endpoint stored by older builds; migrated automatically. */
        private const val LEGACY_DEFAULT_BASE_URL = "https://api.z.ai/api/v1"

        /** Trims, adds a scheme when missing and removes trailing slashes. */
        fun normalizeBaseUrl(raw: String?): String {
            var url = raw?.trim().orEmpty()
            if (url.isEmpty()) url = DEFAULT_BASE_URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            return url.trimEnd('/')
        }
    }
}
