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
    val model: String = "glm-4.6",
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
    val contextWindow: Int = 128_000
)

object AvailableModels {
    val ALL = listOf(
        ZaiModel(
            id = "glm-4.6",
            displayName = "GLM-4.6",
            description = "جدیدترین و قدرتمندترین مدل Z.ai",
            supportsThinking = true,
            supportsVision = true,
            badge = "پیشنهادی",
            contextWindow = 200_000
        ),
        ZaiModel(
            id = "glm-4.5v",
            displayName = "GLM-4.5V",
            description = "نسخه بینایی — درک تصویر و سند",
            supportsThinking = false,
            supportsVision = true,
            badge = "بینایی",
            contextWindow = 64_000
        ),
        ZaiModel(
            id = "glm-4.5",
            displayName = "GLM-4.5",
            description = "نسخه پایدار قبلی",
            supportsThinking = true,
            supportsVision = false,
            contextWindow = 128_000
        ),
        ZaiModel(
            id = "glm-4-plus",
            displayName = "GLM-4-Plus",
            description = "نسخه پلاس — تعادل بهینه",
            supportsThinking = false,
            supportsVision = false,
            contextWindow = 128_000
        ),
        ZaiModel(
            id = "glm-4-air",
            displayName = "GLM-4-Air",
            description = "سریع و سبک برای پاسخ‌های فوری",
            badge = "سریع",
            contextWindow = 8_000
        ),
        ZaiModel(
            id = "glm-4-airx",
            displayName = "GLM-4-AirX",
            description = "فوق سریع — ایده‌آل برای چت سبک",
            badge = "فوق سریع",
            contextWindow = 8_000
        ),
        ZaiModel(
            id = "glm-4-flash",
            displayName = "GLM-4-Flash",
            description = "رایگان و مناسب کاربردهای ساده",
            badge = "رایگان",
            contextWindow = 8_000
        )
    )

    /** Pick the right model for vision input. */
    fun pickForVision(currentModelId: String): String {
        val current = ALL.firstOrNull { it.id == currentModelId }
        if (current?.supportsVision == true) return current.id
        return "glm-4.5v"
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
