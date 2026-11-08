package com.salizzai.app.data.repository

import android.content.Context
import com.salizzai.app.data.db.AppDatabase
import com.salizzai.app.data.db.entity.User
import com.salizzai.app.data.model.AuthResult
import com.salizzai.app.data.model.AuthUser
import com.salizzai.app.data.model.AuthValidator
import com.salizzai.app.data.prefs.UserPreferences
import com.salizzai.app.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID

/**
 * Repository that owns all authentication logic.
 *
 * The app is server-less: every check happens locally in Room.
 * - Email + password → PBKDF2 hash (see [PasswordHasher]).
 * - Email OTP → a local demo code is generated and displayed in the UI; no email is sent.
 * - Google sign-in → Credential Manager obtains a Google ID token, but this server-less
 *   app only extracts claims client-side and creates a local profile. There is no backend
 *   token verification or remote account authority; do not treat this as production auth.
 */
class AuthRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val userPrefs: UserPreferences
) {
    private val userDao get() = database.userDao()
    private val codeDao get() = database.emailCodeDao()

    /** OTP code issued most recently; the auth UI reads this to display the inbox. */
    private val _lastIssuedCode = MutableStateFlow<Pair<String, String>?>(null) // (email, code)
    val lastIssuedCode: StateFlow<Pair<String, String>?> = _lastIssuedCode.asStateFlow()

    // ─── Email + password ───────────────────────────────────────────────

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String?,
        username: String?
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (!AuthValidator.isValidEmail(cleanEmail)) return@withContext AuthResult.Error("ایمیل معتبر نیست")
        if (!AuthValidator.isValidPassword(password)) return@withContext AuthResult.Error("گذرواژه باید حداقل ۶ کاراکتر باشد")
        if (userDao.getByEmail(cleanEmail) != null) return@withContext AuthResult.Error("این ایمیل قبلاً ثبت شده است")
        if (username != null && username.isNotBlank() && userDao.getByUsername(username) != null) {
            return@withContext AuthResult.Error("این نام کاربری قبلاً گرفته شده است")
        }
        val user = User(
            id = UUID.randomUUID().toString(),
            email = cleanEmail,
            username = username?.trim()?.takeIf { it.isNotBlank() },
            name = name?.trim()?.takeIf { it.isNotBlank() },
            passwordHash = PasswordHasher.hash(password),
            authProvider = "local"
        )
        userDao.insert(user)
        loginInternal(user, persist = true)
    }

    suspend fun signInWithEmailPassword(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val cleanEmail = email.trim().lowercase()
            val user = userDao.getByEmail(cleanEmail)
                ?: return@withContext AuthResult.Error("حسابی با این ایمیل پیدا نشد")
            if (user.authProvider == "google" && user.passwordHash.isBlank()) {
                return@withContext AuthResult.Error("این حساب با گوگل ساخته شده؛ از ورود با گوگل استفاده کنید")
            }
            if (!PasswordHasher.verify(password, user.passwordHash)) {
                return@withContext AuthResult.Error("گذرواژه اشتباه است")
            }
            loginInternal(user, persist = true)
        }

    // ─── Email OTP (passwordless) ────────────────────────────────────────

    suspend fun startOtpLogin(email: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (!AuthValidator.isValidEmail(cleanEmail)) return@withContext AuthResult.Error("ایمیل معتبر نیست")
        // Purge stale codes first
        codeDao.purgeExpired()
        // Issue a fresh 6-digit code
        val code = generateSixDigitCode()
        val codeHash = PasswordHasher.hash(code)
        codeDao.upsert(
            com.salizzai.app.data.db.entity.EmailCode(
                email = cleanEmail,
                codeHash = codeHash,
                purpose = "login",
                expiresAt = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
            )
        )
        // Expose it via the in-app "inbox" (lastIssuedCode).
        _lastIssuedCode.value = cleanEmail to code
        AuthResult.OtpRequired(cleanEmail, "login")
    }

    suspend fun verifyOtp(email: String, code: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val record = codeDao.getByEmail(cleanEmail)
            ?: return@withContext AuthResult.Error("کدی صادر نشده است؛ دوباره درخواست کنید")
        if (record.attempts >= 5) {
            codeDao.deleteByEmail(cleanEmail)
            return@withContext AuthResult.Error("تعداد تلاش‌ها بیش از حد مجاز بود؛ کد باطل شد")
        }
        if (System.currentTimeMillis() > record.expiresAt) {
            codeDao.deleteByEmail(cleanEmail)
            return@withContext AuthResult.Error("کد منقضی شده است")
        }
        if (!PasswordHasher.verify(code, record.codeHash)) {
            codeDao.incrementAttempt(cleanEmail)
            return@withContext AuthResult.Error("کد اشتباه است")
        }
        codeDao.deleteByEmail(cleanEmail)
        // Find or create the user
        val user = userDao.getByEmail(cleanEmail) ?: run {
            val newUser = User(
                id = UUID.randomUUID().toString(),
                email = cleanEmail,
                username = null,
                name = null,
                passwordHash = "",
                authProvider = "otp"
            )
            userDao.insert(newUser)
            newUser
        }
        loginInternal(user, persist = true)
    }

    // ─── Google sign-in (Credential Manager) ─────────────────────────────

    /**
     * Called by [AuthViewModel] after Credential Manager returns a Google credential.
     * This is local profile convenience, not server-verified identity authentication.
     */
    suspend fun signInWithGoogle(email: String, name: String?): AuthResult =
        withContext(Dispatchers.IO) {
            val cleanEmail = email.trim().lowercase()
            if (!AuthValidator.isValidEmail(cleanEmail)) {
                return@withContext AuthResult.Error("ایمیل دریافت‌شده از گوگل معتبر نیست")
            }
            val user = userDao.getByEmail(cleanEmail) ?: run {
                val newUser = User(
                    id = UUID.randomUUID().toString(),
                    email = cleanEmail,
                    username = null,
                    name = name,
                    passwordHash = "",
                    authProvider = "google"
                )
                userDao.insert(newUser)
                newUser
            }
            loginInternal(user, persist = true)
        }

    // ─── Guest mode ──────────────────────────────────────────────────────

    suspend fun continueAsGuest(): AuthResult = withContext(Dispatchers.IO) {
        val existing = userDao.getGuestUser()
        val user = existing ?: run {
            val newGuest = User(
                id = UUID.randomUUID().toString(),
                email = "guest@sali-zzai.local",
                username = "guest_${UUID.randomUUID().toString().take(6)}",
                name = "کاربر مهمان",
                passwordHash = "",
                authProvider = "local",
                isGuest = true
            )
            userDao.insert(newGuest)
            newGuest
        }
        loginInternal(user, persist = true)
    }

    // ─── Session ─────────────────────────────────────────────────────────

    val loggedInUserId: Flow<String?> = userPrefs.loggedInUserId

    suspend fun currentUser(): AuthUser? = withContext(Dispatchers.IO) {
        val id = userPrefs.loggedInUserIdSnapshot() ?: return@withContext null
        userDao.getById(id)?.toAuthUser()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        userPrefs.setLoggedInUserId(null)
    }

    suspend fun deleteAccount(): AuthResult = withContext(Dispatchers.IO) {
        val id = userPrefs.loggedInUserIdSnapshot() ?: return@withContext AuthResult.Error("کاربری وارد نشده")
        database.conversationDao().deleteAllForUser(id)
        userDao.deleteById(id)
        userPrefs.setLoggedInUserId(null)
        AuthResult.Success(AuthUser(id, "", null, null, "local", null))
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private suspend fun loginInternal(user: User, persist: Boolean): AuthResult {
        if (persist) userPrefs.setLoggedInUserId(user.id)
        return AuthResult.Success(user.toAuthUser())
    }

    private fun User.toAuthUser() = AuthUser(
        id = id,
        email = email,
        name = name,
        username = username,
        authProvider = authProvider,
        avatarUrl = avatarUrl
    )

    private fun generateSixDigitCode(): String {
        val random = SecureRandom()
        val n = random.nextInt(1_000_000)
        return "%06d".format(n)
    }
}
