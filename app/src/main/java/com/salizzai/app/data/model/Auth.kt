package com.salizzai.app.data.model

/**
 * Auth-related models used by [com.salizzai.app.feature.auth.AuthViewModel].
 */

data class AuthUser(
    val id: String,
    val email: String,
    val name: String?,
    val username: String?,
    val authProvider: String,
    val avatarUrl: String?
)

sealed class AuthState {
    object Loading : AuthState()
    data class LoggedOut(val reason: LogoutReason = LogoutReason.Initial) : AuthState()
    data class LoggedIn(val user: AuthUser) : AuthState()
}

enum class LogoutReason { Initial, UserAction, AccountDeleted, ApiKeyMissing }

/** Result of an authentication attempt. */
sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class OtpRequired(val email: String, val purpose: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/** Validation results used by the auth forms. */
object AuthValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun isValidPassword(pw: String): Boolean = pw.length in 6..1_024

    fun isValidUsername(name: String): Boolean =
        name.isNotBlank() && name.length in 3..32 &&
        name.matches(Regex("^[A-Za-z0-9_\\-\\.]+$"))
}
