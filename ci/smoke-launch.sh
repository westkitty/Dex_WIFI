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


# Do not mistake Android's splash window for a usable Compose screen.
# Require visible app semantics AND the expected foreground activity.
wait_for_main_ui() {
  local phase="$1"
  local dump="$OUT/${phase}-ui.xml"
  local attempt
  for attempt in $(seq 1 24); do
    if ! adb shell pidof "$PACKAGE" > "$OUT/${phase}-pid.txt"; then
      echo "CRASH: $PACKAGE stopped while waiting for the $phase interface" >&2
      return 1
    fi
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    adb exec-out cat /sdcard/window.xml > "$dump" 2>/dev/null || true
    if grep -Eq 'WiFi Pulse|Apartment Signal|content-desc="Networks"|text="Networks"' "$dump" &&
       adb shell dumpsys activity activities | grep -Eq "topResumedActivity=.*$PACKAGE"; then
      adb exec-out screencap -p > "$OUT/${phase}-ui.png" 2>/dev/null || true
      echo "UI_READY: $phase; real app labels found, foreground confirmed"
      return 0
    fi
    sleep 1
  done
  echo "FAIL: $phase never rendered the actual Wi-Fi interface" >&2
  return 1
}

test -s "$APK" || { echo "No real APK downloaded" >&2; exit 1; }
adb devices -l | tee "$OUT/adb-devices.txt"
adb shell getprop ro.build.version.sdk | tee "$OUT/device-api.txt"
adb install "$APK" | tee "$OUT/install-result.txt"
adb shell pm path "$PACKAGE" | tee "$OUT/installed-package.txt"
grep -q "package:" "$OUT/installed-package.txt"

# Cold launch and keep the real application process alive for several seconds.
adb logcat -c
adb shell am start -W -n "$PACKAGE/$ACTIVITY" | tee "$OUT/cold-launch.txt"
wait_for_main_ui "cold-launch"
cat "$OUT/cold-launch-pid.txt"

# Verify the launcher can recover after normal force-stop.
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 | tee "$OUT/launcher-result.txt"
wait_for_main_ui "relaunch"

# Fail if Android reported a fatal exception for this app.
adb logcat -d -v brief > "$OUT/preliminary-logcat.txt"
if grep -A 12 -E "FATAL EXCEPTION:" "$OUT/preliminary-logcat.txt" |
   grep -q "Process: $PACKAGE"; then
  echo "FATAL EXCEPTION in $PACKAGE" >&2
  exit 1
fi

echo "PASS: installed, actual Wi-Fi UI rendered, relaunched to Wi-Fi UI, no app fatal exceptions"
