package com.pureguard.app.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.pureguard.app.R
import com.pureguard.app.core.Notifier
import com.pureguard.app.core.Prefs

/**
 * Device administrator: while active, Android refuses to uninstall PureGuard.
 * Deactivation is additionally blocked by the accessibility guard.
 */
class GuardAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Prefs.init(context)
        DeviceOwner.applyPolicies(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Prefs.init(context)
        if (Prefs.enforcing) {
            Notifier.tamper(context, "Uninstall protection was removed. Open PureGuard to turn it back on.")
        }
    }

    companion object {
        fun component(context: Context) = ComponentName(context, GuardAdminReceiver::class.java)
    }
}
