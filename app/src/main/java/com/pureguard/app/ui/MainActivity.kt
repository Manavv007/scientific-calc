package com.pureguard.app.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.pureguard.app.admin.DeviceOwner
import com.pureguard.app.admin.GuardAdminReceiver
import com.pureguard.app.core.PasswordVault
import com.pureguard.app.core.Prefs
import com.pureguard.app.core.Protection
import com.pureguard.app.ui.theme.PureGuardTheme
import com.pureguard.app.vpn.ShieldVpnService
import com.pureguard.app.work.Scheduler

class MainActivity : ComponentActivity(), HomeActions {

    companion object {
        const val EXTRA_REQUEST_VPN = "request_vpn"
    }

    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(this) == null) ShieldVpnService.start(this)
        else toast("The content filter needs VPN permission to work")
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        handleIntent(intent)
        setContent {
            PureGuardTheme {
                HomeScreen(actions = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Protection.heal(this)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_VPN, false) == true) enableVpn()
    }

    // ----------------------------------------------------------- HomeActions

    override fun enableVpn() {
        val prepare = VpnService.prepare(this)
        if (prepare == null) ShieldVpnService.start(this) else vpnLauncher.launch(prepare)
    }

    override fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        toast("Find PureGuard → turn it on")
    }

    override fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        toast("Tap ⋮ (top right) → Allow restricted settings")
    }

    override fun enableAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, GuardAdminReceiver.component(this))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Prevents PureGuard from being uninstalled without the password.",
                ),
        )
    }

    override fun openVpnSettings() {
        if (!Prefs.canEdit) return
        startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        toast("Tap ⚙ next to PureGuard → turn on Always-on VPN")
    }

    override fun requestBattery() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            )
        }.onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    override fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun lock() {
        Prefs.locked = true
        Prefs.unlockedUntil = 0
        Prefs.vpnWanted = true
        DeviceOwner.applyPolicies(this)
        toast("PureGuard is now locked")
    }

    override fun relock() {
        PasswordVault.relock()
        DeviceOwner.applyPolicies(this)
        toast("Locked again")
    }

    override fun removeProtection() {
        if (!Prefs.canEdit) return
        DeviceOwner.releaseAll(this)
        ShieldVpnService.stop(this)
        Prefs.locked = false
        Prefs.vpnWanted = false
        startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")),
        )
    }

    override fun updateLists() {
        Scheduler.updateNow(this)
        toast("Updating blocklists in the background…")
    }

    override fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("command", text))
        toast("Copied")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
