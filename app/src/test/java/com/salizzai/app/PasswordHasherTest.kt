package com.salizzai.app

import com.salizzai.app.util.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun hashVerifiesOnlyTheOriginalPassword() {
        val encoded = PasswordHasher.hash("correct-horse-battery")
        assertTrue(PasswordHasher.verify("correct-horse-battery", encoded))
        assertFalse(PasswordHasher.verify("incorrect-password", encoded))
    }

    @Test
    fun malformedOrUnboundedHashesAreRejected() {
        assertFalse(PasswordHasher.verify("password", "not-a-hash"))
        assertFalse(PasswordHasher.verify("", "600000$00$00"))
        assertFalse(PasswordHasher.verify("password", "999999999\$salt\$hash"))
        assertFalse(PasswordHasher.verify("password", "600000\$00\$00"))
        assertFalse(PasswordHasher.verify("x".repeat(1_025), "600000\$00\$00"))
    }
}
