package com.pureguard.app

import com.pureguard.app.core.PasswordHash
import com.pureguard.app.core.PaymentApps
import com.pureguard.app.filter.ContentClassifier
import com.pureguard.app.filter.HashedDomainSet
import com.pureguard.app.vpn.Dns
import com.pureguard.app.vpn.Packets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Pure-logic checks that run on every build, before anything is released. */
class LogicTest {

    @Test
    fun passwordFileMatchesTheHashInTheApp() {
        val file = listOf(File("../PASSWORD.txt"), File("PASSWORD.txt")).first { it.exists() }
        val password = file.readLines()[3].trim()
        val spec = PBEKeySpec(
            password.toCharArray(), Base64.getDecoder().decode(PasswordHash.SALT_B64),
            PasswordHash.ITERATIONS, 256,
        )
        val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        assertArrayEquals(Base64.getDecoder().decode(PasswordHash.HASH_B64), actual)
    }

    @Test
    fun paymentAppsAreRecognised() {
        listOf(
            "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user",
            "in.org.npci.upiapp", "com.sbi.lotusintouch", "com.snapwork.hdfc", "com.axis.mobile",
            "com.csam.icici.bank.imobile", "com.somebank.mobile", "com.example.upi",
        ).forEach { assertTrue(it, PaymentApps.isPayment(it)) }
        listOf("com.android.chrome", "com.whatsapp", "com.instagram.android", "org.mozilla.firefox")
            .forEach { assertFalse(it, PaymentApps.isPayment(it)) }
    }

    @Test
    fun classifierBlocksExplicitAndAllowsOrdinaryText() {
        assertTrue(ContentClassifier.isAdultQuery("free porn videos"))
        assertTrue(ContentClassifier.isAdultQuery("https://www.google.com/search?q=xxx+videos"))
        assertFalse(ContentClassifier.isAdultQuery("weather in mumbai tomorrow"))
        assertFalse(ContentClassifier.isAdultQuery("pay electricity bill online"))
        assertFalse(ContentClassifier.isAdultQuery("essex county council"))
        assertFalse(ContentClassifier.isAdultScreen("Send money to Rahul. Enter UPI PIN. Pay 500 rupees."))
        assertFalse(ContentClassifier.isAdultScreen("bikini bikini bikini bikini bikini bikini bikini"))
    }

    @Test
    fun domainSetMatchesSubdomainsOnly() {
        val set = HashedDomainSet.Builder().apply {
            addListLine("0.0.0.0 bad-site.com")
            addListLine("*.other-bad.net")
            addListLine("# comment")
        }.build()
        assertEquals(2, set.size)
        assertTrue(set.matchesSelfOrParent("bad-site.com"))
        assertTrue(set.matchesSelfOrParent("cdn.video.bad-site.com"))
        assertTrue(set.matchesSelfOrParent("www.other-bad.net"))
        assertFalse(set.matchesSelfOrParent("good-site.com"))
        assertFalse(set.matchesSelfOrParent("notbad-site.com"))
    }

    @Test
    fun dnsQueryRoundTrip() {
        val query = Dns.buildQuery(0x1234, "www.example.com", Dns.TYPE_A)
        val parsed = Dns.parseQuery(query)
        assertNotNull(parsed)
        assertEquals("www.example.com", parsed!!.name)
        assertEquals(Dns.TYPE_A, parsed.qtype)

        val blocked = Dns.blockedResponse(parsed)
        assertEquals(0x12, blocked[0].toInt()); assertEquals(0x34, blocked[1].toInt())
        assertEquals(Dns.RCODE_NXDOMAIN, blocked[3].toInt() and 0x0F)

        val answer = Dns.answerResponse(parsed, listOf(Dns.Record(Dns.TYPE_A, 300, byteArrayOf(1, 2, 3, 4))))
        val records = Dns.answers(answer, Dns.TYPE_A)
        assertEquals(1, records.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), records[0].data)
    }

    @Test
    fun udpReplySwapsAddressesAndHasValidChecksum() {
        val payload = Dns.buildQuery(7, "a.com", Dns.TYPE_A)
        val req = Packets.Parsed(
            4, Packets.PROTO_UDP, byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 111, 222.toByte(), 53),
            40000, 53, payload,
        )
        val reply = Packets.udpReply(req, payload)
        val back = Packets.parse(reply, reply.size)!!
        assertArrayEquals(req.dst, back.src)
        assertArrayEquals(req.src, back.dst)
        assertEquals(53, back.srcPort)
        assertEquals(40000, back.dstPort)
        assertArrayEquals(payload, back.payload)
        // IPv4 header checksum must verify to zero.
        var sum = 0
        for (i in 0 until 20 step 2) sum += ((reply[i].toInt() and 0xFF) shl 8) or (reply[i + 1].toInt() and 0xFF)
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        assertEquals(0xFFFF, sum)
    }
}
