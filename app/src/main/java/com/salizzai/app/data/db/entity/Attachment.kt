package com.salizzai.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("messageId")]
)
data class Attachment(
    @PrimaryKey
    val id: String,
    val messageId: String?,
    val conversationId: String?,
    val filename: String,
    val originalName: String,
    val mimeType: String,
    val size: Long,
    val fileType: String = "file", // "file" or "zip"
    val extractedText: String? = null,
    val fileTree: String? = null, // JSON
    val fileCount: Int = 0,
    val totalSize: Long = 0,
    val analysis: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
