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
