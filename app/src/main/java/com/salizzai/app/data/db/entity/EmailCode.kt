package com.salizzai.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One-time passcode sent to the user's email for passwordless login.
 * Stored locally so it can be verified on-device.
 */
@Entity(tableName = "email_codes")
data class EmailCode(
    @PrimaryKey
    val email: String,
    val codeHash: String,          // PBKDF2 hash of the OTP code
    val purpose: String,          // "login" | "signup"
    val expiresAt: Long,          // epoch millis
    val attempts: Int = 0,        // tracks failed attempts (max 5)
    val createdAt: Long = System.currentTimeMillis()
)
