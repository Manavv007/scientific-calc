# PureGuard

A strict, on-device adult content blocker for Android, written in Kotlin with Jetpack Compose.
It is locked with a random password. Only a one-way hash is in the code; the password itself
is saved in `PASSWORD.txt`.

## Install

1. On your Android phone, open this repository's **Releases** page and download the latest
   `PureGuard-x.y.z.apk`.
2. Open it and allow "Install unknown apps" for your browser when asked.
3. Open PureGuard and follow the setup checklist (below). Then tap **Lock PureGuard**.

Updates install over the old version. You never need to uninstall.

## How it blocks: every layer

| Layer | What it does |
|---|---|
| **DNS filter (local VPN)** | Every domain lookup on the phone is checked against ~110,000 adult domains (StevenBlack porn list plus hagezi NSFW, refreshed every 3 days) and a keyword heuristic. Blocked sites get NXDOMAIN. Nothing leaves the phone except normal DNS. |
| **Family-safe upstream DNS** | Allowed lookups are resolved by Cloudflare for Families (1.1.1.3) and CleanBrowsing Adult, which block adult content again on their side. |
| **Forced SafeSearch** | Google, Bing, DuckDuckGo, Yandex, Pixabay and YouTube (Restricted Mode) are rewritten at DNS level to their enforced safe endpoints. Explicit image results disappear. |
| **Anti-bypass DNS** | Public resolvers (8.8.8.8, 1.1.1.1, Quad9, AdGuard, NextDNS…) are routed into the filter: plain DNS to them is filtered, and DNS-over-HTTPS/TLS to them is reset. DoH hostnames are blocked, and Firefox's DoH canary tells Firefox to disable DoH. |
| **Bypass services blocked** | Web proxies, VPN provider sites, Tor, iCloud Private Relay and the Google Translate proxy. |
| **Screen Guard (accessibility)** | Reads the address bar of 25+ browsers, catches adult searches as you type them in any app, and closes screens full of explicit words. |
| **Bypass apps blocked** | Tor Browser, Orbot, Psiphon, Opera (built-in VPN), WARP, and **every other installed app that contains a VPN**. |
| **Self-protection** | While locked, it kicks you out of any screen that could disable it: App info (force stop, uninstall, clear data), its accessibility toggle, device-admin deactivation, VPN settings, Private DNS, adding users or guests, and the "reboot to safe mode" prompt. |
| **Uninstall protection** | Device administrator, so Android refuses to uninstall it. |
| **Self-healing** | A watchdog every 15 minutes, on boot, and every 20 seconds from Screen Guard restarts the filter and raises an alert if anything is off. |
| **Password** | 20 random characters, stored only as PBKDF2-SHA256 (210k iterations). Wrong guesses lock the prompt with an exponential back-off of up to 24 hours. |

### Optional: maximum strength (needs a computer once)

From the app's **Maximum strength** section:

* `adb shell pm grant com.pureguard.app android.permission.WRITE_SECURE_SETTINGS`
  lets the app re-enable Screen Guard by itself and override a custom Private DNS.
* `adb shell dpm set-device-owner com.pureguard.app/.admin.GuardAdminReceiver`
  makes the app **device owner**. Android itself then blocks safe mode, VPN changes, extra users,
  USB debugging and uninstalling, and forces the filter to be always-on. You must remove all
  accounts (Google, Samsung…) from the phone before running it, then add them back.

## Setup checklist

1. **Content filter**: tap *Turn on* and accept the VPN prompt.
2. **Screen Guard**: *Open settings* → PureGuard → on. If the switch is greyed out (Android 13+
   for sideloaded apps), tap *App info* → ⋮ → **Allow restricted settings**, then try again.
3. **Uninstall protection**: *Activate*.
4. **Always-on filter**: *VPN settings* → ⚙ next to PureGuard → **Always-on VPN** on.
   Leave "Block connections without VPN" **off**. The filter only captures DNS, so that option
   would cut your internet.
5. **Never put to sleep**: *Allow*.
6. Tap **Lock PureGuard**.

## Honest limits

No app can be 100% unbreakable on a phone you own, so here is how it could still be removed:

* A **factory reset** always wipes it. This is left available on purpose so the phone can never
  become unusable.
* Someone with the source code could build a different version and install it as an update.
* The filter can't see inside images. Adult images on otherwise normal sites can still appear;
  SafeSearch and the screen text scan reduce this a lot.

## Build it yourself

GitHub Actions builds every push (`.github/workflows/build.yml`). Locally:

```
./gradlew assembleRelease
```

The output is `app/build/outputs/apk/release/app-release.apk`.
