package com.salizzai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.salizzai.app.data.db.entity.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Update
    suspend fun update(message: Message)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getForConversation(conversationId: String): List<Message>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): Message?

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Query("UPDATE messages SET content = content || :append WHERE id = :id")
    suspend fun appendContent(id: String, append: String)

    @Query("UPDATE messages SET isError = :isError WHERE id = :id")
    suspend fun markError(id: String, isError: Boolean)

    @Query("UPDATE messages SET tokensUsed = :tokens WHERE id = :id")
    suspend fun setTokens(id: String, tokens: Int)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun countForConversation(conversationId: String): Int
}
