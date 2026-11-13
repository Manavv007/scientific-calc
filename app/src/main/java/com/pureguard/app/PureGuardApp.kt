package com.pureguard.app

import android.app.Application
import com.pureguard.app.core.Notifier
import com.pureguard.app.core.Prefs
import com.pureguard.app.filter.DomainFilter
import com.pureguard.app.work.Scheduler

class PureGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notifier.createChannels(this)
        DomainFilter.loadAsync(this)
        Scheduler.scheduleAll(this)
    }
}
