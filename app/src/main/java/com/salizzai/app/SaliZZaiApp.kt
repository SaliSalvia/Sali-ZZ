package com.salizzai.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.salizzai.app.data.api.ZaiApiClient
import com.salizzai.app.data.db.AppDatabase
import com.salizzai.app.data.prefs.UserPreferences
import com.salizzai.app.data.repository.AuthRepository

/**
 * Sali-ZZai Application — Native Android app for Z.ai.
 * No server needed, everything happens on-device.
 */
class SaliZZaiApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var userPrefs: UserPreferences
        private set

    lateinit var zaiClient: ZaiApiClient
        private set

    lateinit var encryptedPrefs: SharedPreferences
        private set

    lateinit var authRepository: AuthRepository
        private set

    override fun onCreate() {
        super.onCreate()

        instance = this

        // Initialize encrypted preferences (for API key + sensitive data).
        // Uses the self-healing builder below: a corrupt keystore entry used to
        // crash the app on launch with no way to recover from the UI.
        encryptedPrefs = createSecurePrefs()

        // Initialize Room database (local, no server)
        database = AppDatabase.getInstance(this)

        // Initialize user preferences
        userPrefs = UserPreferences(this)

        // Initialize Z.ai API client (calls Z.ai directly, no proxy)
        // Pass the device cache directory so OkHttp can cache HTTP responses.
        zaiClient = ZaiApiClient(encryptedPrefs, cacheDir)

        // Initialize auth repository (handles email/password/OTP/Google sign-in)
        authRepository = AuthRepository(this, database, userPrefs)
    }

    private fun createSecurePrefs(): SharedPreferences {
        fun build(): SharedPreferences {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                this,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            build()
        } catch (e: Exception) {
            // Corrupted prefs file / stale key: delete and retry once.
            try {
                getSharedPreferences(SECURE_PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
                java.io.File(filesDir.parent, "shared_prefs/$SECURE_PREFS_NAME.xml").delete()
                build()
            } catch (e2: Exception) {
                getSharedPreferences(SECURE_PREFS_NAME + "_fallback", MODE_PRIVATE)
            }
        }
    }

    companion object {
        private const val SECURE_PREFS_NAME = "sali_zzai_secure"
        private lateinit var instance: SaliZZaiApp

        fun get(): SaliZZaiApp = instance

        fun get(context: Context): SaliZZaiApp =
            context.applicationContext as SaliZZaiApp
    }
}
