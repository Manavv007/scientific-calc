package com.pureguard.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val WATCHDOG = "watchdog"
    private const val UPDATE = "blocklist-update"
    private const val UPDATE_NOW = "blocklist-update-now"

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WATCHDOG, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            UPDATE, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(3, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setInitialDelay(6, TimeUnit.HOURS)
                .build(),
        )
    }

    fun updateNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BlocklistUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}
