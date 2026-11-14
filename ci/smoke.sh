#!/usr/bin/env bash
# Installs the exact release APK on the emulator, opens it, and fails the build
# if it crashes or closes. This is the APK that gets published.
set -euo pipefail
APK="$1"
PKG=com.pureguard.app

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/.ui.MainActivity"
sleep 20

if ! adb shell pidof "$PKG" > /dev/null; then
  echo "::error::PureGuard is not running 20 seconds after launch"
  adb logcat -d | grep -E "AndroidRuntime|$PKG" | tail -80 || true
  exit 1
fi
if adb logcat -d | grep -A 25 "FATAL EXCEPTION" | grep -q "$PKG"; then
  echo "::error::PureGuard crashed"
  adb logcat -d | grep -A 40 "FATAL EXCEPTION" | tail -120
  exit 1
fi
FOCUS=$(adb shell dumpsys activity activities | grep -E "ResumedActivity|topResumedActivity" | head -3 || true)
echo "$FOCUS"
echo "$FOCUS" | grep -q "$PKG" || { echo "::error::PureGuard is not the app on screen"; exit 1; }
echo "Smoke test passed: the release APK installs, opens and stays open."
