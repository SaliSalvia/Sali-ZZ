package com.salizzai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salizzai.app.data.db.entity.EmailCode

@Dao
interface EmailCodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(code: EmailCode)

    @Query("SELECT * FROM email_codes WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): EmailCode?

    @Query("UPDATE email_codes SET attempts = attempts + 1 WHERE email = :email")
    suspend fun incrementAttempt(email: String)

    @Query("DELETE FROM email_codes WHERE email = :email")
    suspend fun deleteByEmail(email: String)

    @Query("DELETE FROM email_codes WHERE expiresAt < :before")
    suspend fun purgeExpired(before: Long = System.currentTimeMillis())
}
