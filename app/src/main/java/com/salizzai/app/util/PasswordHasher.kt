package com.salizzai.app.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing with PBKDF2-HMAC-SHA256. The stored format is
 * `iterations$saltHex$hashHex`, allowing bounded verification of older hashes.
 */
object PasswordHasher {

    private const val ITERATIONS = 600_000
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 1_000_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 32
    private const val HASH_LENGTH_BYTES = KEY_LENGTH_BITS / 8
    private const val MAX_PASSWORD_CHARS = 1_024
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hash(password: String): String {
        require(password.isNotEmpty() && password.length <= MAX_PASSWORD_CHARS) {
            "Password length is outside the supported range."
        }
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(password, salt, ITERATIONS)
        return "$ITERATIONS$${salt.toHex()}$${derived.toHex()}"
    }

    fun verify(password: String, storedHash: String): Boolean {
        if (password.isEmpty() || password.length > MAX_PASSWORD_CHARS || storedHash.length > 256) return false
        return try {
            val parts = storedHash.split('$')
            if (parts.size != 3) return false

            val iterations = parts[0].toIntOrNull() ?: return false
            if (iterations !in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) return false

            val salt = parts[1].fromHex() ?: return false
            val expectedHash = parts[2].fromHex() ?: return false
            if (salt.size != SALT_LENGTH_BYTES || expectedHash.size != HASH_LENGTH_BYTES) return false

            val actualHash = deriveKey(password, salt, iterations)
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (isEmpty() || length % 2 != 0) return null
        val result = ByteArray(length / 2)
        for (index in result.indices) {
            val high = Character.digit(this[index * 2], 16)
            val low = Character.digit(this[index * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            result[index] = ((high shl 4) or low).toByte()
        }
        return result
    }
}
