package com.pureguard.app.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.pureguard.app.admin.DeviceOwner
import com.pureguard.app.guard.GuardAccessibilityService
import com.pureguard.app.vpn.ShieldVpnService
import com.pureguard.app.work.Scheduler

/** Snapshot of every protection layer. */
data class ProtectionStatus(
    val privateDns: Boolean,
    val dnsHost: String?,
    val vpnRunning: Boolean,
    val accessibility: Boolean,
    val admin: Boolean,
    val secureSettings: Boolean,
    val batteryUnrestricted: Boolean,
    val notifications: Boolean,
    val locked: Boolean,
    val unlockedForMs: Long,
    val pausedForMs: Long,
    val pausesLeft: Int,
    val blockedToday: Long,
    val blockedTotal: Long,
) {
    /** Something is filtering the network right now. */
    val filterActive: Boolean get() = privateDns || vpnRunning

    /** The optional layers that some payment apps object to. */
    val hasPausableLayers: Boolean get() = accessibility || vpnRunning
}

object Protection {
    private const val TAG = "Protection"
    const val PAUSE_MS = 5 * 60 * 1000L
    const val MAX_PAUSES_PER_DAY = 8

    fun status(context: Context): ProtectionStatus {
        val pm = context.getSystemService(PowerManager::class.java)
        val now = System.currentTimeMillis()
        val host = PrivateDns.currentHost(context)
        return ProtectionStatus(
            privateDns = host in PrivateDns.SAFE_HOSTS,
            dnsHost = host,
            vpnRunning = ShieldVpnService.isRunning,
            accessibility = isAccessibilityEnabled(context),
            admin = DeviceOwner.isAdmin(context),
            secureSettings = hasSecureSettings(context),
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName),
            notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
            locked = Prefs.locked,
            unlockedForMs = if (Prefs.locked) (Prefs.unlockedUntil - now).coerceAtLeast(0) else 0,
            pausedForMs = (Prefs.pauseUntil - now).coerceAtLeast(0),
            pausesLeft = (MAX_PAUSES_PER_DAY - Prefs.pausesToday).coerceAtLeast(0),
            blockedToday = Prefs.blockedToday,
            blockedTotal = Prefs.blockedTotal,
        )
    }

    fun accessibilityComponent(context: Context) =
        ComponentName(context, GuardAccessibilityService::class.java)

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (GuardAccessibilityService.connected) return true
        return try {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = accessibilityComponent(context)
            enabled.split(':').any {
                ComponentName.unflattenFromString(it)?.let { c ->
                    c.packageName == me.packageName && c.className == me.className
                } ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun hasSecureSettings(context: Context) = ContextCompat.checkSelfPermission(
        context, Manifest.permission.WRITE_SECURE_SETTINGS,
    ) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------ payment pause

    /**
     * Switches off the layers that payment apps can object to (Screen Guard and
     * the optional VPN) for a few minutes. The Private DNS filter stays on:
     * payment apps cannot see it. No password needed, by design.
     */
    fun startPause(context: Context): Boolean {
        if (Prefs.pausesToday >= MAX_PAUSES_PER_DAY) return false
        Prefs.recordPause()
        Prefs.pauseUntil = System.currentTimeMillis() + PAUSE_MS
        if (isAccessibilityEnabled(context)) {
            Prefs.resumeGuardPending = true
            GuardAccessibilityService.pauseSelf()
        }
        ShieldVpnService.pause(context)
        Scheduler.resumeAfterPause(context, PAUSE_MS)
        return true
    }

    fun endPause(context: Context) {
        Prefs.pauseUntil = 0
        heal(context)
    }

    // ------------------------------------------------------------ self-healing

    /**
     * Called by the watchdog, on boot, when the app opens and after a pause.
     * Restores layers that should be on. Never throws.
     */
    fun heal(context: Context) {
        try {
            Prefs.init(context)
            if (Prefs.paused) return
            val secure = hasSecureSettings(context)

            // Optional VPN filter.
            if (Prefs.vpnWanted && !ShieldVpnService.isRunning && VpnService.prepare(context) == null) {
                ShieldVpnService.start(context)
            }

            // Screen Guard: bring it back after a pause, or if removed while locked.
            val guardShouldBeOn = Prefs.screenGuardWanted && (Prefs.resumeGuardPending || Prefs.enforcing)
            if (guardShouldBeOn && !isAccessibilityEnabled(context) && secure) {
                enableAccessibility(context)
            }
            if (isAccessibilityEnabled(context)) Prefs.resumeGuardPending = false

            // Private DNS: keep it pointed at the filter while locked.
            if (Prefs.enforcing && secure && !PrivateDns.isActive(context)) {
                PrivateDns.apply(context)
            }

            DeviceOwner.applyPolicies(context)

            val problems = buildList {
                if (Prefs.enforcing && !PrivateDns.isActive(context) && !ShieldVpnService.isRunning) {
                    add("the content filter is off")
                }
                if (Prefs.vpnWanted && !ShieldVpnService.isRunning) add("the VPN filter is off")
                if (Prefs.resumeGuardPending) add("Screen Guard is still paused")
                else if (Prefs.enforcing && Prefs.screenGuardWanted && !isAccessibilityEnabled(context)) {
                    add("Screen Guard is off")
                }
            }
            if (problems.isEmpty()) {
                Notifier.clearTamper(context)
            } else {
                Notifier.tamper(
                    context,
                    problems.joinToString(", ").replaceFirstChar { it.uppercase() } + ". Tap to fix.",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "heal failed", e)
        }
    }

    private fun enableAccessibility(context: Context) {
        try {
            val cr = context.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val mine = accessibilityComponent(context).flattenToString()
            if (current != null && current.contains(mine)) return
            val updated = if (current.isNullOrEmpty()) mine else "$current:$mine"
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (e: Exception) {
            Log.w(TAG, "Could not re-enable Screen Guard", e)
        }
    }
}
