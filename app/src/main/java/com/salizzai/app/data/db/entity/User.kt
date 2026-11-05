package com.salizzai.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String,
    val email: String,
    val username: String?,
    val name: String?,
    val passwordHash: String,           // empty string if Google/OTP-only account
    val authProvider: String = "local", // "local" | "google" | "otp"
    val isGuest: Boolean = false,
    val avatarUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
