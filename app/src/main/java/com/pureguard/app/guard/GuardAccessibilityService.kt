package com.pureguard.app.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.pureguard.app.R
import com.pureguard.app.core.PaymentApps
import com.pureguard.app.core.Prefs
import com.pureguard.app.core.Protection
import com.pureguard.app.filter.BuiltInLists
import com.pureguard.app.filter.ContentClassifier
import com.pureguard.app.filter.DomainFilter
import com.pureguard.app.ui.BlockedActivity
import com.pureguard.app.ui.MainActivity
import com.pureguard.app.vpn.ShieldVpnService
import java.util.ArrayDeque

/**
 * "Screen Guard". Works on top of the DNS filter and catches what DNS can't:
 *  - adult URLs and search queries in any browser's address bar
 *  - adult searches typed into a browser
 *  - optionally, screens full of explicit text
 * It never inspects banking / payment apps.
 *  - bypass apps (Tor, VPN apps, proxy browsers)
 *  - attempts to disable, force-stop or uninstall PureGuard while locked
 */
class GuardAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var connected = false
            private set

        @Volatile private var instance: GuardAccessibilityService? = null

        /**
         * Switches the service off completely (used by the payment pause), so
         * payment apps no longer see an active accessibility service.
         */
        fun pauseSelf() {
            val s = instance ?: return
            connected = false
            runCatching { s.disableSelf() }
        }

        private const val SCAN_DEBOUNCE_MS = 600L
        private const val BLOCK_COOLDOWN_MS = 1500L
        private const val HEAL_INTERVAL_MS = 20_000L
        private const val VPN_APPS_REFRESH_MS = 5 * 60_000L
        private const val MAX_NODES = 600

        /** System screens where PureGuard can be switched off. */
        private val GUARDED_SYSTEM_PACKAGES = setOf(
            "com.android.settings", "com.samsung.android.settings", "com.miui.securitycenter",
            "com.coloros.safecenter", "com.oplus.safecenter", "com.huawei.systemmanager",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.samsung.accessibility",
            "com.samsung.android.lool", "com.samsung.android.sm.devicesecurity",
        )

        private val DANGER_PHRASES = listOf(
            "uninstall", "force stop", "force close", "deactivate", "turn off",
            "clear storage", "clear data", "delete data", "manage space",
            "disconnect", "forget", "always-on vpn", "stop service", "remove",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingScanPkg: String? = null
    private var lastBlockAt = 0L
    private var lastHealAt = 0L
    private var lastVpnAppsAt = 0L
    private var vpnApps: Set<String> = emptySet()
    private var keyboards: Set<String> = emptySet()
    private lateinit var label: String

    private val scanRunnable = Runnable {
        val pkg = pendingScanPkg ?: return@Runnable
        pendingScanPkg = null
        scanScreen(pkg)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Prefs.init(this)
        connected = true
        instance = this
        Prefs.screenGuardWanted = true
        label = getString(R.string.app_name).lowercase()
        DomainFilter.loadAsync(this)
        refreshKeyboards()
        refreshVpnApps()
        Protection.heal(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        // Never look at, or act on, banking / UPI / wallet apps.
        if (PaymentApps.isPayment(pkg)) return
        if (Prefs.paused) return

        periodicMaintenance()

        // 1) Self-protection: don't let a locked PureGuard be switched off.
        if (Prefs.enforcing && pkg in GUARDED_SYSTEM_PACKAGES && guardSelf(pkg)) return

        // 2) Bypass / adult apps.
        if (isBlockedApp(pkg)) {
            block("This app can be used to get around the filter", goHome = true)
            return
        }

        if (pkg in keyboards || pkg == "com.android.systemui") return

        // 3) Searches typed into a browser (other apps are left alone).
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && Prefs.typingGuard &&
            Browsers.isBrowser(pkg)
        ) {
            val typed = event.text?.joinToString(" ") ?: ""
            if (typed.length in 3..200 && ContentClassifier.isAdultQuery(typed)) {
                block("Adult search blocked", goHome = false)
                return
            }
        }

        // 4) Browser address bars.
        if (Browsers.isBrowser(pkg) && checkBrowser(pkg)) return

        // 5) Whole-screen text scan (debounced).
        if (Prefs.screenScan) {
            pendingScanPkg = pkg
            handler.removeCallbacks(scanRunnable)
            handler.postDelayed(scanRunnable, SCAN_DEBOUNCE_MS)
        }
    }

    // ------------------------------------------------------------------ guards

    private fun guardSelf(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val text = collectText(root, 400).lowercase()

        val dangerous = when {
            pkg == "com.android.vpndialogs" -> Prefs.vpnWanted
            text.contains(label) && DANGER_PHRASES.any { text.contains(it) } -> true
            text.contains("use $label") || text.contains("$label shortcut") -> true
            // Private DNS dialog would let someone point DNS around the filter.
            text.contains("private dns provider hostname") ||
                (text.contains("private dns") && text.contains("hostname") && text.contains("automatic")) -> true
            // Adding a second user / guest would give an unfiltered profile.
            (text.contains("add user") || text.contains("add guest")) && text.contains("users") -> true
            else -> false
        }
        if (!dangerous) return false

        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 120)
        Toast.makeText(this, "Protected by PureGuard — the password is required", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun isBlockedApp(pkg: String): Boolean {
        if (pkg in BuiltInLists.BLOCKED_PACKAGES) return true
        if (pkg in Prefs.customApps) return true
        if (Prefs.blockOtherVpnApps && pkg in vpnApps) return true
        return false
    }

    private fun checkBrowser(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val ids = Browsers.URL_BAR_IDS[pkg].orEmpty()
        for (id in ids) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull() ?: continue
            for (node in nodes) {
                val text = node.text?.toString() ?: continue
                if (text.isBlank()) continue
                if (isAdultAddress(text)) {
                    block("Adult website blocked", goHome = true)
                    return true
                }
            }
        }
        return false
    }

    private fun isAdultAddress(text: String): Boolean {
        val host = DomainFilter.hostFromUrl(text)
        if (host != null && DomainFilter.isBlocked(host)) return true
        // Searches show their query in the bar; URLs carry it as ?q=...
        val decoded = runCatching { java.net.URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
        return ContentClassifier.isAdultQuery(decoded)
    }

    private fun scanScreen(pkg: String) {
        if (pkg == packageName) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val text = collectText(root, MAX_NODES)
        if (ContentClassifier.isAdultScreen(text)) {
            block("Explicit content detected on screen", goHome = true)
        }
    }

    // ------------------------------------------------------------------ actions

    private fun block(reason: String, goHome: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < BLOCK_COOLDOWN_MS) return
        lastBlockAt = now
        Prefs.recordBlock()
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (goHome) handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 80)
        handler.postDelayed({
            startActivity(
                Intent(this, BlockedActivity::class.java)
                    .putExtra(BlockedActivity.EXTRA_REASON, reason)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }, 160)
    }

    private fun periodicMaintenance() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHealAt > HEAL_INTERVAL_MS) {
            lastHealAt = now
            Protection.heal(this)
            // VPN permission lost (another VPN took over): ask again right away.
            if (Prefs.vpnWanted && !ShieldVpnService.isRunning && VpnService.prepare(this) != null) {
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_REQUEST_VPN, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        if (now - lastVpnAppsAt > VPN_APPS_REFRESH_MS) {
            lastVpnAppsAt = now
            refreshVpnApps()
            refreshKeyboards()
        }
    }

    /** Every installed app that ships its own VpnService could replace our filter. */
    private fun refreshVpnApps() {
        vpnApps = runCatching {
            packageManager.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), 0)
                .map { it.serviceInfo.packageName }
                .filter { it != packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun refreshKeyboards() {
        keyboards = runCatching {
            getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun collectText(root: AccessibilityNodeInfo, limit: Int): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < limit) {
            val node = queue.removeFirst()
            visited++
            node.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
            node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return sb.toString()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        connected = false
        instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        instance = null
        return super.onUnbind(intent)
    }
}
