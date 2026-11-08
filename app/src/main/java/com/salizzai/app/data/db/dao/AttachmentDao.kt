package com.salizzai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.salizzai.app.data.db.entity.Attachment

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: Attachment): Long

    @Update
    suspend fun update(attachment: Attachment)

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: String): Attachment?

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun getForMessage(messageId: String): List<Attachment>

    @Query("SELECT * FROM attachments WHERE conversationId = :conversationId")
    suspend fun getForConversation(conversationId: String): List<Attachment>

    /** Only the not-yet-sent attachments (messageId is NULL) should reappear as pending chips. */
    @Query("SELECT * FROM attachments WHERE conversationId = :conversationId AND messageId IS NULL")
    suspend fun getPendingForConversation(conversationId: String): List<Attachment>

    /** Links attachments to their message and conversation so they are never orphaned. */
    @Query("UPDATE attachments SET messageId = :messageId, conversationId = :conversationId WHERE id IN (:ids)")
    suspend fun linkToMessage(ids: List<String>, messageId: String, conversationId: String)

    @Query("UPDATE attachments SET analysis = :analysis WHERE id = :id")
    suspend fun setAnalysis(id: String, analysis: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)
}
