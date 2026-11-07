package com.salizzai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── API request models ─────────────────────────────────────────────

/**
 * A chat message sent to Z.ai.
 *
 * @param images Optional list of base64-encoded data URLs (`data:image/...;base64,...`)
 *               attached to this message. When non-empty, the request body builder turns
 *               `content` into a multimodal array (text + image_url parts) so that
 *               vision-capable models (e.g. GLM-4V) can read the images.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList()
)

@Serializable
data class ChatCompletionRequest(
    val model: String = AvailableModels.DEFAULT_MODEL,
    val messages: List<ChatMessage>,
    @SerialName("thinking")
    val thinking: ThinkingConfig? = null,
    val temperature: Double? = null,
    val stream: Boolean = true
)

@Serializable
data class ThinkingConfig(
    val type: String = "disabled" // "disabled" | "enabled"
)

// ─── API response models ────────────────────────────────────────────

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChoiceMessage? = null,
    val delta: Delta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ChoiceMessage(
    val role: String = "assistant",
    val content: String = ""
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

// ─── Image generation ───────────────────────────────────────────────

@Serializable
data class ImageGenerationRequest(
    val prompt: String,
    val size: String = "1024x1024",
    val model: String? = null
)

@Serializable
data class ImageGenerationResponse(
    val created: Long = 0,
    val data: List<ImageData> = emptyList()
)

@Serializable
data class ImageData(
    val base64: String? = null,
    val url: String? = null
)

// ─── Web search ─────────────────────────────────────────────────────

@Serializable
data class WebSearchResult(
    val url: String,
    val name: String,
    val snippet: String = "",
    @SerialName("host_name")
    val hostName: String = "",
    val rank: Int = 0,
    val date: String = "",
    val favicon: String = ""
)

// ─── Available models ───────────────────────────────────────────────

data class ZaiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val supportsThinking: Boolean = false,
    val supportsVision: Boolean = false,
    val badge: String? = null,
    val contextWindow: Int = 128_000,
    /** Models the user can chat with for free on chat.z.ai / the free tier. */
    val isFreeTier: Boolean = false
)

/**
 * Built-in model catalogue used before (and alongside) the live list returned by
 * the Z.ai `/models` endpoint.
 *
 * The three GLM-5 models the app prefers by default are free to use on
 * chat.z.ai, so they sit at the top of [PRIORITY] and are the default choice.
 * Older GLM-4.x models stay listed for accounts that only have access to them.
 */
object AvailableModels {

    /** Default model — free on chat.z.ai and the most capable model of the GLM-5 line. */
    const val DEFAULT_MODEL = "glm-5.3"

    /** Preference order: free GLM-5 models first, then fallbacks. */
    val PRIORITY: List<String> = listOf(
        "glm-5.3",
        "glm-5.2",
        "glm-5.3-flash",
        "glm-5.3-flashx",
        "glm-5v-turbo",
        "glm-4.7-flash",
        "glm-4.6",
        "glm-4.5",
        "glm-4.5-flash"
    )

    val ALL: List<ZaiModel> = listOf(
        ZaiModel(
            id = "glm-5.3",
            displayName = "GLM-5.3",
            description = "پرچم‌دار Z.ai — کدنویسی، استدلال و عامل‌های هوشمند",
            supportsThinking = true,
            supportsVision = true,
            badge = "رایگان",
            contextWindow = 1_000_000,
            isFreeTier = true
        ),
        ZaiModel(
            id = "glm-5.2",
            displayName = "GLM-5.2",
            description = "نسل قبل پرچم‌دار — پایدار با پنجرهٔ یک‌میلیونی",
            supportsThinking = true,
            supportsVision = true,
            badge = "رایگان",
            contextWindow = 1_000_000,
            isFreeTier = true
        ),
        ZaiModel(
            id = "glm-5.3-flash",
            displayName = "GLM-5.3 Flash",
            description = "چندوجهی و فوق سریع — سبک‌ترین مدل نسل ۵",
            supportsThinking = true,
            supportsVision = true,
            badge = "رایگان",
            contextWindow = 1_000_000,
            isFreeTier = true
        ),
        ZaiModel(
            id = "glm-5.3-flashx",
            displayName = "GLM-5.3 FlashX",
            description = "نسخهٔ پرسرعت Flash با خروجی تا ۲۰۰ توکن بر ثانیه",
            supportsThinking = true,
            supportsVision = true,
            badge = "سریع",
            contextWindow = 1_000_000
        ),
        ZaiModel(
            id = "glm-5v-turbo",
            displayName = "GLM-5V Turbo",
            description = "درک تصویر و ویدیو با سرعت بالا",
            supportsVision = true,
            badge = "بینایی",
            contextWindow = 200_000
        ),
        ZaiModel(
            id = "glm-4.7-flash",
            displayName = "GLM-4.7 Flash",
            description = "رایگان روی API — پاسخ‌های سریع و سبک",
            badge = "رایگان",
            contextWindow = 128_000,
            isFreeTier = true
        ),
        ZaiModel(
            id = "glm-4.6",
            displayName = "GLM-4.6",
            description = "مدل نسل قبل — سازگار با کلیدهای قدیمی‌تر",
            supportsThinking = true,
            supportsVision = true,
            contextWindow = 200_000
        ),
        ZaiModel(
            id = "glm-4.5v",
            displayName = "GLM-4.5V",
            description = "نسخهٔ بینایی — درک تصویر و سند",
            supportsVision = true,
            badge = "بینایی",
            contextWindow = 64_000
        ),
        ZaiModel(
            id = "glm-4.5",
            displayName = "GLM-4.5",
            description = "نسخهٔ پایدار قبلی",
            supportsThinking = true,
            contextWindow = 128_000
        ),
        ZaiModel(
            id = "glm-4.5-flash",
            displayName = "GLM-4.5 Flash",
            description = "رایگان روی API — مناسب کاربردهای ساده",
            badge = "رایگان",
            contextWindow = 128_000,
            isFreeTier = true
        )
    )

    /** Normalises ids such as `"GLM 5.3 Flash"` → `"glm-5.3-flash"`. */
    fun normalizeId(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), "-")

    fun byId(id: String): ZaiModel? =
        ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun displayName(id: String): String = byId(id)?.displayName ?: id

    /**
     * Full picker list: known models in preference order, followed by any extra
     * ids the API reported that the app does not know about yet.
     */
    fun catalog(remoteIds: Set<String> = emptySet()): List<ZaiModel> {
        val seen = mutableSetOf<String>()
        val items = mutableListOf<ZaiModel>()
        PRIORITY.forEach { id ->
            byId(id)?.let { model ->
                if (seen.add(model.id)) items += model
            }
        }
        ALL.forEach { model ->
            if (seen.add(model.id)) items += model
        }
        remoteIds.map(::normalizeId)
            .filter { it.isNotBlank() }
            .sorted()
            .forEach { id ->
                if (seen.add(id)) {
                    items += ZaiModel(
                        id = id,
                        displayName = id.uppercase(),
                        description = "مدل گزارش‌شده توسط حساب Z.ai شما",
                        badge = "از API"
                    )
                }
            }
        return items
    }

    /**
     * Returns the best usable model id from a live model list, falling back to
     * the preferred order. Returns `null` when the list is empty.
     */
    fun bestAvailable(ids: Collection<String>): String? {
        val normalized = ids.map(::normalizeId).filter { it.isNotBlank() }
        if (normalized.isEmpty()) return null
        PRIORITY.firstOrNull { it in normalized }?.let { return it }
        normalized.firstOrNull { it.contains("flash") }?.let { return it }
        return normalized.first()
    }

    /** Pick the right model for vision input. */
    fun pickForVision(currentModelId: String): String {
        val current = byId(currentModelId)
        if (current?.supportsVision == true) return current.id
        return "glm-5.3-flash"
    }
}

// ─── File tree models ───────────────────────────────────────────────

@Serializable
data class FileTreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val ext: String? = null,
    val children: List<FileTreeNode> = emptyList()
)

// ─── ZIP comparison (multi-ZIP analysis) ────────────────────────────

/**
 * Snapshot of a single ZIP for comparison purposes.
 * Two or more of these are bundled into [ZipComparison] for diffing.
 */
data class ZipSnapshot(
    val originalName: String,
    val fileCount: Int,
    val totalSize: Long,
    val topExtensions: Map<String, Int>,
    val topLevelDirs: List<String>,
    val filesByPath: Map<String, Long>, // path → size
    val textPreviewByPath: Map<String, String>
)

/**
 * Result of comparing two or more ZIPs.
 */
data class ZipComparison(
    val snapshots: List<ZipSnapshot>,
    val commonFiles: List<String>,
    val uniqueFilesBySnapshot: List<List<String>>,
    val sizeDeltas: Map<String, Long>, // path → bytes changed (positive: only in snapshot[1])
    val summary: String
)
