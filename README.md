# PureGuard

An on-device adult content blocker for Android, written in Kotlin with Jetpack Compose.
Version 2 is built so that it does **not** interfere with other apps, banking and UPI apps included.

## How it works

**Core protection (works alongside every app)**

| Layer | What it does |
|---|---|
| **Safe DNS filter** | Points Android's own *Private DNS* setting at CleanBrowsing's family filter. Every app and browser on the phone gets adult sites blocked and SafeSearch forced on Google, Bing and YouTube. It is a system setting, not a VPN and not an accessibility service, so payment apps have nothing to object to. |
| **Uninstall protection** | Device administrator, so Android refuses to uninstall PureGuard while it is on. |

**Optional extras (off by default)**

Some banking and payment apps refuse to run while *any* accessibility service or VPN is active.
These extras are therefore optional, never touch payment apps, and can be paused.

| Layer | What it does |
|---|---|
| **Screen Guard** (accessibility) | Checks browser address bars and searches typed in browsers, blocks Tor and proxy apps, and guards PureGuard's settings while locked. Banking, UPI and wallet apps are never inspected. |
| **Backup VPN filter** | A second, local DNS filter with ~110,000 adult domains. Payment apps are routed around it. |
| **Pause for payments** | One tap, no password: switches Screen Guard and the VPN filter off completely for 5 minutes (up to 8 times a day). The Safe DNS filter stays on. |

## Install

1. On your Android phone, open this repository's **Releases** page and download the latest
   `PureGuard-2.0.x.apk`.
2. Open it and allow "Install unknown apps" when asked. If Play Protect warns, choose
   **More details → Install anyway**.
3. Open PureGuard and tap **Set up** on *Safe DNS filter*, then follow the three steps shown.
4. Tap **Activate** on *Uninstall protection*.
5. Use the phone normally for a few days, payments included. Only then consider **Lock PureGuard**.

### Recommended one-time computer step

With USB debugging on, run this once, then turn USB debugging off again (some banking apps
dislike USB debugging being on):

```
adb shell pm grant com.pureguard.app android.permission.WRITE_SECURE_SETTINGS
```

It lets PureGuard switch the Safe DNS filter on by itself, put it back if it is changed while
locked, and bring Screen Guard back automatically after a payment pause.

## Password

The unlock password is in [`PASSWORD.txt`](PASSWORD.txt). The app shows its version number at the
bottom of the home screen and in the password box. A correct password unlocks settings for
10 minutes; **Remove protection** then switches everything off and lets you uninstall.

## If something ever goes wrong

These rescue routes are never blocked by PureGuard:

* **Safe mode**: hold the power button, long-press *Power off*, choose safe mode. PureGuard is
  inactive there, so you can deactivate it under *Device admin apps* and uninstall it.
* **USB debugging**: `adb install -r` a newer build over it, or `adb uninstall com.pureguard.app`
  after deactivating the device admin.
* **Private DNS**: if a network blocks the filter and you have no internet, set
  *Settings → Private DNS* to *Automatic* (needs the password when Screen Guard is on and locked).

## Checks before every release

Each push runs on GitHub Actions and a release is only published if all of this passes:

1. Logic tests: password file matches the app, payment-app detection, classifier, DNS and packet code.
2. The app is installed on an Android emulator and tested there: the home and block screens open,
   the bundled blocklist loads, the Safe DNS filter is really switched on and off, and the payment
   pause works.
3. The exact release APK is installed and opened on the emulator and must stay open.

An emulator cannot run real banking apps, so compatibility with a specific payment app can only be
confirmed on your own phone. That is why locking is optional and comes last.

## Honest limits

* The filter works on website names. Adult images on otherwise normal sites can still appear;
  forced SafeSearch reduces this a lot.
* The Strict filter also blocks mixed-content sites such as Reddit. Switch to Standard in Settings
  if that is too much.
* Anyone with the password, safe mode or a computer can remove it. That is deliberate.

## Build it yourself

```
./gradlew testReleaseUnitTest assembleRelease
```
