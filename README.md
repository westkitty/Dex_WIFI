# Dex_WIFI / WiFi Pulse

Native Android (Kotlin + Jetpack Compose) Wi-Fi scan, channel spectrum, optimization and apartment heatmap app.

## Build an installable APK

The **Android APK** GitHub Actions workflow builds a **debug-signed** `app-debug.apk`. From the successful run, download and unzip the `Dex_WIFI-debug-apk` artifact. Debug APKs are for direct sideload/testing, not Play Store publication.

CI uses Android SDK 36, JDK 17, AGP 8.13.2, Kotlin 2.3.21, and Gradle 8.14.3. Locally, install the matching SDK and Gradle and run:

```sh
gradle --no-daemon :app:assembleDebug
gradle --no-daemon :app:testDebugUnitTest
```

To install over authorized ADB:

```sh
adb devices -l
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify DEVICE_SERIAL identifies the intended tablet. A debug key is generated per build machine; differently signed APKs cannot update one another. Never uninstall an app with survey data before preserving that data.

For real scans, turn on Android Location and grant location permission. Simulation mode uses demo networks and must not be mistaken for a real scan. The app cannot reconfigure an external router's Wi-Fi channel solely by reading scans. Release signing retains AI Studio's KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD environment-variable contract; no keys are checked into Git.

## Floor Plan Studio (preview)

Tap **Heatmap** → **Draw / photograph my apartment** (or **Edit my apartment map** if one is saved). Use the tablet in landscape mode for more canvas space. Existing example layouts remain available as demonstrations only.

1. **Start a map.** Draw the outer perimeter by tapping corners and **Close outline**, or use **Rectangle start**. For an irregular apartment, follow the actual perimeter instead of forcing a rectangle.
2. **Trace structure.** Select **Pen** for a continuous finger/stylus wall sketch, or **Wall**, **Door**, or **Window** to tap endpoints. Turn off grid snap when matching a real photo. **Erase** removes nearby drawn geometry. **Undo** restores the previous editing action.
3. **Identify rooms.** Choose **Room**, tap two opposite corners of each rectangular label region, select its type and give it a name. Room labels are annotations; they are not a guaranteed architectural measurement.
4. **Use your own plan.** **Import picture** selects an existing floor-plan image; **Take photo** opens the device camera through Android's secure FileProvider. The app keeps a downsampled copy in its private storage. Tracing still requires manual verification. If you replace a picture, check your existing geometry/pins against the new one.
5. **Calibrate accurately.** With **Scale**, tap two points with a known tape-measured distance and enter meters or feet. For a freehand sketch, set **Measured apartment dimensions** (width and height in meters) independently; one scale line alone cannot correct an unknown or distorted image aspect ratio. A photograph is not automatically an accurate blueprint.
6. **Save plan.** The private versioned layout survives app restart. Existing Room survey readings remain in the database and are associated with the custom layout ID.
7. **Collect real signal.** Turn on Android Location, grant the app location permission, select **Hardware** (not Simulated), and physically stand at each spot. Tap the corresponding location to record the currently connected network's RSSI. Record readings throughout the apartment. Android scan throttling or missing permissions may temporarily prevent measurements.
8. **Interpret the result.** The custom heatmap requires calibrated dimensions and at least 3 authentic sample records. The map is an *interpolation*, not a floor-by-floor RF simulation or validation of hidden walls. Areas further than 3.5 m from samples and outside a closed perimeter remain blank. For a changed SSID/router/band, start a separate survey rather than compare unmatched measurements as if they were the same source.

**Privacy and accuracy:** The app does not transmit floor-plan photos itself; Android system backups may back up app data according to device settings. Real-Wi-Fi scan data is hardware/OS dependent. Traced walls and doors are visual annotations and are not yet used as physically calibrated RF attenuation obstacles. The editor offers no automatic room recognition, perspective correction, PDF import, or multi-floor project manager yet.

**Preserving existing installations:** Package ID is `com.example` (kept unchanged). Updating an APK requires the signing certificate to match the installed version. If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, do not uninstall the old version until its data and original signing identity are protected; GitHub Actions debug keys are not stable between separate build machines.

Reference mechanisms studied: NetSpot's [Android floor-plan/calibration workflow](https://www.netspotapp.com/help/netspot-android-manual/) and [grayed-out unmeasured regions](https://www.netspotapp.com/help/how-can-i-adjust-the-appearance-of-my-heatmaps/), plus [NetSpot's planning tools](https://www.netspotapp.com/netspot-wifi-analyzer-for-android.html). These illustrate interface patterns, not measured accuracy of this app.
