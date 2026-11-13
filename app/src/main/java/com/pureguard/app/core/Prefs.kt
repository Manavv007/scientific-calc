package com.pureguard.app.core

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Small, synchronous settings store shared by every component of the app. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences("pureguard", Context.MODE_PRIVATE)
        }
    }

    /** Once locked, turning anything off requires the password. */
    var locked: Boolean
        get() = sp.getBoolean("locked", false)
        set(v) = sp.edit().putBoolean("locked", v).apply()

    /** Epoch millis until which a correct password temporarily unlocks settings. */
    var unlockedUntil: Long
        get() = sp.getLong("unlocked_until", 0L)
        set(v) = sp.edit().putLong("unlocked_until", v).apply()

    /** True when protection is locked and no password session is active. */
    val enforcing: Boolean
        get() = locked && System.currentTimeMillis() > unlockedUntil

    /** True when the user is allowed to change settings right now. */
    val canEdit: Boolean
        get() = !enforcing

    // ---- Brute-force throttling of the password prompt ----
    var failedAttempts: Int
        get() = sp.getInt("failed_attempts", 0)
        set(v) = sp.edit().putInt("failed_attempts", v).apply()

    var lockoutUntil: Long
        get() = sp.getLong("lockout_until", 0L)
        set(v) = sp.edit().putLong("lockout_until", v).apply()

    // ---- Feature switches (all on by default; only changeable when unlocked) ----
    var screenScan: Boolean
        get() = sp.getBoolean("screen_scan", true)
        set(v) = sp.edit().putBoolean("screen_scan", v).apply()

    var typingGuard: Boolean
        get() = sp.getBoolean("typing_guard", true)
        set(v) = sp.edit().putBoolean("typing_guard", v).apply()

    var safeSearch: Boolean
        get() = sp.getBoolean("safe_search", true)
        set(v) = sp.edit().putBoolean("safe_search", v).apply()

    var blockBypassTools: Boolean
        get() = sp.getBoolean("block_bypass", true)
        set(v) = sp.edit().putBoolean("block_bypass", v).apply()

    var blockOtherVpnApps: Boolean
        get() = sp.getBoolean("block_vpn_apps", true)
        set(v) = sp.edit().putBoolean("block_vpn_apps", v).apply()

    var vpnWanted: Boolean
        get() = sp.getBoolean("vpn_wanted", false)
        set(v) = sp.edit().putBoolean("vpn_wanted", v).apply()

    // ---- Custom lists ----
    var customBlocked: Set<String>
        get() = sp.getStringSet("custom_blocked", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("custom_blocked", v).apply()

    var customAllowed: Set<String>
        get() = sp.getStringSet("custom_allowed", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("custom_allowed", v).apply()

    var customApps: Set<String>
        get() = sp.getStringSet("custom_apps", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("custom_apps", v).apply()

    // ---- Blocklist metadata ----
    var listUpdatedAt: Long
        get() = sp.getLong("list_updated_at", 0L)
        set(v) = sp.edit().putLong("list_updated_at", v).apply()

    // ---- Statistics ----
    val blockedTotal: Long get() = sp.getLong("blocked_total", 0L)

    val blockedToday: Long
        get() = if (sp.getString("blocked_day", "") == LocalDate.now().toString())
            sp.getLong("blocked_today", 0L) else 0L

    @Synchronized
    fun recordBlock() {
        val today = LocalDate.now().toString()
        val sameDay = sp.getString("blocked_day", "") == today
        sp.edit()
            .putLong("blocked_total", blockedTotal + 1)
            .putString("blocked_day", today)
            .putLong("blocked_today", if (sameDay) sp.getLong("blocked_today", 0L) + 1 else 1L)
            .apply()
    }
}
