package com.pureguard.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings
import android.util.Log

/**
 * The main filter. Android's own "Private DNS" setting is pointed at a
 * family-safe resolver, so every app's lookups are filtered by the system
 * itself. No VPN and no accessibility service are involved, which is why
 * this mode does not interfere with banking or payment apps.
 */
object PrivateDns {
    private const val TAG = "PrivateDns"
    private const val MODE = "private_dns_mode"
    private const val SPECIFIER = "private_dns_specifier"

    /** Blocks adult sites, proxies/VPN sites, and forces SafeSearch + YouTube restricted mode. */
    const val HOST_STRICT = "family-filter-dns.cleanbrowsing.org"

    /** Blocks adult sites and forces SafeSearch, nothing else. */
    const val HOST_STANDARD = "adult-filter-dns.cleanbrowsing.org"

    /** Any of these counts as "filtered". */
    val SAFE_HOSTS = setOf(
        HOST_STRICT, HOST_STANDARD,
        "family.cloudflare-dns.com", "family.adguard-dns.com",
    )

    fun wantedHost(): String = if (Prefs.dnsStrict) HOST_STRICT else HOST_STANDARD

    /** The Private DNS hostname currently configured on the phone, or null. */
    fun currentHost(context: Context): String? {
        try {
            val cr = context.contentResolver
            val mode = Settings.Global.getString(cr, MODE)
            if (mode != null) {
                return if (mode == "hostname") Settings.Global.getString(cr, SPECIFIER)?.lowercase() else null
            }
        } catch (_: Exception) {
            // Not readable on this Android version; fall through to the public API.
        }
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    fun isActive(context: Context): Boolean = currentHost(context) in SAFE_HOSTS

    /** Sets the filter. Needs WRITE_SECURE_SETTINGS (granted once over USB). */
    fun apply(context: Context): Boolean = write(context, "hostname", wantedHost())

    /** Returns Private DNS to Android's default. */
    fun clear(context: Context): Boolean = write(context, "opportunistic", null)

    private fun write(context: Context, mode: String, host: String?): Boolean {
        if (!Protection.hasSecureSettings(context)) return false
        return try {
            val cr = context.contentResolver
            if (host != null) Settings.Global.putString(cr, SPECIFIER, host)
            Settings.Global.putString(cr, MODE, mode)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not change Private DNS", e)
            false
        }
    }
}
