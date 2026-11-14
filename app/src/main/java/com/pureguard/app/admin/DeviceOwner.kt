package com.pureguard.app.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.pureguard.app.core.Prefs

/**
 * Device-admin helpers. If PureGuard has additionally been made device owner,
 * a few system-level restrictions are applied while locked. Safe mode, USB
 * debugging and factory reset are never restricted.
 */
object DeviceOwner {
    private const val TAG = "DeviceOwner"

    // Safe mode and USB debugging are deliberately NOT restricted: they are the
    // rescue routes if anything ever goes wrong.
    private val RESTRICTIONS = buildList {
        add(UserManager.DISALLOW_ADD_USER) // a second user/guest has no filter
        if (Build.VERSION.SDK_INT >= 29) add(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
    }

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
                @Suppress("DEPRECATION")
                dpm.clearDeviceOwnerApp(context.packageName)
            } catch (e: Exception) {
                Log.e(TAG, "release failed", e)
            }
        }
        if (isAdmin(context)) runCatching { dpm.removeActiveAdmin(admin) }
    }
}
