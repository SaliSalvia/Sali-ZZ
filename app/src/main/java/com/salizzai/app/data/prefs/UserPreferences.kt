package com.salizzai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.salizzai.app.data.model.AvailableModels

private val Context.dataStore by preferencesDataStore(name = "sali_zzai_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val MODEL = stringPreferencesKey("model")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val THINKING_ENABLED = booleanPreferencesKey("thinking_enabled")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val LOGGED_IN_USER_ID = stringPreferencesKey("logged_in_user_id")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val VOICE_INPUT_ENABLED = booleanPreferencesKey("voice_input_enabled")
        val AUTO_SEND_VOICE = booleanPreferencesKey("auto_send_voice")
        val AVAILABLE_MODEL_IDS = stringSetPreferencesKey("available_model_ids")
        val API_VALIDATED_AT = longPreferencesKey("api_validated_at")
    }

    val theme: Flow<String> = context.dataStore.data.map { it[THEME] ?: "dark" }
    val language: Flow<String> = context.dataStore.data.map { it[LANGUAGE] ?: "fa" }
    val model: Flow<String> = context.dataStore.data.map { it[MODEL] ?: AvailableModels.DEFAULT_MODEL }
    val temperature: Flow<Float> = context.dataStore.data.map { it[TEMPERATURE] ?: 0.7f }
    val thinkingEnabled: Flow<Boolean> = context.dataStore.data.map { it[THINKING_ENABLED] ?: false }
    val systemPrompt: Flow<String> = context.dataStore.data.map { it[SYSTEM_PROMPT] ?: "" }
    val loggedInUserId: Flow<String?> = context.dataStore.data.map { it[LOGGED_IN_USER_ID] }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }
    val dynamicColors: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLORS] ?: false }
    val fontScale: Flow<Float> = context.dataStore.data.map { it[FONT_SCALE] ?: 1.0f }
    val ttsEnabled: Flow<Boolean> = context.dataStore.data.map { it[TTS_ENABLED] ?: false }
    val voiceInputEnabled: Flow<Boolean> = context.dataStore.data.map { it[VOICE_INPUT_ENABLED] ?: true }
    val autoSendVoice: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SEND_VOICE] ?: false }

    /** Model ids last reported by the Z.ai API (empty until a key is verified). */
    val availableModelIds: Flow<Set<String>> =
        context.dataStore.data.map { it[AVAILABLE_MODEL_IDS] ?: emptySet() }

    /** Epoch millis of the last successful API-key validation (0 = never). */
    val apiValidatedAt: Flow<Long> = context.dataStore.data.map { it[API_VALIDATED_AT] ?: 0L }

    /** Blocking snapshot used by non-async code paths (e.g. [AuthRepository.currentUser]). */
    suspend fun loggedInUserIdSnapshot(): String? = loggedInUserId.first()

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[THEME] = value }
    }
    suspend fun setLanguage(value: String) {
        context.dataStore.edit { it[LANGUAGE] = value }
    }
    suspend fun setModel(value: String) {
        context.dataStore.edit { it[MODEL] = value }
    }
    suspend fun setTemperature(value: Float) {
        context.dataStore.edit { it[TEMPERATURE] = value }
    }
    suspend fun setThinkingEnabled(value: Boolean) {
        context.dataStore.edit { it[THINKING_ENABLED] = value }
    }
    suspend fun setSystemPrompt(value: String) {
        context.dataStore.edit { it[SYSTEM_PROMPT] = value }
    }
    suspend fun setLoggedInUserId(value: String?) {
        context.dataStore.edit {
            if (value == null) it.remove(LOGGED_IN_USER_ID) else it[LOGGED_IN_USER_ID] = value
        }
    }
    suspend fun setOnboardingDone(value: Boolean) {
        context.dataStore.edit { it[ONBOARDING_DONE] = value }
    }
    suspend fun setDynamicColors(value: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLORS] = value }
    }
    suspend fun setFontScale(value: Float) {
        context.dataStore.edit { it[FONT_SCALE] = value }
    }
    suspend fun setTtsEnabled(value: Boolean) {
        context.dataStore.edit { it[TTS_ENABLED] = value }
    }
    suspend fun setVoiceInputEnabled(value: Boolean) {
        context.dataStore.edit { it[VOICE_INPUT_ENABLED] = value }
    }
    suspend fun setAutoSendVoice(value: Boolean) {
        context.dataStore.edit { it[AUTO_SEND_VOICE] = value }
    }
    suspend fun setAvailableModelIds(ids: Set<String>) {
        context.dataStore.edit {
            if (ids.isEmpty()) it.remove(AVAILABLE_MODEL_IDS) else it[AVAILABLE_MODEL_IDS] = ids
        }
    }
    suspend fun setApiValidatedAt(value: Long) {
        context.dataStore.edit { it[API_VALIDATED_AT] = value }
    }
}
