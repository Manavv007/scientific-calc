package com.pureguard.app.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pureguard.app.core.PasswordVault
import com.pureguard.app.core.Prefs
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
import java.text.NumberFormat

interface HomeActions {
    fun enableVpn()
    fun openAccessibility()
    fun openAppInfo()
    fun enableAdmin()
    fun openVpnSettings()
    fun requestBattery()
    fun requestNotifications()
    fun lock()
    fun relock()
    fun removeProtection()
    fun updateLists()
    fun copy(text: String)
}

private const val CMD_SECURE =
    "adb shell pm grant com.pureguard.app android.permission.WRITE_SECURE_SETTINGS"
private const val CMD_OWNER =
    "adb shell dpm set-device-owner com.pureguard.app/.admin.GuardAdminReceiver"

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
    var confirmLock by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val refresh = { tick++; status = Protection.status(context) }

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
            item { Stats(status) }

            item { SectionTitle("Protection layers") }
            item {
                Layer(
                    Icons.Rounded.Dns, "Content filter",
                    "Blocks ${fmt(status.domains.coerceAtLeast(100_000))}+ adult sites at the network level, " +
                        "forces SafeSearch on Google, Bing, DuckDuckGo & YouTube, and shuts down DNS bypasses.",
                    status.vpnRunning, "Turn on", actions::enableVpn,
                )
            }
            item {
                Layer(
                    Icons.Rounded.Visibility, "Screen Guard",
                    "Watches address bars, searches and on-screen text in every app, blocks bypass apps, " +
                        "and stops PureGuard from being switched off.",
                    status.accessibility, "Open settings", actions::openAccessibility,
                    hint = if (!status.accessibility)
                        "Toggle greyed out? Open App info → ⋮ → “Allow restricted settings”, then try again." else null,
                    hintAction = "App info" to actions::openAppInfo,
                )
            }
            item {
                Layer(
                    Icons.Rounded.AdminPanelSettings, "Uninstall protection",
                    "Android refuses to uninstall PureGuard while this is on.",
                    status.admin, "Activate", actions::enableAdmin,
                )
            }
            item {
                Layer(
                    Icons.Rounded.VpnLock, "Always-on filter",
                    "Starts the filter automatically at boot and keeps it running. In the VPN settings, enable " +
                        "“Always-on VPN” for PureGuard. Leave “Block connections without VPN” off.",
                    status.alwaysOn || status.deviceOwner, "VPN settings",
                    actions::openVpnSettings, enabled = Prefs.canEdit,
                )
            }
            item {
                Layer(
                    Icons.Rounded.BatteryChargingFull, "Never put to sleep",
                    "Stops the battery optimiser from killing protection in the background.",
                    status.batteryUnrestricted, "Allow", actions::requestBattery,
                )
            }
            if (!status.notifications) {
                item {
                    Layer(
                        Icons.Rounded.Notifications, "Tamper alerts",
                        "Lets PureGuard warn you if protection is ever interrupted.",
                        false, "Allow", actions::requestNotifications,
                    )
                }
            }

            item { Advanced(status, actions) }

            when {
                !status.locked -> item {
                    LockCard(status.coreReady) { confirmLock = true }
                }
                status.unlockedForMs > 0 -> item {
                    UnlockedCard(status.unlockedForMs, actions::relock) { confirmRemove = true }
                }
                else -> item { LockedCard { showPassword = true } }
            }

            if (Prefs.canEdit) {
                item { SectionTitle("Settings") }
                item { SettingsCard(tick, refresh) }
                item { DomainListCard("Always block", "example.com", tick, refresh, allowList = false) }
                item { DomainListCard("Always allow", "work-site.com", tick, refresh, allowList = true) }
            }

            item {
                OutlinedButton(onClick = actions::updateLists, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Update blocklists now")
                }
            }
            item {
                Text(
                    "Everything runs on this phone. PureGuard never sends what you browse anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }

    if (showPassword) PasswordDialog(onDismiss = { showPassword = false; refresh() })

    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            icon = { Icon(Icons.Rounded.Lock, null) },
            title = { Text("Lock PureGuard?") },
            text = {
                Text(
                    "After locking, PureGuard cannot be switched off, changed or uninstalled without " +
                        "the password — and you don't know the password. This is permanent by design.",
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
    val protectedFully = status.coreReady
    val accent by animateColorAsState(if (protectedFully) TealBright else Warn, label = "accent")
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
                    if (protectedFully) Icons.Rounded.Shield else Icons.Rounded.Warning,
                    null, tint = accent, modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    if (protectedFully) "You're protected" else "Finish setup",
                    style = MaterialTheme.typography.headlineSmall, color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${status.layersActive} of ${status.layersTotal} layers active",
                    color = accent, style = MaterialTheme.typography.titleMedium,
                )
                if (status.deviceOwner) {
                    Text("Maximum strength mode", color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Stats(status: ProtectionStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("Blocked today", fmt(status.blockedToday), Modifier.weight(1f))
        Stat("All time", fmt(status.blockedTotal), Modifier.weight(1f))
        Stat(
            "List updated",
            if (status.listUpdatedAt == 0L) "Built-in"
            else DateUtils.getRelativeTimeSpanString(status.listUpdatedAt).toString(),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    FilledTonalButton(onClick = onAction, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(actionLabel)
                    }
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
private fun Advanced(status: ProtectionStatus, actions: HomeActions) {
    var open by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Maximum strength (optional)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Uses a computer once to give PureGuard powers no ordinary app has.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        "Enable USB debugging (Settings → About → tap Build number 7× → Developer options), " +
                            "connect to a computer with Android platform-tools, and run:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    CommandRow(
                        "Self-healing", "Auto re-enables Screen Guard if switched off and blocks custom Private DNS.",
                        CMD_SECURE, status.secureSettings, actions,
                    )
                    Spacer(Modifier.height(10.dp))
                    CommandRow(
                        "Device owner", "Strongest. Blocks safe mode, VPN changes, extra users, adb and uninstall " +
                            "at the system level. Requires removing all accounts (Google etc.) first; add them back after.",
                        CMD_OWNER, status.deviceOwner, actions,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandRow(title: String, desc: String, cmd: String, done: Boolean, actions: HomeActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (done) Icon(Icons.Rounded.CheckCircle, "Active", tint = Good)
        }
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cmd, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { actions.copy(cmd) }) { Icon(Icons.Rounded.ContentCopy, "Copy") }
        }
    }
}

@Composable
private fun LockCard(ready: Boolean, onLock: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp)) {
            Text("Final step: lock it", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                if (ready) "Everything essential is on. Lock PureGuard so it can't be disabled in a weak moment."
                else "Turn on the content filter, Screen Guard and uninstall protection first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLock, enabled = ready, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Rounded.Lock, null)
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
                "Settings can only be changed with the password.",
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
            Text(
                "Unlocked for ${remainingMs / 60_000 + 1} min",
                style = MaterialTheme.typography.titleMedium,
            )
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
private fun SettingsCard(tick: Int, onChange: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Toggle("Enforce SafeSearch", "Google, Bing, DuckDuckGo, YouTube restricted mode", Prefs.safeSearch) {
                Prefs.safeSearch = it; onChange()
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("Scan screen text", "Close pages and apps full of explicit words", Prefs.screenScan) {
                Prefs.screenScan = it; onChange()
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("Block adult searches", "Stops explicit searches as you type them", Prefs.typingGuard) {
                Prefs.typingGuard = it; onChange()
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("Block bypass services", "Proxies, VPN sites, Tor, encrypted-DNS resolvers", Prefs.blockBypassTools) {
                Prefs.blockBypassTools = it; onChange()
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Toggle("Block other VPN apps", "Any app that could replace the filter", Prefs.blockOtherVpnApps) {
                Prefs.blockOtherVpnApps = it; onChange()
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
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun DomainListCard(title: String, placeholder: String, tick: Int, onChange: () -> Unit, allowList: Boolean) {
    var input by remember { mutableStateOf("") }
    val items = if (allowList) Prefs.customAllowed else Prefs.customBlocked
    fun save(set: Set<String>) {
        if (allowList) Prefs.customAllowed = set else Prefs.customBlocked = set
        onChange()
    }
    fun add() {
        val d = input.trim().lowercase().removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").substringBefore('/')
        if (d.contains('.')) save(items + d)
        input = ""
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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
            items.sorted().forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { save(items - d) }) { Icon(Icons.Rounded.Close, "Remove") }
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

private fun fmt(n: Number): String = NumberFormat.getIntegerInstance().format(n)

private fun dur(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60} min"
        else -> "${s / 3600} h ${(s % 3600) / 60} min"
    }
}
