package com.pureguard.app.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.pureguard.app.core.Prefs

/**
 * Extra-strong mode, available only when PureGuard is made "device owner" with
 *   adb shell dpm set-device-owner com.pureguard.app/.admin.GuardAdminReceiver
 * In this mode Android itself enforces the rules below; no settings trick can
 * undo them. Factory reset is intentionally left available as a last resort.
 */
object DeviceOwner {
    private const val TAG = "DeviceOwner"

    private val RESTRICTIONS = buildList {
        add(UserManager.DISALLOW_CONFIG_VPN)          // can't switch VPN / always-on off
        add(UserManager.DISALLOW_SAFE_BOOT)           // safe mode would disable PureGuard
        add(UserManager.DISALLOW_ADD_USER)            // a second user/guest has no filter
        add(UserManager.DISALLOW_DEBUGGING_FEATURES)  // no adb uninstall
        if (Build.VERSION.SDK_INT >= 29) add(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
    }.distinct()

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    fun isAdmin(context: Context) = dpm(context).isAdminActive(GuardAdminReceiver.component(context))

    fun isOwner(context: Context) = dpm(context).isDeviceOwnerApp(context.packageName)

    /** Applies all hard restrictions. Safe to call repeatedly. */
    fun applyPolicies(context: Context) {
        if (!isOwner(context) || !Prefs.locked) return
        val dpm = dpm(context)
        val admin = GuardAdminReceiver.component(context)
        try {
            RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }
            dpm.setUninstallBlocked(admin, context.packageName, true)
            dpm.setAlwaysOnVpnPackage(admin, context.packageName, false)
            if (Build.VERSION.SDK_INT >= 29) {
                dpm.setGlobalPrivateDnsModeOpportunistic(admin)
            }
            // Keep our accessibility service allowed.
            dpm.setPermittedAccessibilityServices(admin, null)
        } catch (e: Exception) {
            Log.e(TAG, "applyPolicies failed", e)
        }
    }

    /** Removes everything so the app can be uninstalled. Requires an unlocked session. */
    fun releaseAll(context: Context) {
        if (!Prefs.canEdit) return
        val dpm = dpm(context)
        val admin = GuardAdminReceiver.component(context)
        if (isOwner(context)) {
            try {
                RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }
                dpm.setUninstallBlocked(admin, context.packageName, false)
                dpm.setAlwaysOnVpnPackage(admin, null, false)
                @Suppress("DEPRECATION")
                dpm.clearDeviceOwnerApp(context.packageName)
            } catch (e: Exception) {
                Log.e(TAG, "release failed", e)
            }
        }
        if (isAdmin(context)) runCatching { dpm.removeActiveAdmin(admin) }
    }
}
