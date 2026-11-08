package com.salizzai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.salizzai.app.data.db.entity.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: String): Flow<User?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE email = :email OR username = :username LIMIT 1")
    suspend fun findByEmailOrUsername(email: String, username: String): User?

    @Query("SELECT * FROM users WHERE isGuest = 1 LIMIT 1")
    suspend fun getGuestUser(): User?

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int
}
