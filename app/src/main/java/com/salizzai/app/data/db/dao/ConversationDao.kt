package com.salizzai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.salizzai.app.data.db.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation): Long

    @Update
    suspend fun update(conversation: Conversation)

    @Query("SELECT * FROM conversations WHERE userId = :userId OR userId IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeForUser(userId: String?): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): Conversation?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: String): Flow<Conversation?>

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE conversations SET updatedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations WHERE userId = :userId OR userId IS NULL")
    suspend fun deleteAllForUser(userId: String?)

    @Query("SELECT COUNT(*) FROM conversations WHERE userId = :userId OR userId IS NULL")
    suspend fun countForUser(userId: String?): Int
}
