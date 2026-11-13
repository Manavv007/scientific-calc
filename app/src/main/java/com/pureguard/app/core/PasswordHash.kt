package com.pureguard.app.core

/**
 * Salted PBKDF2-HMAC-SHA256 hash of the unlock password.
 * The plain password is kept only in PASSWORD.txt at the repository root.
 */
internal object PasswordHash {
    const val ITERATIONS = 210000
    const val SALT_B64 = "8ccAwjN5IRyzTmmCpLDyAQ=="
    const val HASH_B64 = "+rbn1gGkzjNT7Ldwwn7LQPtC0mKb977dp/nAMPNxbfI="
}
