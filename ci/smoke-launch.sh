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
    # Android's launcher dispatch is asynchronous; a not-yet-created PID
    # is not proof of a crash. Retry until the real UI is visible or timeout.
    if ! adb shell pidof "$PACKAGE" > "$OUT/${phase}-pid.txt"; then
      sleep 1
      continue
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


# Navigate by accessible Compose label, not hard-coded screen coordinates.
tap_accessible_label() {
  local target="$1"
  local file="$2"
  local coordinates
  coordinates="$(python3 - "$file" "$target" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
target = sys.argv[2]
nodes = list(root.iter("node"))
matches = [n for n in nodes if n.get("text") == target or n.get("content-desc") == target]
if not matches:
    matches = [n for n in nodes if target in (n.get("text") or "") or target in (n.get("content-desc") or "")]
if not matches:
    raise SystemExit("No accessible text/description matches: " + target)
bounds = matches[0].get("bounds") or ""
m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
if not m:
    raise SystemExit("No parseable bounds for " + target)
x1, y1, x2, y2 = (int(n) for n in m.groups())
print((x1+x2)//2, (y1+y2)//2)
PY
  )"
  echo "UI_TAP: $target at $coordinates"
  adb shell input tap $coordinates
}

wait_for_screen_text() {
  local phase="$1"
  local phrase="$2"
  local dump="$OUT/${phase}-ui.xml"
  local attempt
  for attempt in $(seq 1 20); do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    adb exec-out cat /sdcard/window.xml > "$dump" 2>/dev/null || true
    if grep -Fq "$phrase" "$dump" &&
       adb shell dumpsys activity activities | grep -Eq "topResumedActivity=.*$PACKAGE"; then
      adb exec-out screencap -p > "$OUT/${phase}-ui.png" 2>/dev/null || true
      echo "UI_READY: $phase; expected text: $phrase"
      return 0
    fi
    sleep 1
  done
  echo "FAIL: $phase did not expose $phrase" >&2
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

# Exercise the actual requested map workflow, not just the Networks landing page.
tap_accessible_label "Heatmap" "$OUT/cold-launch-ui.xml"
wait_for_screen_text "heatmap-screen" "APARTMENT BLUEPRINT"
tap_accessible_label "Draw / photograph my apartment" "$OUT/heatmap-screen-ui.xml"
wait_for_screen_text "floor-plan-studio" "FLOOR PLAN STUDIO"
tap_accessible_label "Cancel" "$OUT/floor-plan-studio-ui.xml"
wait_for_screen_text "return-to-heatmap" "APARTMENT BLUEPRINT"

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

echo "PASS: installed, Wi-Fi UI rendered, Heatmap and Floor Plan Studio opened, relaunched to Wi-Fi UI, no fatal exception"
