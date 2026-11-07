package com.salizzai.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class Message(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val model: String? = null,
    val tokensUsed: Int = 0,
    val isError: Boolean = false,
    /** Streamed `reasoning_content` for this answer (empty when thinking was off). */
    val reasoning: String = "",
    /** Wall-clock time the model spent thinking, in milliseconds. */
    val reasoningMs: Long = 0,
    /**
     * JSON array of web-search sources used to ground this answer, or null.
     * Stored as text so the sources survive app restarts.
     */
    val sources: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasReasoning: Boolean get() = reasoning.isNotBlank()
}
