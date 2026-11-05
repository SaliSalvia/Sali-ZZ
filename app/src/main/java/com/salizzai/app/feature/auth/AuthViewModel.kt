package com.salizzai.app.feature.auth

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.salizzai.app.SaliZZaiApp
import com.salizzai.app.data.model.AuthResult
import com.salizzai.app.data.model.AuthState
import com.salizzai.app.data.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.util.Base64

/**
 * Drives the auth flow: email/password, sign-up, OTP passwordless login and Google sign-in.
 *
 * Google sign-in uses AndroidX Credential Manager (the modern, Play-Services-free path).
 * The server's WEB_CLIENT_ID is used purely to issue an ID token; the token itself is
 * never persisted — only the verified email is kept.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Email that is currently waiting for an OTP code, if any. */
    private val _pendingOtpEmail = MutableStateFlow<String?>(null)
    val pendingOtpEmail: StateFlow<String?> = _pendingOtpEmail.asStateFlow()

    /** Code issued most recently (shown in the in-app inbox). */
    val lastIssuedCode: StateFlow<Pair<String, String>?> = authRepository.lastIssuedCode

    init {
        // Watch the persisted user id and emit LoggedIn/LoggedOut accordingly.
        viewModelScope.launch {
            authRepository.loggedInUserId.collect { id ->
                if (id.isNullOrBlank()) {
                    _state.value = AuthState.LoggedOut()
                } else {
                    val user = authRepository.currentUser()
                    if (user != null) {
                        _state.value = AuthState.LoggedIn(user)
                    } else {
                        _state.value = AuthState.LoggedOut()
                    }
                }
            }
        }
    }

    // ─── Email + password ───────────────────────────────────────────────

    fun signInWithEmailPassword(email: String, password: String) {
        launchAuth {
            authRepository.signInWithEmailPassword(email, password)
        }
    }

    fun signUpWithEmail(email: String, password: String, name: String?, username: String?) {
        launchAuth {
            authRepository.signUpWithEmail(email, password, name, username)
        }
    }

    // ─── Email OTP ─────────────────────────────────────────────────────

    fun startOtpLogin(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = authRepository.startOtpLogin(email)
            _isLoading.value = false
            when (result) {
                is AuthResult.OtpRequired -> _pendingOtpEmail.value = result.email
                is AuthResult.Error -> _error.value = result.message
                is AuthResult.Success -> _state.value = AuthState.LoggedIn(result.user)
            }
        }
    }

    fun verifyOtp(code: String) {
        val email = _pendingOtpEmail.value ?: run {
            _error.value = "ابتدا درخواست کد کنید"
            return
        }
        launchAuth {
            authRepository.verifyOtp(email, code)
        }
    }

    fun cancelOtp() {
        _pendingOtpEmail.value = null
    }

    // ─── Google sign-in ─────────────────────────────────────────────────

    /**
     * Triggers the Google sign-in bottom sheet via Credential Manager.
     * `serverClientId` is the OAuth Web client ID created in Google Cloud Console.
     * Pass an empty string to fall back to a local "mock" Google sign-in
     * (useful when the user has not set up a Web client ID yet — they'll just be
     * asked for an email in a dialog).
     */
    fun signInWithGoogle(serverClientId: String, activity: android.app.Activity) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                if (serverClientId.isBlank()) {
                    // Fallback: ask user for email/password-less account creation
                    _error.value = "Google Client ID تنظیم نشده. در تنظیمات وارد کنید یا با ایمیل وارد شوید."
                    _isLoading.value = false
                    return@launch
                }
                val credential = requestGoogleCredential(serverClientId, activity)
                val (email, name) = parseGoogleCredential(credential)
                val result = authRepository.signInWithGoogle(email, name)
                handleAuthResult(result)
            } catch (e: GetCredentialException) {
                _error.value = "ورود با گوگل لغو شد یا ناموفق بود"
            } catch (e: Exception) {
                _error.value = e.message ?: "خطای ناشناخته در ورود با گوگل"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun requestGoogleCredential(
        serverClientId: String,
        activity: android.app.Activity
    ): Credential = withContext(Dispatchers.Main) {
        val credentialManager = CredentialManager.create(appContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        credentialManager.getCredential(activity, request).credential
    }

    private fun parseGoogleCredential(credential: Credential): Pair<String, String?> {
        if (credential !is GoogleIdTokenCredential) {
            // androidx.credentials.Credential (1.3.0) exposes only `type` — no `id` —
            // so there is nothing usable here for a non-Google credential.
            error("مدنوع credential پشتیبانی نمی‌شود (فقط Google ID Token)")
        }
        // Decode the ID token's payload to extract the email and name
        val token = credential.idToken
        val parts = token.split(".")
        val payload = if (parts.size >= 2) {
            runCatching {
                // JWT payload uses URL-safe base64 without padding
                val padded = parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
                val json = String(Base64.decode(padded, Base64.URL_SAFE))
                Json.parseToJsonElement(json).jsonObject
            }.getOrNull()
        } else null
        // GoogleIdTokenCredential.id holds the Google account ID; the email
        // is only present in the decoded JWT payload.
        val email = payload?.get("email")?.jsonPrimitive?.contentOrNull
            ?: credential.id
        val name = payload?.get("name")?.jsonPrimitive?.contentOrNull
        return email to name
    }

    // ─── Guest ──────────────────────────────────────────────────────────

    fun continueAsGuest() {
        launchAuth { authRepository.continueAsGuest() }
    }

    // ─── Logout / delete ────────────────────────────────────────────────

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            authRepository.deleteAccount()
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun launchAuth(action: suspend () -> AuthResult) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = action()
            _isLoading.value = false
            handleAuthResult(result)
        }
    }

    private fun handleAuthResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> _state.value = AuthState.LoggedIn(result.user)
            is AuthResult.Error -> _error.value = result.message
            is AuthResult.OtpRequired -> _pendingOtpEmail.value = result.email
        }
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = SaliZZaiApp.get()
                return AuthViewModel(app.authRepository, app) as T
            }
        }
    }
}
