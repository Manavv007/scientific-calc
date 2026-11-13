package com.pureguard.app.vpn

import java.io.ByteArrayOutputStream

/** Minimal DNS message codec. */
object Dns {
    const val TYPE_A = 1
    const val TYPE_CNAME = 5
    const val TYPE_AAAA = 28
    const val RCODE_NXDOMAIN = 3

    class Query(
        val raw: ByteArray,
        val id: Int,
        val flags: Int,
        val name: String,
        val qtype: Int,
        val qclass: Int,
        /** Offset just past the (single) question section. */
        val questionEnd: Int,
    )

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)

    fun parseQuery(msg: ByteArray): Query? {
        if (msg.size < 17) return null
        val flags = u16(msg, 2)
        if (flags and 0x8000 != 0) return null // a response, not a query
        if (u16(msg, 4) != 1) return null // we only understand single-question queries
        val sb = StringBuilder()
        var i = 12
        while (true) {
            if (i >= msg.size) return null
            val len = u8(msg, i)
            if (len == 0) { i++; break }
            if (len and 0xC0 != 0) return null
            if (i + 1 + len > msg.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..len) sb.append(msg[i + k].toInt().toChar())
            i += 1 + len
        }
        if (i + 4 > msg.size) return null
        return Query(msg, u16(msg, 0), flags, sb.toString().lowercase(), u16(msg, i), u16(msg, i + 2), i + 4)
    }

    /** NXDOMAIN response echoing the question — the cleanest "does not exist". */
    fun blockedResponse(q: Query): ByteArray {
        val out = ByteArrayOutputStream()
        header(out, q, ancount = 0, rcode = RCODE_NXDOMAIN)
        out.write(q.raw, 12, q.questionEnd - 12)
        return out.toByteArray()
    }

    /** A fresh recursive query for [name] (used for SafeSearch rewriting). */
    fun buildQuery(id: Int, name: String, qtype: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id ushr 8); out.write(id)
        out.write(0x01); out.write(0x00) // RD
        out.write(0); out.write(1) // QDCOUNT
        repeat(6) { out.write(0) }
        writeName(out, name)
        out.write(qtype ushr 8); out.write(qtype)
        out.write(0); out.write(1)
        return out.toByteArray()
    }

    class Record(val type: Int, val ttl: Long, val data: ByteArray)

    /** Extracts answer records of [type] from a response. */
    fun answers(resp: ByteArray, type: Int): List<Record> {
        if (resp.size < 12) return emptyList()
        val qd = u16(resp, 4)
        val an = u16(resp, 6)
        var i = 12
        try {
            repeat(qd) { i = skipName(resp, i) + 4 }
            val result = ArrayList<Record>()
            repeat(an) {
                i = skipName(resp, i)
                val t = u16(resp, i)
                val ttl = (u16(resp, i + 4).toLong() shl 16) or u16(resp, i + 6).toLong()
                val rdlen = u16(resp, i + 8)
                val start = i + 10
                if (start + rdlen > resp.size) return result
                if (t == type) result += Record(t, ttl, resp.copyOfRange(start, start + rdlen))
                i = start + rdlen
            }
            return result
        } catch (_: IndexOutOfBoundsException) {
            return emptyList()
        }
    }

    /** Response to [q] containing [records] as answers for the original name. */
    fun answerResponse(q: Query, records: List<Record>): ByteArray {
        val out = ByteArrayOutputStream()
        header(out, q, ancount = records.size, rcode = 0)
        out.write(q.raw, 12, q.questionEnd - 12)
        for (r in records) {
            out.write(0xC0); out.write(0x0C) // pointer to the question name
            out.write(r.type ushr 8); out.write(r.type)
            out.write(0); out.write(1)
            val ttl = r.ttl.coerceIn(60, 3600)
            out.write((ttl ushr 24).toInt()); out.write((ttl ushr 16).toInt())
            out.write((ttl ushr 8).toInt()); out.write(ttl.toInt())
            out.write(r.data.size ushr 8); out.write(r.data.size)
            out.write(r.data)
        }
        return out.toByteArray()
    }

    /** Rewrites the response ID (responses to our own queries carry our ID). */
    fun withId(resp: ByteArray, id: Int): ByteArray {
        val r = resp.copyOf()
        r[0] = (id ushr 8).toByte(); r[1] = id.toByte()
        return r
    }

    private fun header(out: ByteArrayOutputStream, q: Query, ancount: Int, rcode: Int) {
        out.write(q.id ushr 8); out.write(q.id)
        val flags = 0x8000 or (q.flags and 0x7900) or 0x0080 or rcode // QR, opcode+RD, RA
        out.write(flags ushr 8); out.write(flags)
        out.write(0); out.write(1)
        out.write(ancount ushr 8); out.write(ancount)
        repeat(4) { out.write(0) }
    }

    private fun skipName(b: ByteArray, start: Int): Int {
        var i = start
        while (true) {
            val len = u8(b, i)
            when {
                len == 0 -> return i + 1
                len and 0xC0 == 0xC0 -> return i + 2
                else -> i += 1 + len
            }
        }
    }

    private fun writeName(out: ByteArrayOutputStream, name: String) {
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
    }
}
