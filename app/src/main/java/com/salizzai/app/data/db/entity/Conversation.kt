package com.salizzai.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.salizzai.app.data.model.AvailableModels

@Entity(
    tableName = "conversations",
    indices = [Index("userId")]
)
data class Conversation(
    @PrimaryKey
    val id: String,
    val title: String,
    val model: String = AvailableModels.DEFAULT_MODEL,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val userId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
