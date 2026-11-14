package com.pureguard.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pureguard.app.BuildConfig
import com.pureguard.app.core.PasswordVault
import com.pureguard.app.core.Prefs
import com.pureguard.app.core.PrivateDns
import com.pureguard.app.core.Protection
import com.pureguard.app.core.ProtectionStatus
import com.pureguard.app.ui.theme.Good
import com.pureguard.app.ui.theme.Navy
import com.pureguard.app.ui.theme.NavyDeep
import com.pureguard.app.ui.theme.Teal
import com.pureguard.app.ui.theme.TealBright
import com.pureguard.app.ui.theme.Warn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface HomeActions {
    fun applyDns()
    fun openNetworkSettings()
    fun setDnsStrict(strict: Boolean)
    fun enableAdmin()
    fun openAccessibility()
    fun openAppInfo()
    fun enableVpn()
    fun pause()
    fun resume()
    fun requestBattery()
    fun requestNotifications()
    fun lock()
    fun relock()
    fun removeProtection()
    fun copy(text: String)
}

private const val CMD_SECURE =
    "adb shell pm grant com.pureguard.app android.permission.WRITE_SECURE_SETTINGS"

@Composable
fun HomeScreen(actions: HomeActions) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(Protection.status(context)) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            status = Protection.status(context)
            delay(1000)
        }
    }
    var showPassword by remember { mutableStateOf(false) }
    var showDnsSteps by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val refresh = { tick++; status = Protection.status(context) }
    val canEdit = !status.locked || status.unlockedForMs > 0

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(status) }
            item { Hero(status) }

            if (status.hasPausableLayers || status.pausedForMs > 0) {
                item { PauseCard(status, actions) }
            }

            item { SectionTitle("Core protection") }
            item {
                Layer(
                    Icons.Rounded.Dns, "Safe DNS filter",
                    "Blocks adult sites and forces SafeSearch in every app and browser, using Android's own " +
                        "Private DNS setting. Works alongside banking and payment apps.",
                    status.privateDns,
                    if (status.secureSettings) "Turn on" else "Set up",
                    { if (status.secureSettings) actions.applyDns() else showDnsSteps = true },
                    enabled = canEdit || !status.privateDns,
                )
            }
            item {
                Layer(
                    Icons.Rounded.AdminPanelSettings, "Uninstall protection",
                    "Android refuses to uninstall PureGuard while this is on.",
                    status.admin, "Activate", actions::enableAdmin,
                )
            }
            if (!status.notifications) {
                item {
                    Layer(
                        Icons.Rounded.Notifications, "Alerts",
                        "Lets PureGuard tell you if the filter is ever switched off.",
                        false, "Allow", actions::requestNotifications,
                    )
                }
            }
            if (!status.secureSettings) {
                item { ComputerSetup(actions) }
            }

            item { SectionTitle("Optional extras") }
            item {
                Text(
                    "These add strength, but some banking and payment apps refuse to run while they are on. " +
                        "If a payment fails, use “Pause for payments” at the top, or leave these off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                Layer(
                    Icons.Rounded.Visibility, "Screen Guard",
                    "Checks browser address bars and searches, blocks Tor and proxy apps, and guards " +
                        "PureGuard's settings while locked. Never looks at payment apps.",
                    status.accessibility, "Open settings", actions::openAccessibility,
                    hint = if (!status.accessibility)
                        "Shows “Restricted setting”? Tap OK, then App info → ⋮ → Allow restricted settings." else null,
                    hintAction = "App info" to actions::openAppInfo,
                )
            }
            item {
                Layer(
                    Icons.Rounded.VpnLock, "Backup VPN filter",
                    "A second on-device filter with 110,000+ adult sites. Payment apps are routed around it, " +
                        "but some still object to any active VPN.",
                    status.vpnRunning, "Turn on", actions::enableVpn,
                )
            }
            if ((status.accessibility || status.vpnRunning) && !status.batteryUnrestricted) {
                item {
                    Layer(
                        Icons.Rounded.BatteryChargingFull, "Keep extras running",
                        "Stops the battery optimiser from closing the optional extras.",
                        false, "Allow", actions::requestBattery,
                    )
                }
            }

            when {
                !status.locked -> item { LockCard(status.filterActive) { confirmLock = true } }
                status.unlockedForMs > 0 -> item {
                    UnlockedCard(status.unlockedForMs, actions::relock) { confirmRemove = true }
                }
                else -> item { LockedCard { showPassword = true } }
            }

            if (canEdit) {
                item { SectionTitle("Settings") }
                item { SettingsCard(tick, actions, refresh) }
                item { DomainListCard("Always block", "example.com", tick, refresh, allowList = false) }
                item { DomainListCard("Always allow", "work-site.com", tick, refresh, allowList = true) }
            }

            item {
                Text(
                    "PureGuard ${BuildConfig.VERSION_NAME}  ·  Everything runs on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp),
                )
            }
        }
    }

    if (showPassword) PasswordDialog(onDismiss = { showPassword = false; refresh() })

    if (showDnsSteps) {
        val host = PrivateDns.wantedHost()
        AlertDialog(
            onDismissRequest = { showDnsSteps = false },
            icon = { Icon(Icons.Rounded.Dns, null) },
            title = { Text("Turn on the Safe DNS filter") },
            text = {
                Column {
                    Text("1. Tap “Copy address”.")
                    Text("2. Tap “Open settings” and find Private DNS (search for it if needed).")
                    Text("3. Choose “Private DNS provider hostname”, paste the address and save.")
                    Spacer(Modifier.height(12.dp))
                    Text(host, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { actions.copy(host) }) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy address")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { actions.copy(host); actions.openNetworkSettings() }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showDnsSteps = false }) { Text("Done") } },
        )
    }

    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            icon = { Icon(Icons.Rounded.Lock, null) },
            title = { Text("Lock PureGuard?") },
            text = {
                Text(
                    "After locking, settings can only be changed with the password, which is kept in " +
                        "PASSWORD.txt in the project. You can still pause the optional extras for payments " +
                        "without it.",
                )
            },
            confirmButton = {
                Button(onClick = { confirmLock = false; actions.lock(); refresh() }) { Text("Lock it") }
            },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Not yet") } },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { Icon(Icons.Rounded.Warning, null) },
            title = { Text("Remove all protection?") },
            text = { Text("This switches every layer off and lets you uninstall PureGuard.") },
            confirmButton = {
                Button(onClick = { confirmRemove = false; actions.removeProtection() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun Header(status: ProtectionStatus) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("PureGuard", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        val (label, icon) = when {
            !status.locked -> "Setup" to Icons.Rounded.LockOpen
            status.unlockedForMs > 0 -> "Unlocked" to Icons.Rounded.LockOpen
            else -> "Locked" to Icons.Rounded.Lock
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Hero(status: ProtectionStatus) {
    val ok = status.filterActive
    val accent by animateColorAsState(if (ok) TealBright else Warn, label = "accent")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Navy, NavyDeep)))
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(72.dp).background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ok) Icons.Rounded.Shield else Icons.Rounded.Warning,
                    null, tint = accent, modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    if (ok) "You're protected" else "Filter is off",
                    style = MaterialTheme.typography.headlineSmall, color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        status.privateDns && status.dnsHost == PrivateDns.HOST_STANDARD -> "Safe DNS · Standard"
                        status.privateDns -> "Safe DNS · Strict"
                        status.vpnRunning -> "Backup VPN filter only"
                        else -> "Turn on the Safe DNS filter below"
                    },
                    color = accent, style = MaterialTheme.typography.titleMedium,
                )
                if (status.blockedTotal > 0) {
                    Text(
                        "${status.blockedToday} blocked today · ${status.blockedTotal} in total",
                        color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseCard(status: ProtectionStatus, actions: HomeActions) {
    val paused = status.pausedForMs > 0
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Payments, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (paused) "Paused · ${status.pausedForMs / 1000 / 60}:${"%02d".format(status.pausedForMs / 1000 % 60)}"
                    else "Payment not going through?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    if (paused) "Screen Guard and the VPN filter are off. The Safe DNS filter stays on."
                    else "Switch the optional extras off for 5 minutes. ${status.pausesLeft} left today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (paused) {
                OutlinedButton(onClick = actions::resume) { Text("Resume") }
            } else {
                Button(onClick = actions::pause, enabled = status.pausesLeft > 0) { Text("Pause") }
            }
        }
    }
}

@Composable
private fun ComputerSetup(actions: HomeActions) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("One-time computer step (recommended)", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Lets PureGuard switch the Safe DNS filter on by itself, keep it on while locked, and " +
                    "bring Screen Guard back after a payment pause. With USB debugging on, run this once, " +
                    "then turn USB debugging off again:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    CMD_SECURE, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.copy(CMD_SECURE) }) { Icon(Icons.Rounded.ContentCopy, "Copy") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
    )
}

@Composable
private fun Layer(
    icon: ImageVector,
    title: String,
    description: String,
    active: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
    hint: String? = null,
    hintAction: Pair<String, () -> Unit>? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(
                            if (active) Good.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = if (active) Good else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (active) {
                    Icon(Icons.Rounded.CheckCircle, "Active", tint = Good, modifier = Modifier.size(28.dp))
                } else {
                    FilledTonalButton(
                        onClick = onAction, enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Text(actionLabel) }
                }
            }
            if (hint != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hint, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    if (hintAction != null) {
                        TextButton(onClick = hintAction.second) { Text(hintAction.first) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockCard(ready: Boolean, onLock: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Text("Lock it (optional)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (ready) "Locking means settings can only be changed with the password. Try PureGuard " +
                    "unlocked for a few days first, and check that your payment apps work."
                else "Turn on the Safe DNS filter first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onLock, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lock PureGuard")
            }
        }
    }
}

@Composable
private fun LockedCard(onUnlock: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = Teal)
                Spacer(Modifier.width(10.dp))
                Text("Protection is locked", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Settings can only be changed with the password (see PASSWORD.txt in the project).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onUnlock) { Text("Enter password") }
        }
    }
}

@Composable
private fun UnlockedCard(remainingMs: Long, onRelock: () -> Unit, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Text("Unlocked for ${remainingMs / 60_000 + 1} min", style = MaterialTheme.typography.titleMedium)
            Text(
                "You can change settings below. It locks itself again automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRelock, modifier = Modifier.weight(1f)) { Text("Lock now") }
                OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Remove protection") }
            }
        }
    }
}

@Composable
private fun SettingsCard(tick: Int, actions: HomeActions, onChange: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Toggle(
                "Strict Safe DNS",
                "On: also blocks proxies, VPN sites and mixed sites like Reddit. Off: adult sites only.",
                Prefs.dnsStrict,
            ) { actions.setDnsStrict(it); onChange() }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("Block adult searches", "Screen Guard: stops explicit searches typed in a browser", Prefs.typingGuard) {
                Prefs.typingGuard = it; onChange()
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("Scan screen text", "Screen Guard: close screens full of explicit words. Can misfire.", Prefs.screenScan) {
                Prefs.screenScan = it; onChange()
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("Block other VPN apps", "Screen Guard: close any app that contains a VPN", Prefs.blockOtherVpnApps) {
                Prefs.blockOtherVpnApps = it; onChange()
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("SafeSearch in VPN filter", "Backup VPN filter: force SafeSearch", Prefs.safeSearch) {
                Prefs.safeSearch = it; onChange()
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("Block bypass services in VPN filter", "Backup VPN filter: proxies, Tor, other DNS", Prefs.blockBypassTools) {
                Prefs.blockBypassTools = it; onChange()
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun DomainListCard(title: String, placeholder: String, tick: Int, onChange: () -> Unit, allowList: Boolean) {
    var input by remember { mutableStateOf("") }
    val domains = if (allowList) Prefs.customAllowed else Prefs.customBlocked
    fun save(set: Set<String>) {
        if (allowList) Prefs.customAllowed = set else Prefs.customBlocked = set
        onChange()
    }
    fun add() {
        val d = input.trim().lowercase().removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").substringBefore('/')
        if (d.contains('.')) save(domains + d)
        input = ""
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Used by Screen Guard and the backup VPN filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text(placeholder) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { add() }) { Icon(Icons.Rounded.Add, "Add") }
            }
            domains.sorted().forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { save(domains - d) }) { Icon(Icons.Rounded.Close, "Remove") }
                }
            }
        }
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Enter password") },
        text = {
            Column {
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it; message = null },
                    singleLine = true, label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This is version ${BuildConfig.VERSION_NAME}. The password is in PASSWORD.txt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && pw.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.Default) { PasswordVault.tryUnlock(pw) }
                        busy = false
                        when (r) {
                            PasswordVault.Result.Success -> onDismiss()
                            is PasswordVault.Result.Wrong -> {
                                pw = ""
                                message = if (r.lockedForMs > 0) "Wrong password. Try again in ${dur(r.lockedForMs)}."
                                else "Wrong password."
                            }
                            is PasswordVault.Result.LockedOut ->
                                message = "Too many attempts. Try again in ${dur(r.remainingMs)}."
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Unlock")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun dur(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60} min"
        else -> "${s / 3600} h ${(s % 3600) / 60} min"
    }
}
