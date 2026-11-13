package com.pureguard.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pureguard.app.core.Prefs
import com.pureguard.app.filter.DomainFilter
import com.pureguard.app.filter.HashedDomainSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Refreshes the adult-domain list from community-maintained sources and
 * merges it with the list bundled in the app (so an update can only ever add
 * domains, never shrink protection).
 */
class BlocklistUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BlocklistUpdate"
        val SOURCES = listOf(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/nsfw-onlydomains.txt",
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Prefs.init(applicationContext)
        val builder = HashedDomainSet.Builder()
        applicationContext.assets.open(DomainFilter.ASSET).use {
            builder.addAll(GZIPInputStream(it))
        }
        var fetched = 0
        for (src in SOURCES) {
            try {
                val conn = (URL(src).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "PureGuard")
                }
                conn.inputStream.use { builder.addAll(it) }
                conn.disconnect()
                fetched++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch $src", e)
            }
        }
        if (fetched == 0) return@withContext Result.retry()
        val set = builder.build()
        DomainFilter.install(applicationContext, set)
        Prefs.listUpdatedAt = System.currentTimeMillis()
        Log.i(TAG, "Blocklist updated: ${set.size} domains from $fetched sources")
        Result.success()
    }
}
