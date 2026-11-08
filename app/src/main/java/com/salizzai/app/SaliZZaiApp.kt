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

    lateinit var authRepository: AuthRepository
        private set

    // Lazy: building EncryptedSharedPreferences does AndroidKeyStore IPC and
    // file decryption on the calling thread, and creating the API client touches
    // it. Both are therefore created on first real use (after the brand intro is
    // already on screen) instead of blocking the first frame of a cold start.
    val encryptedPrefs: SharedPreferences by lazy { createSecurePrefs() }

    val zaiClient: ZaiApiClient by lazy { ZaiApiClient(encryptedPrefs, cacheDir) }

    override fun onCreate() {
        super.onCreate()

        instance = this

        // NOTE: encryptedPrefs / zaiClient are intentionally NOT touched here.
        // They are lazy (see above) and must go through createSecurePrefs():
        // creating EncryptedSharedPreferences directly used to crash
        // Application.onCreate on HyperOS devices with a corrupted Keystore
        // or prefs file restored from another phone, before any UI appeared.

        // Initialize Room database (local, no server)
        database = AppDatabase.getInstance(this)

        // Initialize user preferences
        userPrefs = UserPreferences(this)

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
                // Never downgrade to plaintext storage: API keys and account
                // identifiers must remain protected even when the Keystore is
                // unavailable or the encrypted file cannot be recovered.
                throw IllegalStateException(
                    "Secure credential storage is unavailable. Credentials were not loaded or saved.",
                    e2
                )
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
