package com.pureguard.app.vpn

/**
 * Minimal IPv4/IPv6 + UDP/TCP parsing and building — just enough to answer
 * DNS queries and to reset TCP connections to sink-holed resolvers.
 */
object Packets {
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    class Parsed(
        val version: Int,
        val protocol: Int,
        val src: ByteArray,
        val dst: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        /** UDP payload, or empty for TCP. */
        val payload: ByteArray,
        // TCP only
        val tcpSeq: Long = 0,
        val tcpFlags: Int = 0,
        val tcpPayloadLen: Int = 0,
    )

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
    private fun u32(b: ByteArray, i: Int) =
        (u8(b, i).toLong() shl 24) or (u8(b, i + 1).toLong() shl 16) or
            (u8(b, i + 2).toLong() shl 8) or u8(b, i + 3).toLong()

    fun parse(buf: ByteArray, len: Int): Parsed? {
        if (len < 20) return null
        return when (u8(buf, 0) ushr 4) {
            4 -> {
                val ihl = (u8(buf, 0) and 0x0F) * 4
                val total = minOf(u16(buf, 2), len)
                val proto = u8(buf, 9)
                // ignore non-first fragments
                if ((u16(buf, 6) and 0x1FFF) != 0) return null
                transport(buf, ihl, total, proto, 4, buf.copyOfRange(12, 16), buf.copyOfRange(16, 20))
            }
            6 -> {
                if (len < 40) return null
                val total = minOf(40 + u16(buf, 4), len)
                val next = u8(buf, 6)
                transport(buf, 40, total, next, 6, buf.copyOfRange(8, 24), buf.copyOfRange(24, 40))
            }
            else -> null
        }
    }

    private fun transport(
        b: ByteArray, off: Int, end: Int, proto: Int, ver: Int, src: ByteArray, dst: ByteArray,
    ): Parsed? = when (proto) {
        PROTO_UDP -> {
            if (end < off + 8) null else {
                val udpLen = u16(b, off + 4)
                val payEnd = minOf(off + udpLen, end)
                if (payEnd < off + 8) null
                else Parsed(ver, proto, src, dst, u16(b, off), u16(b, off + 2), b.copyOfRange(off + 8, payEnd))
            }
        }
        PROTO_TCP -> {
            if (end < off + 20) null else {
                val dataOff = (u8(b, off + 12) ushr 4) * 4
                Parsed(
                    ver, proto, src, dst, u16(b, off), u16(b, off + 2), ByteArray(0),
                    tcpSeq = u32(b, off + 4),
                    tcpFlags = u8(b, off + 13),
                    tcpPayloadLen = (end - off - dataOff).coerceAtLeast(0),
                )
            }
        }
        else -> null
    }

    /** Builds a UDP reply to [req] (addresses and ports swapped). */
    fun udpReply(req: Parsed, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val udp = ByteArray(udpLen)
        put16(udp, 0, req.dstPort)
        put16(udp, 2, req.srcPort)
        put16(udp, 4, udpLen)
        System.arraycopy(payload, 0, udp, 8, payload.size)
        return wrap(req.version, PROTO_UDP, req.dst, req.src, udp)
    }

    /** Builds a TCP RST|ACK answering a connection attempt, so it fails instantly. */
    fun tcpReset(req: Parsed): ByteArray {
        val tcp = ByteArray(20)
        put16(tcp, 0, req.dstPort)
        put16(tcp, 2, req.srcPort)
        // seq = 0
        var ackAdd = req.tcpPayloadLen.toLong()
        if (req.tcpFlags and 0x02 != 0) ackAdd += 1 // SYN
        if (req.tcpFlags and 0x01 != 0) ackAdd += 1 // FIN
        put32(tcp, 8, (req.tcpSeq + ackAdd) and 0xFFFFFFFFL)
        tcp[12] = (5 shl 4).toByte()
        tcp[13] = (0x04 or 0x10).toByte() // RST + ACK
        return wrap(req.version, PROTO_TCP, req.dst, req.src, tcp)
    }

    private fun wrap(ver: Int, proto: Int, src: ByteArray, dst: ByteArray, seg: ByteArray): ByteArray {
        val csumOffset = if (proto == PROTO_UDP) 6 else 16
        // Transport checksum (mandatory for IPv6 UDP and all TCP; we always set it)
        put16(seg, csumOffset, 0)
        var c = pseudoHeaderSum(src, dst, proto, seg.size)
        c = sum(seg, 0, seg.size, c)
        var cs = fold(c)
        if (cs == 0 && proto == PROTO_UDP) cs = 0xFFFF
        put16(seg, csumOffset, cs)

        return if (ver == 4) {
            val p = ByteArray(20 + seg.size)
            p[0] = 0x45
            put16(p, 2, p.size)
            put16(p, 6, 0x4000) // don't fragment
            p[8] = 64
            p[9] = proto.toByte()
            System.arraycopy(src, 0, p, 12, 4)
            System.arraycopy(dst, 0, p, 16, 4)
            put16(p, 10, fold(sum(p, 0, 20, 0)))
            System.arraycopy(seg, 0, p, 20, seg.size)
            p
        } else {
            val p = ByteArray(40 + seg.size)
            p[0] = 0x60
            put16(p, 4, seg.size)
            p[6] = proto.toByte()
            p[7] = 64
            System.arraycopy(src, 0, p, 8, 16)
            System.arraycopy(dst, 0, p, 24, 16)
            System.arraycopy(seg, 0, p, 40, seg.size)
            p
        }
    }

    private fun pseudoHeaderSum(src: ByteArray, dst: ByteArray, proto: Int, len: Int): Long {
        var c = sum(src, 0, src.size, 0)
        c = sum(dst, 0, dst.size, c)
        c += proto.toLong()
        c += len.toLong()
        return c
    }

    private fun sum(b: ByteArray, off: Int, len: Int, initial: Long): Long {
        var c = initial
        var i = off
        val end = off + len
        while (i + 1 < end) {
            c += u16(b, i).toLong(); i += 2
        }
        if (i < end) c += (u8(b, i) shl 8).toLong()
        return c
    }

    private fun fold(c0: Long): Int {
        var c = c0
        while (c ushr 16 != 0L) c = (c and 0xFFFF) + (c ushr 16)
        return (c.inv() and 0xFFFF).toInt()
    }

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte()
    }

    private fun put32(b: ByteArray, i: Int, v: Long) {
        b[i] = (v ushr 24).toByte(); b[i + 1] = (v ushr 16).toByte()
        b[i + 2] = (v ushr 8).toByte(); b[i + 3] = v.toByte()
    }
}
