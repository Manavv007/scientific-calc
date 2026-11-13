package com.pureguard.app.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.pureguard.app.core.Protection

/** Runs every 15 minutes and restores any protection layer that went missing. */
class WatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Protection.heal(applicationContext)
        return Result.success()
    }
}
