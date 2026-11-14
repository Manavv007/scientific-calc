package com.pureguard.app

import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pureguard.app.core.PrivateDns
import com.pureguard.app.core.Prefs
import com.pureguard.app.core.Protection
import com.pureguard.app.filter.DomainFilter
import com.pureguard.app.ui.BlockedActivity
import com.pureguard.app.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a real Android emulator in CI before any release is published. */
@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<PureGuardApp>()

    private fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }.trim()
    }

    @Test
    fun homeScreenOpensAndStaysOpen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(4000)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun blockScreenOpens() {
        ActivityScenario.launch(BlockedActivity::class.java).use { scenario ->
            Thread.sleep(1500)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun bundledBlocklistLoads() {
        DomainFilter.load(context)
        assertTrue("blocklist has ${DomainFilter.size} entries", DomainFilter.size > 100_000)
        assertTrue(DomainFilter.isBlocked("pornhub.com"))
        assertFalse(DomainFilter.isBlocked("wikipedia.org"))
        assertFalse(DomainFilter.isBlocked("onlinesbi.sbi"))
    }

    @Test
    fun safeDnsFilterCanBeSwitchedOnAndOff() {
        shell("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        Prefs.init(context)
        assertTrue(Protection.hasSecureSettings(context))

        assertTrue(PrivateDns.apply(context))
        assertEquals("hostname", shell("settings get global private_dns_mode"))
        assertEquals(PrivateDns.wantedHost(), shell("settings get global private_dns_specifier"))
        assertTrue(PrivateDns.isActive(context))
        assertTrue(Protection.status(context).filterActive)

        assertTrue(PrivateDns.clear(context))
        assertEquals("opportunistic", shell("settings get global private_dns_mode"))
        assertFalse(PrivateDns.isActive(context))
    }

    @Test
    fun paymentPauseAndHealNeverCrash() {
        Prefs.init(context)
        assertTrue(Protection.startPause(context))
        assertTrue(Protection.status(context).pausedForMs > 0)
        Protection.endPause(context)
        assertEquals(0L, Protection.status(context).pausedForMs)
        Protection.heal(context)
    }
}
