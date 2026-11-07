package com.salizzai.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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

        /**
         * v2 → v3: assistant messages gained their streamed reasoning, the time the
         * model spent thinking, and the list of web-search sources. Existing rows
         * keep their content; the new columns default to empty.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN sources TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sali_zzai.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Only fall back to a wipe for schemas older than v2 (no migration
                    // path exists there); v2 → v3 upgrades keep the chat history.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Test hook: build against an arbitrary name without touching the singleton. */
        internal fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
