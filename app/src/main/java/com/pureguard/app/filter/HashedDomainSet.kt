package com.pureguard.app.filter

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.util.Arrays

/**
 * Memory-efficient set of domains: each domain is stored as a 64-bit FNV-1a
 * hash in a sorted LongArray (~8 bytes per entry instead of ~100 for Strings).
 * Membership is a binary search. Collisions at 64 bits are negligible.
 */
class HashedDomainSet private constructor(private val hashes: LongArray) {

    val size: Int get() = hashes.size

    operator fun contains(domain: String): Boolean = Arrays.binarySearch(hashes, hash(domain)) >= 0

    /** True if [domain] or any of its parent domains (above the TLD) is in the set. */
    fun matchesSelfOrParent(domain: String): Boolean {
        if (hashes.isEmpty()) return false
        var d = domain
        while (true) {
            if (d in this) return true
            val dot = d.indexOf('.')
            if (dot < 0) return false
            d = d.substring(dot + 1)
            if (d.indexOf('.') < 0) return false // don't match a bare TLD
        }
    }

    fun writeTo(file: File) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(hashes.size)
            for (h in hashes) out.writeLong(h)
        }
        if (!tmp.renameTo(file)) {
            file.delete(); tmp.renameTo(file)
        }
    }

    class Builder {
        private var arr = LongArray(1 shl 17)
        private var n = 0

        fun add(rawDomain: String) {
            val d = normalize(rawDomain) ?: return
            if (n == arr.size) arr = arr.copyOf(arr.size * 2)
            arr[n++] = hash(d)
        }

        /** Accepts hosts-file lines ("0.0.0.0 x.com"), plain lists and "*.x.com" wildcards. */
        fun addListLine(line: String) {
            var s = line.substringBefore('#').trim()
            if (s.isEmpty() || s.startsWith("!") || s.startsWith("[")) return
            val parts = s.split(' ', '\t').filter { it.isNotEmpty() }
            s = when {
                parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::") -> parts[1]
                else -> parts[0]
            }
            s = s.removePrefix("||").removeSuffix("^").removePrefix("*.").removePrefix(".")
            if (s == "localhost" || s == "0.0.0.0" || !s.contains('.')) return
            add(s)
        }

        fun addAll(input: InputStream) {
            input.bufferedReader().useLines { lines -> lines.forEach(::addListLine) }
        }

        fun build(): HashedDomainSet {
            val sorted = arr.copyOf(n)
            sorted.sort()
            // de-duplicate in place
            var w = 0
            for (i in sorted.indices) {
                if (w == 0 || sorted[i] != sorted[w - 1]) sorted[w++] = sorted[i]
            }
            return HashedDomainSet(sorted.copyOf(w))
        }
    }

    companion object {
        private const val MAGIC = 0x50475331 // "PGS1"
        val EMPTY = HashedDomainSet(LongArray(0))

        fun readFrom(file: File): HashedDomainSet? = try {
            DataInputStream(file.inputStream().buffered(1 shl 16)).use { input ->
                if (input.readInt() != MAGIC) return null
                val count = input.readInt()
                if (count < 0 || count > 20_000_000) return null
                val a = LongArray(count) { input.readLong() }
                HashedDomainSet(a)
            }
        } catch (_: Exception) {
            null
        }

        fun normalize(raw: String): String? {
            val d = raw.trim().trimEnd('.').lowercase()
            if (d.length < 3 || d.length > 253 || !d.contains('.')) return null
            return d
        }

        fun hash(s: String): Long {
            var h = -0x340d631b7bdddcdbL // FNV-1a 64 offset basis
            for (c in s) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return h
        }
    }
}
