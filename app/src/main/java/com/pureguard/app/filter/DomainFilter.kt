package com.pureguard.app.filter

import android.content.Context
import android.util.Log
import com.pureguard.app.core.Prefs
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

/** The single source of truth for "is this hostname allowed?". */
object DomainFilter {
    private const val TAG = "DomainFilter"
    const val ASSET = "adult_domains.txt.gz"
    private const val CACHE = "blocklist.bin"

    sealed interface Verdict {
        data object Allow : Verdict
        data class Block(val reason: String) : Verdict
        data class Rewrite(val target: String) : Verdict
    }

    @Volatile private var set: HashedDomainSet = HashedDomainSet.EMPTY
    @Volatile var ready = false
        private set

    val size: Int get() = set.size

    private val tokenRegex = Regex(BuiltInLists.DOMAIN_TOKENS.joinToString("|") { Regex.escape(it) })

    fun loadAsync(context: Context) {
        if (ready) return
        val app = context.applicationContext
        thread(name = "blocklist-load", isDaemon = true) { load(app) }
    }

    @Synchronized
    fun load(context: Context) {
        if (ready) return
        val cache = File(context.filesDir, CACHE)
        val cached = HashedDomainSet.readFrom(cache)
        set = cached ?: buildFromAsset(context).also {
            runCatching { it.writeTo(cache) }
        }
        ready = true
        Log.i(TAG, "Blocklist ready with ${set.size} domains")
    }

    fun buildFromAsset(context: Context): HashedDomainSet {
        val b = HashedDomainSet.Builder()
        context.assets.open(ASSET).use { b.addAll(GZIPInputStream(it)) }
        return b.build()
    }

    /** Called by the updater after downloading fresh lists. */
    fun install(context: Context, newSet: HashedDomainSet) {
        newSet.writeTo(File(context.filesDir, CACHE))
        set = newSet
        ready = true
    }

    fun verdict(rawHost: String): Verdict {
        val host = rawHost.trim().trimEnd('.').lowercase()
        if (host.isEmpty()) return Verdict.Allow

        if (Prefs.safeSearch) safeSearchTarget(host)?.let { return Verdict.Rewrite(it) }

        if (host in BuiltInLists.NEVER_BLOCK) return Verdict.Allow
        if (matchesAny(host, Prefs.customAllowed)) return Verdict.Allow
        if (matchesAny(host, Prefs.customBlocked)) return Verdict.Block("Your blocklist")
        if (Prefs.blockBypassTools && matchesAny(host, BuiltInLists.BYPASS_DOMAINS)) {
            return Verdict.Block("Filter bypass service")
        }
        if (set.matchesSelfOrParent(host)) return Verdict.Block("Adult website")
        if (hasAdultToken(host)) return Verdict.Block("Adult website")
        return Verdict.Allow
    }

    fun isBlocked(host: String): Boolean = verdict(host) is Verdict.Block

    private fun safeSearchTarget(host: String): String? {
        BuiltInLists.SAFE_SEARCH_EXACT[host]?.let { return it }
        if (BuiltInLists.GOOGLE_SEARCH.matches(host)) return BuiltInLists.GOOGLE_SAFE
        return null
    }

    private fun hasAdultToken(host: String): Boolean {
        var h = host
        for (e in BuiltInLists.TOKEN_EXCEPTIONS) h = h.replace(e, "")
        return tokenRegex.containsMatchIn(h)
    }

    private fun matchesAny(host: String, domains: Set<String>): Boolean {
        if (domains.isEmpty()) return false
        for (d in domains) {
            if (host == d || host.endsWith(".$d")) return true
        }
        return false
    }

    /** Extracts a hostname from anything that looks like a URL or address-bar text. */
    fun hostFromUrl(text: String): String? {
        var s = text.trim().lowercase()
        if (s.isEmpty() || s.contains(' ')) return null
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfter('@').substringBefore(':')
        if (!s.contains('.') || s.length < 4) return null
        if (!s.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
        return s
    }
}
