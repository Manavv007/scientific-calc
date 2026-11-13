package com.pureguard.app.core

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.min

/**
 * Verifies the unlock password against the embedded one-way hash and throttles
 * guesses with an exponential lockout, so brute forcing through the UI is useless.
 */
object PasswordVault {
    private const val FREE_ATTEMPTS = 3
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 24 * 60 * 60 * 1000L

    /** How long a correct password keeps settings unlocked. */
    const val UNLOCK_WINDOW_MS = 10 * 60 * 1000L

    sealed interface Result {
        data object Success : Result
        data class Wrong(val lockedForMs: Long) : Result
        data class LockedOut(val remainingMs: Long) : Result
    }

    fun remainingLockout(): Long =
        (Prefs.lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Slow (PBKDF2), call from a background thread. */
    fun tryUnlock(candidate: String): Result {
        val remaining = remainingLockout()
        if (remaining > 0) return Result.LockedOut(remaining)

        return if (matches(candidate.trim())) {
            Prefs.failedAttempts = 0
            Prefs.lockoutUntil = 0
            Prefs.unlockedUntil = System.currentTimeMillis() + UNLOCK_WINDOW_MS
            Result.Success
        } else {
            val fails = Prefs.failedAttempts + 1
            Prefs.failedAttempts = fails
            val lock = if (fails >= FREE_ATTEMPTS) {
                val exp = min(fails - FREE_ATTEMPTS, 20)
                min(BASE_LOCKOUT_MS shl exp, MAX_LOCKOUT_MS)
            } else 0L
            Prefs.lockoutUntil = System.currentTimeMillis() + lock
            Result.Wrong(lock)
        }
    }

    fun relock() {
        Prefs.unlockedUntil = 0
    }

    private fun matches(candidate: String): Boolean {
        if (candidate.isEmpty()) return false
        val salt = Base64.decode(PasswordHash.SALT_B64, Base64.NO_WRAP)
        val expected = Base64.decode(PasswordHash.HASH_B64, Base64.NO_WRAP)
        val spec = PBEKeySpec(candidate.toCharArray(), salt, PasswordHash.ITERATIONS, expected.size * 8)
        return try {
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            MessageDigest.isEqual(actual, expected)
        } finally {
            spec.clearPassword()
        }
    }
}
