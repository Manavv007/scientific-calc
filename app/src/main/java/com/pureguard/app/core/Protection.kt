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
import com.pureguard.app.filter.DomainFilter
import com.pureguard.app.guard.GuardAccessibilityService
import com.pureguard.app.vpn.ShieldVpnService

/** Snapshot of every protection layer, plus helpers that heal broken layers. */
data class ProtectionStatus(
    val vpnRunning: Boolean,
    val vpnPermission: Boolean,
    val alwaysOn: Boolean,
    val accessibility: Boolean,
    val admin: Boolean,
    val deviceOwner: Boolean,
    val secureSettings: Boolean,
    val batteryUnrestricted: Boolean,
    val notifications: Boolean,
    val locked: Boolean,
    val unlockedForMs: Long,
    val domains: Int,
    val blockedToday: Long,
    val blockedTotal: Long,
    val listUpdatedAt: Long,
) {
    val coreReady: Boolean get() = vpnRunning && accessibility && admin
    val layersActive: Int
        get() = listOf(vpnRunning, accessibility, admin, alwaysOn || deviceOwner, batteryUnrestricted).count { it }
    val layersTotal: Int get() = 5
}

object Protection {
    private const val TAG = "Protection"

    fun status(context: Context): ProtectionStatus {
        val pm = context.getSystemService(PowerManager::class.java)
        val now = System.currentTimeMillis()
        return ProtectionStatus(
            vpnRunning = ShieldVpnService.isRunning,
            vpnPermission = VpnService.prepare(context) == null,
            alwaysOn = ShieldVpnService.alwaysOnActive,
            accessibility = isAccessibilityEnabled(context),
            admin = DeviceOwner.isAdmin(context),
            deviceOwner = DeviceOwner.isOwner(context),
            secureSettings = hasSecureSettings(context),
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName),
            notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
            locked = Prefs.locked,
            unlockedForMs = if (Prefs.locked) (Prefs.unlockedUntil - now).coerceAtLeast(0) else 0,
            domains = DomainFilter.size,
            blockedToday = Prefs.blockedToday,
            blockedTotal = Prefs.blockedTotal,
            listUpdatedAt = Prefs.listUpdatedAt,
        )
    }

    fun accessibilityComponent(context: Context) =
        ComponentName(context, GuardAccessibilityService::class.java)

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (GuardAccessibilityService.connected) return true
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = accessibilityComponent(context)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.let { c ->
                c.packageName == me.packageName && c.className == me.className
            } ?: false
        }
    }

    fun hasSecureSettings(context: Context) = ContextCompat.checkSelfPermission(
        context, Manifest.permission.WRITE_SECURE_SETTINGS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Self-healing. Called by the watchdog, on boot and by the accessibility
     * service. Brings back any layer that was switched off while locked.
     */
    fun heal(context: Context) {
        Prefs.init(context)
        if (Prefs.vpnWanted && !ShieldVpnService.isRunning && VpnService.prepare(context) == null) {
            ShieldVpnService.start(context)
        }
        if (!Prefs.locked) return

        if (hasSecureSettings(context)) {
            try {
                if (!isAccessibilityEnabled(context)) {
                    val cr = context.contentResolver
                    val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    val mine = accessibilityComponent(context).flattenToString()
                    val updated = if (current.isNullOrEmpty()) mine else "$current:$mine"
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                }
                // A custom Private DNS hostname would bypass the filter.
                val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
                if (mode == "hostname") {
                    Settings.Global.putString(context.contentResolver, "private_dns_mode", "opportunistic")
                }
            } catch (e: Exception) {
                Log.w(TAG, "secure-settings heal failed", e)
            }
        }

        DeviceOwner.applyPolicies(context)

        val problems = buildList {
            if (!isAccessibilityEnabled(context)) add("Screen Guard is off")
            if (!DeviceOwner.isAdmin(context)) add("uninstall protection is off")
            if (Prefs.vpnWanted && VpnService.prepare(context) != null) add("the content filter is off")
        }
        if (problems.isNotEmpty()) {
            Notifier.tamper(context, problems.joinToString(", ").replaceFirstChar { it.uppercase() } +
                ". Tap to restore protection.")
        }
    }
}
