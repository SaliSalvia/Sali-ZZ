package com.salizzai.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.salizzai.app.data.db.dao.AttachmentDao
import com.salizzai.app.data.db.dao.ConversationDao
import com.salizzai.app.data.db.dao.EmailCodeDao
import com.salizzai.app.data.db.dao.MessageDao
import com.salizzai.app.data.db.dao.UserDao
import com.salizzai.app.data.db.entity.Attachment
import com.salizzai.app.data.db.entity.Conversation
import com.salizzai.app.data.db.entity.EmailCode
import com.salizzai.app.data.db.entity.Message
import com.salizzai.app.data.db.entity.User

/**
 * Local Room database — no server needed.
 * All data stored on-device. All authentication happens locally.
 *
 * Authentication model:
 *  - Email + password: PBKDF2WithHmacSHA256 hash (see PasswordHasher)
 *  - Email OTP: 6-digit code generated locally, hashed, stored in [email_codes] table
 *  - Google sign-in: only the email is captured via Credential Manager, then a local
 *    user record is created with a random internal id. No Google tokens are stored.
 */
@Database(
    entities = [
        User::class,
        Conversation::class,
        Message::class,
        Attachment::class,
        EmailCode::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun emailCodeDao(): EmailCodeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sali_zzai.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
