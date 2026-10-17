#!/usr/bin/env bash
set -Eeuo pipefail

PACKAGE="com.example.floorplan"
ACTIVITY="com.example.MainActivity"
APK="ci-apk/app-debug.apk"
OUT="smoke-evidence"
mkdir -p "$OUT"

collect() {
  adb logcat -d -v threadtime > "$OUT/emulator-logcat.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$OUT/activity-state.txt" 2>&1 || true
  adb exec-out screencap -p > "$OUT/visible-screen.png" 2>/dev/null || true
}
trap collect EXIT

test -s "$APK" || { echo "No real APK downloaded" >&2; exit 1; }
adb devices -l | tee "$OUT/adb-devices.txt"
adb shell getprop ro.build.version.sdk | tee "$OUT/device-api.txt"
adb install "$APK" | tee "$OUT/install-result.txt"
adb shell pm path "$PACKAGE" | tee "$OUT/installed-package.txt"
grep -q "package:" "$OUT/installed-package.txt"

# Cold launch and keep the real application process alive for several seconds.
adb logcat -c
adb shell am start -W -n "$PACKAGE/$ACTIVITY" | tee "$OUT/cold-launch.txt"
sleep 7
if ! adb shell pidof "$PACKAGE" > "$OUT/running-pid.txt"; then
  echo "CRASH: app process not alive after launch" >&2
  exit 1
fi
cat "$OUT/running-pid.txt"

# Verify the launcher can recover after normal force-stop.
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 | tee "$OUT/launcher-result.txt"
sleep 4
adb shell pidof "$PACKAGE" | tee "$OUT/relaunch-pid.txt"

# Fail if Android reported a fatal exception for this app.
adb logcat -d -v brief > "$OUT/preliminary-logcat.txt"
if grep -A 12 -E "FATAL EXCEPTION:" "$OUT/preliminary-logcat.txt" |
   grep -q "Process: $PACKAGE"; then
  echo "FATAL EXCEPTION in $PACKAGE" >&2
  exit 1
fi

echo "PASS: installed, launched, process alive, relaunched, no app fatal exception detected"
