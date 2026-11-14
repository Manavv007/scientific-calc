package com.pureguard.app.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pureguard.app.core.Notifier
import com.pureguard.app.core.PaymentApps
import com.pureguard.app.core.Prefs
import com.pureguard.app.filter.BuiltInLists
import com.pureguard.app.filter.DomainFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local "VPN" that never leaves the phone. It only captures DNS (and traffic to
 * well-known public resolvers), filters every lookup against the blocklists,
 * enforces SafeSearch, and forwards the rest to family-safe resolvers that
 * block adult content a second time.
 */
class ShieldVpnService : VpnService() {

    companion object {
        private const val TAG = "ShieldVpn"
        const val ACTION_START = "com.pureguard.app.START"
        const val ACTION_STOP = "com.pureguard.app.STOP"
        const val ACTION_PAUSE = "com.pureguard.app.PAUSE"

        private const val V4_ADDR = "10.111.222.1"
        private const val V4_DNS = "10.111.222.53"
        private const val V6_ADDR = "fd00:7067:6172:64::1"
        private const val V6_DNS = "fd00:7067:6172:64::53"

        /**
         * Upstream resolvers that themselves block adult content:
         * Cloudflare for Families and CleanBrowsing Adult filter.
         */
        private val UPSTREAMS = listOf(
            "1.1.1.3", "1.0.0.3", "185.228.168.10", "185.228.169.11",
            "2606:4700:4700::1113", "2606:4700:4700::1003",
        )

        @Volatile var isRunning = false
            private set
        @Volatile var alwaysOnActive = false
            private set
        @Volatile var lockdownActive = false
            private set

        fun start(context: Context) {
            Prefs.init(context)
            Prefs.vpnWanted = true
            val i = Intent(context, ShieldVpnService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService failed, trying plain start", e)
                runCatching { context.startService(i) }
            }
        }

        /** Temporary stop for the payment pause; [Protection.heal] starts it again. */
        fun pause(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(Intent(context, ShieldVpnService::class.java).setAction(ACTION_PAUSE))
            }
        }

        /** Only honoured when the password session is active (or not yet locked). */
        fun stop(context: Context) {
            if (!Prefs.canEdit) return
            Prefs.vpnWanted = false
            context.startService(Intent(context, ShieldVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var workers: ExecutorService? = null
    private val upstreamIndex = AtomicInteger(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Prefs.init(this)
        goForeground()
        val stop = intent?.action == ACTION_PAUSE || (intent?.action == ACTION_STOP && Prefs.canEdit)
        if (stop || Prefs.paused) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tun == null) establish()
        return START_STICKY
    }

    private fun goForeground() {
        try {
            ServiceCompat.startForeground(
                this, Notifier.ID_SERVICE, Notifier.serviceNotification(this),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not enter foreground", e)
        }
    }

    private fun establish() {
        DomainFilter.loadAsync(this)
        val b = Builder()
            .setSession("PureGuard filter")
            .setMtu(1500)
            .setBlocking(true)
            .addAddress(V4_ADDR, 32)
            .addDnsServer(V4_DNS)
            .addRoute(V4_DNS, 32)

        for (ip in BuiltInLists.BLACKHOLE_V4) runCatching { b.addRoute(ip, 32) }
        runCatching {
            b.addAddress(V6_ADDR, 128)
            b.addDnsServer(V6_DNS)
            b.addRoute(V6_DNS, 128)
            for (ip in BuiltInLists.BLACKHOLE_V6) runCatching { b.addRoute(ip, 128) }
        }
        // Our own traffic (blocklist updates, upstream DNS) never loops through the tunnel.
        runCatching { b.addDisallowedApplication(packageName) }
        // Banking / UPI / wallet apps bypass the tunnel completely.
        for (pkg in PaymentApps.installed(this)) runCatching { b.addDisallowedApplication(pkg) }
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)

        val pfd = try {
            b.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish failed", e); null
        }
        if (pfd == null) {
            // Permission revoked or another VPN holds the slot.
            isRunning = false
            Notifier.tamper(this, "The content filter is off. Tap to turn it back on.")
            stopSelf()
            return
        }
        tun = pfd
        isRunning = true
        if (Build.VERSION.SDK_INT >= 29) {
            alwaysOnActive = this.isAlwaysOn
            lockdownActive = this.isLockdownEnabled
        }
        Notifier.clearTamper(this)
        workers = Executors.newFixedThreadPool(8)
        reader = Thread({ loop(pfd) }, "vpn-reader").also { it.start() }
        Log.i(TAG, "Tunnel established")
    }

    private fun loop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = input.read(buf)
                if (n <= 0) continue
                val pkt = Packets.parse(buf, n) ?: continue
                when {
                    pkt.protocol == Packets.PROTO_UDP && pkt.dstPort == 53 ->
                        workers?.execute { handleDns(pkt, output) }
                    pkt.protocol == Packets.PROTO_TCP && (pkt.tcpFlags and 0x04) == 0 ->
                        // DoH/DoT/TCP-DNS to a sink-holed resolver: refuse instantly.
                        write(output, Packets.tcpReset(pkt))
                    else -> Unit // drop (e.g. DoQ/UDP 443/853 to resolvers)
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "Tunnel closed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Reader crashed", e)
        }
    }

    private fun handleDns(pkt: Packets.Parsed, output: FileOutputStream) {
        val q = Dns.parseQuery(pkt.payload)
        if (q == null) {
            forward(pkt.payload)?.let { write(output, Packets.udpReply(pkt, it)) }
            return
        }
        val answer: ByteArray? = when (val v = DomainFilter.verdict(q.name)) {
            is DomainFilter.Verdict.Block -> {
                Prefs.recordBlock()
                Dns.blockedResponse(q)
            }
            is DomainFilter.Verdict.Rewrite -> safeSearch(q, v.target)
            DomainFilter.Verdict.Allow -> forward(pkt.payload)
        }
        if (answer != null) write(output, Packets.udpReply(pkt, answer))
    }

    /** Resolves the enforced SafeSearch host and answers as if it were the original name. */
    private fun safeSearch(q: Dns.Query, target: String): ByteArray {
        if (q.qtype != Dns.TYPE_A && q.qtype != Dns.TYPE_AAAA) {
            return Dns.answerResponse(q, emptyList())
        }
        val resp = forward(Dns.buildQuery(q.id, target, q.qtype))
        val records = if (resp != null) Dns.answers(resp, q.qtype) else emptyList()
        return Dns.answerResponse(q, records)
    }

    private fun forward(query: ByteArray): ByteArray? {
        val start = upstreamIndex.get()
        for (k in UPSTREAMS.indices) {
            val idx = (start + k) % UPSTREAMS.size
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 2500
                    val addr = InetAddress.getByName(UPSTREAMS[idx])
                    s.send(DatagramPacket(query, query.size, addr, 53))
                    val rb = ByteArray(4096)
                    val rp = DatagramPacket(rb, rb.size)
                    s.receive(rp)
                    upstreamIndex.set(idx)
                    return rb.copyOf(rp.length)
                }
            } catch (_: Exception) {
                // try the next upstream
            }
        }
        return null
    }

    private fun write(output: FileOutputStream, packet: ByteArray) {
        synchronized(output) {
            try {
                output.write(packet)
            } catch (_: IOException) {
            }
        }
    }

    private fun shutdown() {
        isRunning = false
        reader?.interrupt()
        reader = null
        workers?.shutdownNow()
        workers = null
        runCatching { tun?.close() }
        tun = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        // The user (or another VPN app) took the VPN slot away.
        shutdown()
        if (Prefs.vpnWanted) {
            Notifier.tamper(this, "The content filter was switched off. Tap to turn it back on.")
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
