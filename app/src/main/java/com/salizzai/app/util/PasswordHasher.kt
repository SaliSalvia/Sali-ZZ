package com.salizzai.app.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing using PBKDF2WithHmacSHA256 (Android built-in).
 * No external bcrypt dependency needed.
 * 600,000 iterations (OWASP 2023 guidance), 256-bit output, 32-byte salt.
 * Stored format embeds the iteration count so older hashes keep verifying.
 */
object PasswordHasher {

    private const val ITERATIONS = 600_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 32
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = deriveKey(password, salt)
        // Format: iterations$saltHex$hashHex
        return "$ITERATIONS$${salt.toHex()}$${hash.toHex()}"
    }

    fun verify(password: String, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split("$")
            if (parts.size != 3) return false
            val iterations = parts[0].toIntOrNull() ?: return false
            val salt = parts[1].fromHex()
            val expectedHash = parts[2].fromHex()
            val actualHash = deriveKey(password, salt, iterations)
            // Constant-time comparison
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (e: Exception) {
            false
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) {
            ((Character.digit(this[it * 2], 16) shl 4) +
             Character.digit(this[it * 2 + 1], 16)).toByte()
        }
    }
}
