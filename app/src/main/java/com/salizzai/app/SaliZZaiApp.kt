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

        // Initialize encrypted preferences (for API key + sensitive data)
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        encryptedPrefs = EncryptedSharedPreferences.create(
            this,
            "sali_zzai_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

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

    companion object {
        private lateinit var instance: SaliZZaiApp

        fun get(): SaliZZaiApp = instance

        fun get(context: Context): SaliZZaiApp =
            context.applicationContext as SaliZZaiApp
    }
}
