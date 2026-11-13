package com.pureguard.app.core

/**
 * Salted PBKDF2-HMAC-SHA256 hash of the unlock password.
 *
 * The password itself was generated randomly at build-authoring time and was
 * never printed, logged or stored anywhere. Only this one-way hash exists.
 */
internal object PasswordHash {
    const val ITERATIONS = 210000
    const val SALT_B64 = "+Ct6IGnLm9ueJn19W1lKEg=="
    const val HASH_B64 = "Wg7w9oibKVkkmJJz2evy1R/wDlt1rQWHW3REBEjW1XE="
}
