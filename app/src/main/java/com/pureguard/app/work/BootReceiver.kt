package com.pureguard.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pureguard.app.core.Prefs
import com.pureguard.app.core.Protection

/** Brings protection back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Credential-encrypted storage isn't available during LOCKED_BOOT_COMPLETED.
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        Prefs.init(context)
        Protection.heal(context)
        Scheduler.scheduleAll(context)
    }
}
