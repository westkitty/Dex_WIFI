# Dex_WIFI Project Bible

Append-only project ledger for the Android Wi-Fi analyzer. Do not rewrite earlier entries.

## 2026-10-16 / 2026-10-17 — Floor Plan Studio and honest measurement uplift

### Identity and intent
- Repository: `westkitty/Dex_WIFI`; product: WiFi Pulse, Kotlin / Jetpack Compose Android.
- Starting main commit: `9be2e1e404460440e26a0d163cb1130fc2ca5cd5`.
- Work branch: `feat/floorplan-studio-20261016`, PR #2: https://github.com/westkitty/Dex_WIFI/pull/2
- Scope requested: user-authored and photographed apartment geometry, calibrated real space, more usable survey mapping; twenty or more further enhancements; committed and pushed changes; attempted tablet deployment via authorized ADB.
- Protected: original network/channels/optimizer screens, existing Room survey table, original applicationId and minimum Android SDK, private photos, measured-vs-demo distinction.
- Scope frozen to three cohesive groups: floor-plan creation; calibration and storage; honest survey/heatmap reliability.

### Baseline findings
- Only three sample apartment templates, default simulation when permissions unavailable, misleading fallback -65dBm and fake samples at startup, unbounded interpolation across unsurveyed space, implicit room label coupling.
- Existing Gradle CI on main had passed; SDK 36, Gradle 8.14.3, Kotlin 2.3.21, Java 17.
- MacBook Desktop Commander device is offline (730 h), DEX//REACH node listing fails internally; no authorized reachable ADB endpoint confirmed. Hardware deployment remains **BLOCKED**, not a successful installation.

### Implemented / pushed source work
- Editor model: normalized outline vertices, open/closed perimeter, walls, doors, windows, rectangular labeled rooms, calibrated dimensions, physical router marker, JSON schema v1.
- Editor interaction: sketch pen, two-tap segments, room labeling/types, undo (35 steps), erase, grid snapping and horizontal/vertical aid, blank rectangle, camera `TakePicture`, image picker, reference transparency, private JPEG import, measured width/height and two-point distance calibration.
- Persistence: `FloorPlanStore` JSON SharedPreferences and bounded private JPEG files; no Room schema migration, existing measurements preserved.
- Integration: personal layout on Heatmap tab with editor and template selector; same normalized image coordinates for display and pins, relocate router, safe delete confirmation, room coverage keyed to pin position on personal layout.
- Honesty/reliability: no automatic fake data seeding; hardware-only snapshot pins; clear stale fake networks on mode transition; no fake connected-Wi-Fi defaults; real connection reading can remain when scan returns empty; custom heatmap requires scale + >=3 samples, masks traced exterior, clips cells >3.5m from sampled points, separates newest SSID from another.
- Device-system caveats: camera permission delegated to Android activity; photo dimensions are not a verified blueprint and are not automatically perspective corrected; user must validate positions and real-world scale; no wall-crossing RF attenuation estimation from traced walls yet; Android system backups are not under this app's direct control.

### Attribution and test evidence
- Feature implementation commit: `de2d92aaf017cbfa2b6aa72e644a0e6763a9e5a0`.
- Compile/data safety correction: `52cc6f890edc30969d87a7850dc014bb2706addf`. GitHub Actions `38994934896` **PASS** build+tests.
- Regression/persistence tests commit: `ae51574dd377f7a6775f9a2f105dba3f6b4e16a0`. GitHub Actions run `38995098605` **PASS** build+unit tests; APK ZIP sha256 `cf47794e8809b8509ccb1943bc9e49cbd984979fa55aed104dfee02a9511c90f`, APK binary sha256 `8693ce34e9dc65663a5f8f2112b6022e7201cf9548009bef46f96db29ed86e9f`.
- Source code addition `53b5f4c4a05f033bfeec6db031bf0b3e91466e5c` adds independent measured width/height fields (still pending CI proof when this entry was authored). Do not ascribe earlier APK hashes to this newer source revision.
- New tests: geometry calibration, polygon/heatmap boundaries, Room-independent floor-plan persistence via Robolectric SDK 35; they passed on the implementation commit. Real tablet camera, drag pen/pinch workflow, Android Wi-Fi permissions and installation remain unverified.
- No migration or intentional deletion performed; append-only history preserved.

### Prior-art transfer
- NetSpot Android: explicit floor-plan photo/blank map survey and physical calibration, visualizing unmeasured regions; see https://www.netspotapp.com/help/netspot-android-manual/ and https://www.netspotapp.com/help/how-can-i-adjust-the-appearance-of-my-heatmaps/
- NetSpot planning: walls/windows/doors and material property capture for predictive estimates; see https://www.netspotapp.com/netspot-wifi-analyzer-for-android.html
- Survey convention: professional stop-and-go location capture; avoid treating one Wi-Fi radio's stale RSSI as a precise per-room scientific scan.
- Adapting mechanisms is not copying others' source or claiming professional-validated prediction.

### Further opportunities (not implemented unless explicitly stated)
Prioritize for subsequent bounded, testable iterations:
1. Two-finger zoom and pan while maintaining exact inverse touch transform.
2. Vertex selection, dragging, corner merging and vector path smoothing.
3. Photo de-skew/perspective-correction with four user-set corners.
4. Multipage floor-plan PDF import with explicit page and cropping controls.
5. Opt-in on-device AI-assisted wall detection with editable confirmation.
6. Stylus pressure awareness and palm rejection.
7. Door swing arcs, window width and architectural symbols.
8. Wall material/thickness types (drywall, brick, reinforced concrete, glass).
9. Wall-crossing-aware RF inference explicitly labeled as predictive, never measured.
10. Furniture, shelves, mirrors, appliances and RF obstacle markers.
11. Separate two-axis scale references and calibration-error residual display.
12. Scale bar overlay, ruler and selectable feet/meters display.
13. Multiple floors and distinct buildings without overwriting the active plan.
14. Blueprint revision history, duplicate/restore and non-destructive comparisons.
15. Guided room-to-room walk-survey checklist with completion markers.
16. Suggested unsampled zones based on spatial coverage, not fake predictions.
17. Median RSSI over timed window with sample count and variance.
18. Stale Wi-Fi scan age/OS throttling warnings, no fabricated update time.
19. BSSID-specific measurement filters to distinguish mesh APs sharing an SSID.
20. Separate 2.4, 5 and 6 GHz coverage histories.
21. Confidence/uncertainty map with measured-vs-interpolated legend.
22. Active throughput, latency, loss, jitter tests on authorized networks.
23. Controlled before/after survey comparisons with same AP and frequency.
24. Router/mesh AP inventory and confirmed physical placements.
25. Local-only CSV/PNG/JSON exports and portable encrypted backup/restore.
26. Accessible high-contrast/colorblind legends, reduced motion, large touch targets.
27. Room-based thresholds and plain-language remediation.
28. Battery/performance-friendly survey scan scheduling and memory profiling.
29. Optional AR-assisted surveying where device APIs actually support it.
30. Real-device integration tests for camera, touch, permissions and upgrade data retention.

### Risk / regression notes
- **Physical accuracy:** normalizing picture pixels does not make them survey-grade. A photo may be distorted, occluded, or rotated; verify perimeter and scale.
- **Survey accuracy:** Android may provide cached scan results. Current pins are snapshots, not timed statistical averages. Treat interpolation as provisional and blank spaces as unknown.
- **Update signatures:** changing debug signing keys will prevent in-place `adb install -r`; never silently uninstall user data.
- **Room model:** room labels use rectangles, drawn walls do not yet bound rooms as full polygons.
- **Persistence:** local JSON/pictures are private app files but Android system backup may carry data; avoid claiming complete offline/cloud absence without platform backup audit.
- **CI versus device:** successful assembleDebug and Robolectric do not prove actual tablet pen, camera, Wi-Fi scan accuracy or installation.
- **ADB target:** only proceed after live proof of correct tablet serial and authorized connection.

### Git and execution state
- User expressly authorized stage, commit and push; connector-created immutable GitHub commits and branch push confirmed.
- Main branch intentionally unchanged pending device-level acceptance; PR #2 is the review/merge gate.
- Binary from verified `ae515...` build exposed before any Google Drive archival as requested.
- Documentation follow-up commit: to be reported separately.
- ADB install: blocked by device offline, no claim of deployment; ask user to bring Desktop Commander Remote or DEX//REACH online, then re-identify node, `adb devices -l` and verify package version/signature before installation.
