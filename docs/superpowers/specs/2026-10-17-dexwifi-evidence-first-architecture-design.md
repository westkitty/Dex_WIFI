# Dex_WIFI — Evidence-First Architecture Design

**Date:** October 17, 2026  
**Version:** 1.0 — design for human review  
**Status:** PROPOSED; NOT YET APPROVED FOR IMPLEMENTATION  
**Repository:** https://github.com/westkitty/Dex_WIFI  
**Documentation branch:** `docs/evidence-first-dexwifi-20261017` based on feature commit f76682720dba7e42721791793e6ef1b9c51c3ead; ongoing implementation PR https://github.com/westkitty/Dex_WIFI/pull/2  
**Source baseline:** `f76682720dba7e42721791793e6ef1b9c51c3ead`  
**CI evidence:** predecessor ea26bd6901d096cadf7d27fc052e4dcc66ce4226 passed GitHub tests and API 35/36 emulator navigation at https://github.com/westkitty/Dex_WIFI/actions/runs/39003366809. The newer f76682720dba7e42721791793e6ef1b9c51c3ead source reports a local Gradle build/test pass in `Dex_WIFI_bible.md`, but its GitHub Actions run https://github.com/westkitty/Dex_WIFI/actions/runs/39005555004 was pending at document preparation. Do not transfer predecessor CI results to the new SHA.  
**Authority:** User approved the evidence-first architecture and authorized this design specification. Approval does *not* authorize application implementation, package renaming, data deletion, merging, installing, release secrets or provisioning a signing key.

## 1. Product outcome and hierarchy

Dex_WIFI is a **local-first Android apartment Wi-Fi diagnostic workstation**. It must do three things reliably, in strict priority order:

1. **Preserve ownership and updates:** repeated APK builds, updates and migrations do not discard prior drawings, photos or survey data. If an installation cannot update safely, show the cause and preserve the prior app.
2. **Describe a real apartment:** sketches, photos, wall dimensions, rooms, router positions and measurement pins all share one physically calibrated coordinate system.
3. **Tell the truth about the radio:** every reading comes from a traceable hardware event; unknown, stale, simulated, imported and estimated values cannot masquerade as fresh measurements.

Core success: a user can photograph or sketch an irregular apartment, edit and label the floor plan on a Samsung tablet, calibrate to tape measurements, collect actual network observations while moving between rooms, understand why a heatmap shows a result, and export a recoverable local project.

Accuracy is bounded: **an image is not a survey; RSSI is not throughput; a heatmap is not a direct measurement of each pixel; Android radio APIs are not calibrated professional RF equipment**.

### 1.1 Protected capabilities and non-goals

Preserve the existing `Networks`, `Channels`, `Optimizer`, `Heatmap` and `Audit` screens, current user-mediated external router-admin workflow, local-first storage, and supported Android devices. Router passwords stay in the router's own UI. No mandatory cloud service, accounts, paid RF instrument, third-party telemetry, Docker, or automatic router configuration. No unauthorized file deletion to “fix” a full tablet. No promise of entire-apartment geometry from a single image without real measurements.

The 32 proposed features are an **inventory, not one atomic implementation**. Implementation must be decomposed into safe, independently testable slices.

## 2. Current-state evidence and source anchors

The following state is grounded in the current GitHub feature branch—not inferred from a previous APK:

| File | Current baseline | Architecture implication |
|---|---|---|
| `app/build.gradle.kts` and `app/src/debug/AndroidManifest.xml` | Base ID `com.example` and separate debug preview ID `com.example.floorplan` | These are distinct app sandboxes; ephemeral debug certificates cannot reliably update one another |
| `app/src/main/java/com/example/data/AppDatabase.kt` | Room v1 database `wifi_survey.db` using `fallbackToDestructiveMigration()` | **Blocker:** remove destructive fallback and add explicit migrations before altering schema |
| `data/SurveyEntity.kt`, `data/SurveyDao.kt` | layoutId, normalized XY, roomName, SSID/RSSI/frequency/channel/time/notes | No survey session, BSSID, sample freshness, device identity or floor revision |
| `data/FloorPlanStore.kt` | One private JSON version-1 plan and JPEG references | Need multiple named projects, revision history, recovery and portable export |
| `model/FloorPlanDocument.kt`, `ApartmentModel.kt` | Perimeter, wall/door/window segments, rectangular room labels, normalized points | Need metric coordinates, shared-wall graph, irregular rooms |
| `ui/screens/FloorPlanStudio.kt` | Draw/trace, photo import/camera, room labels, user-entered dimensions, undo | Need precision geometry, zoom/pan, revisions, stable constraints |
| `ui/components/CustomFloorPlanCanvas.kt` | Pin and heatmap rendering | Latest SSID is not sufficient to identify an unambiguous measurement cohort |
| `service/WifiScannerService.kt`, `service/ScanFreshness.kt` | Lifecycle-aware scanner, demo isolation, monotonic scan timestamp correction | Need sample provenance, explicit lock and release-grade capture validation |
| `service/HeatmapInterpolator.kt` | IDW estimate, perimeter mask, minimum samples and 3.5m default radius | Must output cell-level contributors and unknown states |
| `RadioOperationsScreen.kt`, `RouterAdminPolicy.kt` | Radio diagnostics and safe external-browser admin handoff | Preserve user-owned router changes, no credential capture |
| `service/SurveyCoachEngine.kt`, `ui/screens/SurveyCoachScreen.kt` | New calibrated sampling proximity, SSID+band cohorts, room, stale and duplicate checks | No per-observation BSSID, full session identity or verified coverage |
| `service/ConnectionDoctorEngine.kt`, `ui/screens/ConnectionDoctorScreen.kt` | New OS link status, optional gateway/DNS probes and private before/after baseline | Keep opt-in diagnostics distinct from measured throughput and causal proof |
| `ci/smoke-launch.sh`, `.github/workflows/android.yml` | API 35/36 compile, test, install, screen navigation and relaunch | Extend to persistence, migration and actual tablet proof |
| `Dex_WIFI_bible.md` | Append-only project history, known failures and pending device evidence | Preserve source-of-truth and evidence states |

The project bible documents a previous tablet with approximately **508 MiB** free and ADB storage-related installation trouble. This must be **rechecked live**; it is not a statement of current free space. GitHub PR #2 is open and is not a released build. The predecessor green emulator CI does not verify current commit f76682720dba7e42721791793e6ef1b9c51c3ead, Wi-Fi precision, camera or S Pen behavior on the actual tablet. Survey Coach and Connection Doctor are source-present, with current CI and physical evidence kept distinct.

### 2.1 Comparative precedents

NetSpot Android demonstrates imported/photo/blank survey plans, physical-distance calibration, measured scan stations and coverage distinctions (https://www.netspotapp.com/help/netspot-android-manual/). magicplan Android demonstrates irregular manual floor plans and editable, locked physical wall measurements (https://help.magicplan.app/creating-floor-plans-in-magicplan-on-your-android-device and https://help.magicplan.app/how-to-lock-and-unlock-dimensions). Copy workflow mechanisms, not source code or claims of the competitors' accuracy.

## 3. Required user journeys and terminal outcomes

**J1 — Safe install, update and return.** The user identifies original versus preview app, verifies installed package/signer, has a recoverable export and imports it into a separate release channel when update signatures do not match. A corrupted project, app termination or insufficient storage never silently deletes source records. **Pass:** signer/ID/version, hashes and record counts are verified on actual tablet; old app remains until restore is proven.

**J2 — Draw an accurate irregular apartment.** User starts blank/photo/blueprint, traces an irregular outline, joins walls and openings, creates named polygons, corrects lengths, locks measured dimensions and sees calibration errors. Editing is comfortable with finger or S Pen, works after zoom/pan, and survives process death. **Pass:** geometry is topologically valid, transforms are reversible, referenced dimensions persist and old survey points do not move silently.

**J3 — Survey a selected real AP.** Readiness assistant checks Wi-Fi, Location permission/services, hardware/demo, scan freshness and current AP. User locks a session to physical plan revision and network identity, records repeated authentic observations in a room, and gets a confidence-aware aggregate. **Pass:** a stale/demo/redacted/roamed/disconnected value cannot enter a verified session without explicitly recording its source and handling the state.

**J4 — Explain and export evidence.** User sees measured pins, interpolated regions and unknown areas separately, taps one cell to see contributing real stations, evaluates before/after only across compatible cohorts, and exports a privacy-reviewed archive with hashes. **Pass:** no inferred value appears as a measured radio observation, and the exported project restores without overwriting existing data.

## 4. Immutable rules

- **I01 DATA:** No automatic deletion, Room destructive migration, hidden DB reset or app uninstall.
- **I02 SPACE:** The stored plan and measurements are in physical meters, not camera pixels or device viewport coordinates.
- **I03 SOURCE:** DEMO, IMPORTED_UNVERIFIED and INTERPOLATED never become HARDWARE_OBSERVED by data transformation.
- **I04 SESSION:** Distinct BSSID, band, floor revision or device context are not silently mixed.
- **I05 UNKNOWN:** Missing permissions, scan throttling, inaccessible source or stale scans are not weak RSSI.
- **I06 REVISIONS:** Updating geometry or replacing a photo does not rewrite historical observed positions.
- **I07 RESTORE:** Evidence of recoverability precedes migration, signature/key change or uninstallation.
- **I08 PROOF:** APK assembly and process survival are weaker evidence than rendered UI, persisted user journey and physical tablet measurements.
- **I09 ROUTER:** Existing router admin remains external and manually authenticated, not a hidden device mutation API.
- **I10 LIFECYCLE:** Scanning follows OS rules and foreground lifetime; UI tick speed is not hardware scan frequency.

## 5. Component design and dependencies

```text
Compose: Project Library / Apartment Studio / Guided Survey / Audit / Export
      |               |                |           |      |
      v               v                v           v      v
Project Coordinator   Geometry       Capture    Evidence  Export
      |               Engine          Session     Explorer  Gate
      +----------------|----------------|--------------+
                       |                |
                 Versioned Room     Observation Validator
                 + private assets        ^
                       |                 |
               Migration / Backup    Radio Adapter
                       |                 |
                 Android SAF          Android Wi-Fi APIs

World coordinates + immutable snapshots -> inference -> measured/estimated/unknown view
```

| Module | Must own | Must not own |
|---|---|---|
| `project` | project/floor selection, active published revision and draft lifecycle | Android Wi-Fi polling |
| `geometry` | meter coordinates, graph, polygon validity, constraints, transforms and calibration | Context, Room, networking |
| `reference` | image ingest, EXIF/crop/rotation, original/derivative hashes, image-to-world transform | fabricated physical dimensions |
| `radio` | permission, scan/connected events, OS timestamps, demo isolation | survey DB writes |
| `capture` | session state, cohort validation and append-only observations | renderer and router-admin API |
| `inference` | eligible sample filtering, distance weighting, mask, provenance explanation and derived cache | modifying original observations |
| `storage` | explicit migrations, atomic persistence, checksum-verified imports/exports and recovery | destructive fallback |
| `ui` | touch/stylus gestures, accessible states and interpretation labels | main-thread filesystem and expensive RF work |
| `release` | package/signature/version/CI proof chain | silent user-data mutation |

Use Kotlin, Compose, Room and coroutines already present. Add no new framework merely to rename modules. Domain math is pure and independently testable. Radio events normalize through one adapter to validated snapshots; capture commits through one service; inference consumes immutable snapshots off the UI dispatcher, cancels superseded calculations and returns derived data only. UI never reads mutable shared DB internals directly.

## 6. Canonical schema and old-data mapping

| Entity | Required properties | Contract |
|---|---|---|
| `Project` | UUID, name, times, schemaVersion, activeFloorId | Multiple user-owned projects |
| `Floor` | UUID, projectId, level/name, activeRevisionId | Calibrated coordinate frame |
| `FloorPlanRevision` | UUID, floorId, parentRevisionId, worldBoundsMeters, calibrationId, createdAt | Immutable on publication |
| `Vertex` and `WallEdge` | stable IDs, world x/y, shared start/end vertex refs, type/locks | Valid connected topology |
| `RoomPolygon` | ID, ordered simple boundary, room label/type | Concave shapes; no self-crossing |
| `ReferenceAsset` | ID, private safe path, original SHA256, pixel dims, EXIF/crop/transform | No external URI dependency |
| `CalibrationEvidence` | measured spans, units, anchors, residuals, method, user confirmation | Auditable physical scale |
| `SurveySession` | ID, project/floorRevision, SSID/BSSID/band/freq, device, times/state | Explicit target cohort |
| `Observation` | ID, session/station, world XY, RSSI, target, timestamp/source/age, Android device details | Append-only hardware evidence |
| `SurveyStation` | ID, physical XY, observation IDs, note, aggregation version | Retains repeated raw samples |
| `InferenceResult` | revision/cohort hashes, method version, mask, contributors/weights, excluded/unknown cells | Disposable derivative |
| `ImportReceipt` | archive hashes, source version, item counts, identity mapping, result | Idempotent recovery proof |
| `ReleaseReceipt` | package ID, signer fingerprint, versionCode, APK hash, commit, CI run | Installed identity proof |

**Canonical XY:** origin at a declared floor-plan bounding-box top left, +X right, +Y down, values stored in meters. Not geographic coordinates, and not inferred compass north. Source-image pixels map through an independent ReferenceToWorld transform. Screen pixels map through WorldToViewport and its inverse; rotation, pan and zoom cannot mutate stored world coordinates.

**Legacy adapter:** Convert source v1 normalized 0–1 values using the original plan's explicit width/height only once. Preserve original bytes, coordinate values and an import manifest. Existing Room v1 survey pins must be marked `LEGACY_UNVERIFIED` when original BSSID, freshness, device and simulation truth cannot be reconstructed. Reassign durable UUIDs with stored legacy ID mapping, preserve SSID/RSSI/frequency/channel/timestamp/notes and do not invent BSSID. Existing photo missing? Show `REFERENCE_MISSING` while retaining walls/readings. No unverified legacy data is promoted to authenticated fresh-hardware status.


## 7. Geometry and calibration: what “accurate” means

### 7.1 Valid geometry and constrained editing

- Wall vertices are shared by adjacent rooms; room labels are independent metadata. Every published room polygon must be simple (no self-intersection), finite, nonzero area and contain three or more noncollinear vertices. A concave L-shaped room is valid. An edge may represent wall, door/opening or window; structural segments must never be inferred automatically from just room labels.
- Typed wall length is a **physical measurement** with units; meter values are canonical. A locked edge length and shared endpoint must not shift silently when the user drags a neighbor. Show a preview of proposed changes, unsatisfied constraints and the cancel/accept choices.
- Split/merge and move operations are reversible commands with stable IDs and a bounded undo/redo journal. A saved draft can be resumed after process death; “publish revision” creates an immutable snapshot. Undo never deletes original radio data.
- Room area and perimeter are mathematically derived from plan geometry; show a label such as “plan-derived area,” never “physically verified area” unless independently supported.
- View transformations must be reversible and deterministic. One-finger stylus draw is active only in drawing mode; two-finger pan/zoom never adds an accidental line. Test screen↔world round trip at 0.5×, 1×, 4× and 8× and portrait/landscape, with <=1-pixel synthetic tolerance for nondegenerate transforms.

### 7.2 Calibration states and evidence

| Status | Data available | Permitted interpretation |
|---|---|---|
| `UNCALIBRATED` | No physically measured reference | Distances are illustrative |
| `PARTIAL` | One known distance; unknown photo aspect/perspective | One provisional scale, not whole-plan certainty |
| `MEASURED` | Two independent references spanning both axes, or measured width and height, mutually consistent | User-measured scale; not a professional survey |
| `CONFLICT` | Measurements, wall locks or reference transforms disagree beyond threshold | Must resolve before trustworthy spatial inference |
| `STALE` | Reference photo, crop, orientation, perspective or world transform changed | Old alignment/calibration needs review |

For independent measured references, if inferred versus entered distances differ by more than **max(0.10 m, 5% of the larger measurement)**, display residuals and status `CONFLICT`; never average inconsistency away invisibly. Perspective correction uses four user-confirmed noncollinear image corners and must reject degenerate, mirrored or poorly conditioned transforms. It does not turn unmeasured pixels into real room sizes.

Changing only a viewport transformation cannot change world coordinates. Changing underlying geometry produces a new floor-plan revision, with previous raw observations still linked to the old revision. Replacing/cropping a photo or changing scale must mark any existing pin overlay `ALIGNMENT_REVIEW_REQUIRED` until the user checks an explicit reprojection. A map photo is a reference layer, not the canonical coordinate authority.

### 7.3 Photo and layer handling

Reference import accepts supported local Android images via Storage Access Framework or photo picker, and a user-triggered camera activity. Preserve the source image, store EXIF/rotation and SHA-256, create a size-bounded derivative for drawing, and keep separate visibility and opacity for photo, geometry, labels, pins, heatmap and unknown mask. Failed decode, missing image or permission revocation cannot erase the geometry. Image orientation and cropping cannot silently invalidate calibration; they cause `STALE` and a review action.

## 8. Verified radio acquisition and survey capture

### 8.1 Immutable provenance classes

- `CONNECTED_HARDWARE`: current connected-link RSSI delivered by Android with actual sample capture time, not necessarily a scan-result timestamp.
- `SCAN_RESULT_HARDWARE`: Android AP result with scan timestamp/freshness metadata when exposed.
- `IMPORTED_UNVERIFIED`: legacy/user-provided value with incomplete chain of custody.
- `DEMO`: explicit simulator observation, stored separately from verified sessions.
- `ESTIMATED`: interpolation/prediction, never raw radio evidence.

Only eligible hardware observations can be committed as verified session samples, and eligibility is checked at write time by the capture layer. An RSSI of -65 dBm is never substituted simply because Android returned no data. UI “fresh” labels must respect the actual Android timing clock. `WifiInfo` source values do not inherit unrelated `ScanResult` timestamps.

BSSID fields may be redacted by platform permissions as `02:00:00:00:00:00`: store `BSSID_UNKNOWN` and explain it, not an invented AP identity. Capture a persistent device/Android context without exposing user personal secrets by default.

### 8.2 Cohort and roam policies

The existing Survey Coach offers SSID+band *analysis cohorts* over saved pins, but cannot disambiguate mesh BSSIDs because legacy points lack BSSID. A future durable survey session binds to **one project, one published floor-plan revision, an explicit Wi-Fi target, and a device/source context**. The target includes SSID, BSSID if genuinely accessible, frequency/band and optional mesh identity. If the observed BSSID/band changes, pause verified capture and request a new session or explicit marked-roam continuation. Never auto-combine two APs sharing SSID into a “same signal” heatmap. A change in geometry calibration or provenance similarly invalidates automatic cohort comparison.

### 8.3 Hardware readiness state machine

States include `NO_WIFI`, `PERMISSION_DENIED`, `LOCATION_DISABLED`, `DISCONNECTED`, `SIMULATED`, `SCANNING`, `SCAN_THROTTLED`, `CONNECTED_FRESH`, `CONNECTED_AGE_UNKNOWN`, `ROAMED`, `BACKGROUND_PAUSED` and `READY_TO_CAPTURE`. Every non-ready state offers a specific remedy rather than an inert button.

All write paths recheck active foreground/lifecycle state, source, permissions, target cohort and location coordinate. No background radio ticker should run when the UI is inactive. Android 10+ generally allows four foreground `startScan()` requests per two minutes, subject to OS/vendor behavior; the existing `ScanFreshness` helper correctly converts microseconds since boot but capture-level provenance remains proposed; faster UI refresh does not mean fresh hardware scans. Neither time on screen nor tap count manufactures new radio evidence.

### 8.4 Repeated samples, outliers and drift

At each selected physical station, preserve multiple raw observations (suggested target 3–10 depending on availability), compute median RSSI, min/max, median absolute deviation, actual sample count, and classify missing/stale rather than averaging placeholders. Outliers can be flagged and retaken but are not automatically deleted. A hold-still hint from motion sensors can aid repeatability; it does not prove RF accuracy and must be skippable. Revisit a previously measured reference station to reveal drift under a comparable network/device context. Mesh roaming history is a sequence of actual observed transitions, not inferred trajectories.

## 9. Interpolation, unknown space and explanation

One map area can contain four mutually exclusive evidence states:

- **MEASURED_PIN:** a real station with a retained hardware observation/aggregate.
- **INTERPOLATED:** spatially estimated between eligible stations, marked estimated everywhere.
- **UNKNOWN:** no valid reading, uncalibrated space, wrong cohort, masked exterior or insufficient nearby samples.
- **PREDICTED_EXPERIMENTAL:** a later, clearly separate geometry/material model that cannot be described as observed.

Start from a versioned baseline `IDW_V1` matching the existing algorithm, then introduce `IDW_V2` using physical distances, validated cohort filtering, contributor weights and explicit mask reasons. Require **three spatially distinct eligible stations** rather than three clicks at one point; the default distinct-station separation is 0.5 m, with documented small-space exceptions. Default mask excludes cells farther than **3.5 m** from the nearest eligible sample and all cells outside a valid perimeter. Unmeasured areas must remain transparent/hatched; they cannot be colored as good or bad signal just to fill the canvas.

Tap-to-explain returns method version, selected target, contributing station IDs, positions/distances, age/source flags, normalized weights, sample count, mask decisions and any excluded/unknown reasons. Never claim a numeric statistical confidence percent without empirical calibration against held-out real samples. “Coverage of observations” is acceptable only when explicitly distinguished from “probability that the RF prediction is accurate.”

Before/after views allow comparison only when device, band/AP target and geometry revisions are compatible or have an explicitly approved mapping; otherwise label the mismatch and block a single unqualified delta score. Wall material attenuation is **not** a first-phase inference input until tested against physical measurements. An impressive-looking smooth heatmap without accountable data is a defect.

## 10. Interaction design: tablet first, without hiding uncertainty

Retain the five top-level destinations: Networks, Channels, Optimizer, Heatmap and Audit. Inside Heatmap, use an expandable **Apartment Studio** with:

1. **Project/Floor bar:** named project, selected floor, published revision, calibration state and dirty/saved status.
2. **Tool rail:** Select, Draw, Vertex, Wall, Door, Window, Room, Split/Merge, Erase, Measure, Reference, Undo and Redo.
3. **Canvas:** independent overlays for source image, walls, rooms, device/router and actual/estimated/unknown survey.
4. **Properties inspector:** wall length/lock, room type/name, reference transform, calibration residual, evidence source; on narrower devices a bottom sheet.
5. **Survey panel:** chosen SSID/BSSID/band, readiness, actual sample age, physical pin selection, capture/retake, sample count and next unsurveyed area.
6. **Evidence sheet:** raw readings for measured pins; contributor breakdown for interpolated areas.

**Gestures:** one finger/stylus draws only with active draw mode; two-finger navigation never creates walls. Offset magnifier and world-coordinate crosshair support corner precision. S Pen pressure/palm rejection are best-effort and real-device tested. Geometry undo does not remove survey evidence; radio sample deletion has a separate explicit confirmation and recoverable export route.

**Accessibility:** large accessible targets, semantic labels (not color-only meaning), system text scaling, keyboard/screen-reader controls, colorblind-safe legend with pattern cues, reduced motion, landscape tablet split layout, portrait bottom-sheet fallback and OLED-friendly dark scheme.

**UI states:** EMPTY, LOADING, READY, PERMISSION_DENIED, SIMULATED, STALE, STORAGE_LOW, CORRUPT_REFERENCE, INVALID_GEOMETRY, RECOVERY_READ_ONLY, MIGRATION_BLOCKED. Saved confirmation happens only after durable write completion. Photo import is accepted only after successful media copy and verification; camera cancellation leaves existing layout untouched.

## 11. Safe application identity, signing and migration — blocking Phase A

### 11.1 Existing package identities are separate

The current source explicitly uses `com.example` for legacy and `com.example.floorplan` for debug preview. They have different Android private app sandboxes. A release signed with a new persistent certificate **cannot retroactively update** an already installed APK signed by a different ephemeral debug certificate. No automatic uninstall, false “upgraded” status or inaccessible-sandbox read.

**Release policy:**

1. Verify actual tablet identity, installed packages, versionCodes, signing fingerprints, free space and accessible data. Trust current readbacks over the repository or older chat records.
2. Obtain a recoverable user-controlled export from every accessible legacy/preview identity before any replacement. If a legacy version cannot export, mark that migration blocked until a legitimate recovery path exists. Never claim to access another app's private folder without authorization/API support.
3. Choose a **new stable release package ID** only at the implementation-plan review after checking namespace collisions and deciding a user-visible migration path. Preserve both current identities untouched during preparation. No final package rename is authorized by this design document.
4. Store persistent release signing credentials only in protected release CI secrets and independently recoverable secure offline key storage. Commit a non-secret signing fingerprint and release receipt, not a keystore or password.
5. Verify SHA-256 APK content, signer fingerprint, versionCode, Git SHA and installation readback. Same-key update test and wrong-key negative test are mandatory.
6. If package or certificate identity conflicts, stop and show an explicit migration alternative. Never silently remove a prior installed app.

### 11.2 Explicit DB/schema migration

**Replace the current `fallbackToDestructiveMigration()` before increasing schema version.** Export Room schema history into source control, test an explicit v1→new migration against legacy fixtures, and reject any missing migration path into read-only recovery instead of table recreation.

The migration must preserve each old pin's physical meaning, RSSI, frequency, channel, timestamp, notes, layout ID and source ID mapping. Where BSSID/source-age/simulation identity cannot be proven, tag `LEGACY_UNVERIFIED`; never retroactively declare it fresh hardware. Migrate plan JSON and private photos via staging plus checksum, without overwriting originals until new DB and images reopen successfully. Interrupted migration resumes or rolls back idempotently without duplicates. Test corrupted data, schema version mismatch, zero free space and power/process termination.

**Low storage admission:** before staging, require at least two times the estimated temporary footprint plus an additional 100 MiB available, and separately respect Android's real APK install space requirements. This is a conservative heuristic, not a guarantee of enough space. If unmet, stop before modification and display safe user-directed choices. App must not delete unrelated photos or uninstall packages.

### 11.3 Portable ownership, privacy and backups

A `DexWifiProject` archive comprises versioned manifest, geometry revisions, source images, calibration evidence, raw observations, import mapping and per-entry SHA-256. Use safe relative paths and strict limits; reject zip traversal, decompression bombs, duplicate IDs, hash mismatch, unsupported schema and invalid coordinates. Import creates a **new** project by default; merge/overwrite is a separately confirmed operation. Export has per-field redaction choices for SSID, BSSID, room notes, physical coordinates, photos, device identifiers and times, previewed before saving. An unencrypted archive must be labeled sensitive. Never store router credentials in project data.

Android Auto Backup may capture app-private files when enabled. Explicitly configure and test `dataExtractionRules` for Android 12+ and `fullBackupContent` for older devices, distinguishing cloud backups from device-to-device transfer. Keep deliberate user-controlled export regardless of automatic backup settings. Avoid making a “nothing ever leaves your tablet” claim without verifying all relevant platform routes.

## 12. Router-admin scope boundary

Keep the existing Radio Audit Lab, Survey Coach, Connection Doctor and `RouterAdminPolicy` as a safe gateway to **the router's own authenticated browser UI**. The Connection Doctor currently reads OS validation/captive status and offers opt-in private gateway TCP-web-port and DNS checks; these do not prove measured throughput or every connection failure cause. The app may display an observed pre-change channel, recommend a supported band/channel with explicit uncertainty, open a private local gateway URL, and observe a post-change reconnection. It must not collect admin credentials, call undocumented router APIs, or claim an admin setting changed solely because Android later observed another channel. ISP-managed routers can enforce automatic channel behavior. Rollback is a documented user action in the router's interface, not a device reset command.

No direct router writes, remote admin integrations or surveillance tooling are authorized in this architecture.

## 13. Performance and resource acceptance

All performance claims require baseline and candidate on the **same physical device, OS, map, input method and radio mode**. JVM collection microbenchmarks are useful for narrow regressions but not proof of user-visible S Pen responsiveness.

| Workload | Proposed acceptance target | Evidence |
|---|---|---|
| 2,500 geometry segments + live stylus | Gesture median frame <=16.7ms, p95 <=32ms when feasible on target device | Frame traces, repeated equivalent gesture |
| 1,000 station readings | Canvas interaction p95 <=100ms; no ANR | Instrumented tablet replay |
| 4096×3072 input image | Off-main decode, bounded preview bitmap (initial <=1536px long edge), no OOM | Bitmap/heap/trace receipt |
| Pan and zoom | Does not rerun RF interpolation just because viewport moved | Inference call counter test |
| New observation / altered session | Cancels superseded calculations; publishes only matching cohort/revision | Trace and async race fixture |
| Cold start and resume | No >10% slowdown versus matched 20-run baseline without documented review | Run distribution and memory delta |
| Background state | No periodic scans/history ticker when app inactive | Lifecycle test and energy trace |
| Low disk / interrupted export | No original data loss, clear recovery state | Failure injection and hash comparison |

These are targets to test, **not measured performance achievements**. If hardware cannot satisfy a proposed number, preserve the result honestly and let the next review decide scope. Never reduce benchmarks solely to obtain a green result.

Derived cache keys must contain project + floor revision, calibrated transform hash, selected session/cohort hash, algorithm version, mask and settings. Cached pixels are disposable; raw source observations are not. Keep photo loading and interpolation off the main Compose thread, use cancellable coroutines and avoid redundant list copying during continuous sketch strokes.


## 14. Adversarial failures and recovery states

| Fault to inject | Required non-destructive response | Regression proof |
|---|---|---|
| Wrong APK signer / mismatched package | Reject overwrite, identify mismatch, preserve installed version and data | Negative update/install test |
| Tablet lacks required free storage | Stop before staging or installing; show disk requirement, no cleanup side effects | Low-space and source-hash fixture |
| Unknown Room migration version | Read-only recovery, never implicit table recreation | Preserved v1 + incompatible schema fixture |
| Corrupt legacy JSON/photo | Quarantine failed asset only, keep readable map and measurements | Truncated JSON and missing/corrupt JPEG |
| Terminate app during autosave | Restore last consistent draft; no half-published revision | Repeated process-kill test |
| Permission revoked after UI enables Record | Commit-time validator rejects pending sample | Race/permission state fixture |
| Background app or scan throttle | Suspend collection; show last known timestamp | Lifecycle and stale-read fixture |
| Connected AP changes BSSID/band | Pause or tag roam explicitly; no hidden cohort mixing | Simulated AP transition |
| Room graph self-intersects or locks conflict | Refuse invalid commit, preserve prior revision | Polygon/constraint property tests |
| Edited photo changes alignment | Keep old pins on old revision; require realignment review | Revision-delta fixture |
| Slow bitmap or heatmap recompute | Cancel stale compute, keep UI interactive | Stress/frame trace |
| Malicious ZIP/path traversal | Reject before write; no partial import | Archive fuzz fixtures |
| Router admin page unreachable or forced auto | Report inability to apply; never pretend it changed | Private gateway and unsupported-router tests |
| Cloud backup differs from user expectation | Clearly document backup mode; honor tested rules | Android backup/transfer policy tests |
| Inference has no eligible samples | Render unknown region rather than invented good/bad signal | Empty/co-located/stale cohort tests |

## 15. Verification plan and release gates

### G0 — Baseline capture (before touching application code)

- Resolve exact current HEAD and unmerged PR state.
- Identify installed package IDs, versionCodes, signing certificates and actual intended tablet serial over authorized ADB; do not infer from source alone.
- Record live free space and source data row counts, photo hashes and export availability. The older ~508 MiB observation is not a replacement for current measurement.
- Protect successful Networks/Channels/Optimizer/Heatmap/Audit user paths and manual router-admin handoff.
- Capture original CI test/proof links, current memory/gesture baselines and rollback commit.

### G1 — Safe upgrade and data preservation

- A durable release signing lineage is established **for a new channel**, or the installation remains a clearly labeled side-by-side preview.
- Explicit v1→new Room migration passes with old database fixtures. Row/data hashes match and original map/photo data reopens. No destructive fallback remains.
- Export/restore round trip, crash during migration, insufficient free space, incompatible package/signature, invalid import and duplicate import all preserve originals.
- On physical tablet the original app remains installed until verified user-mediated migration. This is a HARD release gate.

### G2 — Geometry/editor

- Unit/property tests: concave polygon, room split/merge, self-intersection refusal, shared-vertex graph, locked length conflict, known-distance calibration, inverse world/screen transforms at supported zoom.
- UI tests on API 35 and 36: create room, drag/undo, photo picker, camera cancellation/return, enter measured width/height, save, force-stop/reopen, relink image, change orientation and inspect stable pin alignment.
- Physical Samsung tablet: finger and S Pen edit, tap targets, large font, landscape split, portrait sheet and stress plan with 2,500 segments/1,000 pins.
- Preservation check: old evidence remains assigned to its correct floor revision.

### G3 — Wi-Fi session truth

- Test denied permission, Location off, disconnected, redacted BSSID, stale scan, scan throttled, simulated mode, AP roam, band switch and background task cancellation.
- Assert none of these invalid inputs can produce a falsely verified stored sample. Assert session aggregation is deterministic and raw readings remain unchanged.
- Physically measure at two known locations under user consent; verify observed identifiers/timestamps against Android-visible state and explicit device limitations.

### G4 — Explainability, privacy and import

- Every estimated cell must have eligible sample IDs/weights and correct algorithm/revision ID; all unknown/outside/excluded cells stay unfilled.
- Three clicks at the same station do **not** satisfy the distinct-station minimum. A change of band/BSSID/revision invalidates its prior interpolation cache.
- Exported private fields are present only when chosen; tampered/malicious archives fail without data loss; accepted round trip matches manifests and sample values.
- Connectivity diagnosis distinguishes link, gateway, DNS and internet; no negotiated link rate is reported as a measured speed test.

### G5 — Release proof

- Gradle compilation, unit/property/migration tests, API 35/36 instrumented UI and emulator smoke, actual Samsung cold launch, photo/camera, saved drawing, Wi-Fi survey and background/resume all pass.
- Release manifest must include **source Git SHA, APK SHA-256, package ID, signer fingerprint, versionCode, CI run URL, explicit device serial confirmation and installed-app readback**.
- Performance comparison includes raw matched traces, frame time distribution, memory and battery/lifecycle observations; a claim of “performance uplift” without measurements is forbidden.
- Only after relevant gates may a later approved task merge, release or install. An emulator pass is not Samsung acceptance.

Evidence states must remain separate: `PLANNED`, `IMPLEMENTED_UNVERIFIED`, `EMULATOR_VERIFIED`, `TABLET_VERIFIED`, `BLOCKED`, `FAILED` and `SUPERSEDED`. Review changes can preserve a partial verified result without claiming universal completion.

## 16. The 32-feature uplift ledger

**Status:** No app code is implemented by this document. F05, F15, F17, F24, F25 and F26 **extend partially implemented Survey Coach or Connection Doctor behavior**, and are not represented as wholly new. The remaining 26 are additional capabilities not established by the current source snapshot. All 32 are future acceptance targets. Slices: A safety, B geometry, C measurement, D explanation/diagnostics/performance.

| ID | Feature | Slice | Minimal acceptance proof |
|---|---|---|---|
| F01 | Stable private release signing | A | Two same-cert updates preserve saved database |
| F02 | Original/preview/stable package migration | A | Explicit export/import restores counted and hashed data |
| F03 | Crash-safe quarantine recovery mode | A | Corrupt plan doesn't prevent access to other projects |
| F04 | Recoverable atomic draft autosave | B | Force-stop restores last consistent sketch |
| F05 | Platform/hardware readiness wizard | A/C | Permission/Location/scan deficiency explains and blocks false capture |
| F06 | Arbitrary concave room polygons | B | L-shaped room saves and computes containment/area |
| F07 | Typed measured wall lengths | B | Physical numeric wall length persists through restart |
| F08 | Shared walls with lockable constraints | B | Corner drag respects locks or aborts safely |
| F09 | Reversible room split and merge | B | Valid revision operation, history retained |
| F10 | Separate photo/geometry/pin layers | B | Toggling a layer does not move geometry |
| F11 | Non-destructive crop/rotate/photo enhancement | B | Original image retained and calibration warned |
| F12 | Finger-offset precision drawing magnifier | B | Selected vertex can be precisely positioned at zoom |
| F13 | Structural geometry validator | B | Zero-area and self-crossing room blocked |
| F14 | Tablet split workspace, responsive inspector | B | Landscape and portrait tasks remain accessible |
| F15 | Locked AP/network survey cohort | C | BSSID/frequency changes pause or start new session |
| F16 | Immutable source-rich observation record | C | Sample linked to session, device, timing and floor revision |
| F17 | Stale scan and throttle detection | C | Unfresh observation cannot be labeled fresh |
| F18 | Optional hold-steady signal capture assistant | C | Motion warning never fabricates a sample |
| F19 | Outlier review with non-destructive retake | C | Original sample and retake both remain |
| F20 | Reference station drift remeasurement | C | Like-for-like revisit produces a drift record |
| F21 | Per-access-point mesh roaming trail | C | AP transition records actual identifiers/time |
| F22 | Real-life buffering/dropout issue markers | D | Issue pin separate from RSSI |
| F23 | Explainable per-cell heatmap provenance | D | Tap shows contributor IDs, weights and sample ages |
| F24 | Multi-stage failure diagnosis beyond current gateway/DNS probes | D | Gateway, DNS, OS validation and internet cases supported by distinct observations |
| F25 | Actionable captive/limited-internet triage beyond OS status | D | Portal and validation recovery states distinguished without false Internet claim |
| F26 | Optional genuine throughput test distinct from OS/PHY estimates | D | Real controlled transfer test is clearly separate from link Mbps and OS bandwidth hints |
| F27 | Channel/DFS/regulatory suitability guards | D | Unsupported recommendation visibly constrained |
| F28 | Redaction preview for export | D | User-excluded sensitive fields absent in archive |
| F29 | Missing reference image relinking | B | Reattach reference without erasing readings/walls |
| F30 | Incremental heatmap recomputation | D | Pan/zoom doesn't trigger RF recalculation |
| F31 | Adaptive render budget and geometry caching | D | Matched tablet workload improves without misleading pixels |
| F32 | Privacy-safe diagnostic evidence package | D | Support bundle includes integrity receipt, no passwords |

### 16.1 Traceability back to user goals

| User objective | Contract sections | Terminal gate |
|---|---|---|
| Safe APK updates, protect data | 1, 4, 6, 11 | G0/G1/G5 |
| Accurately draw apartment and identify rooms | 3/J2, 7, 10, F06–F14 | G2 |
| Take/import a picture and define dimensions | 3/J2, 7, F10/F11/F29 | G2 |
| Trust real Wi-Fi measurements | 3/J3, 8, F15–F21 | G3 |
| Make interpolation understandable | 3/J4, 9, F23 | G4 |
| Improve real tablet UX and performance | 10, 13, F30/F31 | G2/G5 |
| Preserve safe admin workflow | 1, 4, 12 | G0/G4 |
| Provide 20+ meaningful new features | F01–F32 | Each relevant slice gate |

## 17. Sequenced work after SPEC approval

Superpowers `writing-plans` will create explicit, finite implementation packets only after the user reviews/approves this written specification:

**Plan A (blocking):** package/signing inventory, project backup, explicit Room migrations and `fallbackToDestructiveMigration()` removal, export/restore, safe recovery and readiness. No new feature rollout while source data isn't protected. Stop at G1.

**Plan B:** physical world-coordinate and room graph, calibrated revisions, photo/reference layers, geometry commands, precision editor and crash-resilient autosave. Stop at G2.

**Plan C:** hardware observation provenance, session ID, real freshness, source/roam changes, repeat stations and drift. Stop at G3.

**Plan D:** explainable interpolation, diagnostics, portable/privacy reports, incremental caching and matched device performance. Stop at G4/G5.

No two simultaneous tasks may independently edit the same Room schema, signing configuration, authoritative floor graph or capture-session contract. Parallel research and disjoint tests are acceptable; conflicting schema edits are not. Every slice uses TDD, limited Git changes, a rollback path and evidence-labeled acceptance.

## 18. Risk decisions, out-of-scope boundaries and document review

| Temptation / risk | Decision | Reason |
|---|---|---|
| Add 32 controls to current 600-line editor directly | Reject as architecture | Keeps geometry, UI and persistence tightly coupled |
| Promise professional RF prediction from wall sketches | Defer | Wall material physics unvalidated |
| Automatically interpret one photo as a measured floor | Reject | Physical scale and distortion unknown |
| Always-on fast background scanning | Exclude | OS throttling/battery/privacy limits |
| Password-capturing in-app router login | Exclude | Keep authenticated browser route |
| Delete apps/files to make install fit | Prohibit absent new explicit authorization | Prior tablet data may be irreplaceable |
| Merge or release because emulator is green | Reject | Real Samsung interactions are unverified |
| Mandatory cloud sync, telemetry, paid API or Docker | Out of scope | Local ownership and scope discipline |
| Treat legacy RSSI as fresh and same-BSSID | Prohibit | No trustworthy source history |
| One pull request delivering all new capabilities | Reject | Poor proof, rollback and code review |

### 18.1 Factual references and project authority

Official Android contracts:
- Wi-Fi scan permissions, location services and throttling: https://developer.android.com/develop/connectivity/wifi/wifi-scan
- WiFiInfo privacy redaction of SSID/BSSID: https://developer.android.com/reference/android/net/wifi/WifiInfo
- App-signing certificate and updates: https://developer.android.com/studio/publish/app-signing
- Room migrations and destructive fallback: https://developer.android.com/training/data-storage/room/migrating-db-versions
- Android backup/data-extraction rules: https://developer.android.com/identity/data/autobackup

Comparable products:
- NetSpot Android manual: https://www.netspotapp.com/help/netspot-android-manual/
- magicplan Android plans: https://help.magicplan.app/creating-floor-plans-in-magicplan-on-your-android-device
- magicplan dimension locks: https://help.magicplan.app/how-to-lock-and-unlock-dimensions

Current authoritative project snapshot:
- https://github.com/westkitty/Dex_WIFI/tree/f76682720dba7e42721791793e6ef1b9c51c3ead
- https://github.com/westkitty/Dex_WIFI/actions/runs/39003366809 (previous green baseline)
- https://github.com/westkitty/Dex_WIFI/actions/runs/39005555004 (current commit, pending at preparation)
- `Dex_WIFI_bible.md`, same snapshot. Historic status is evidence, not current device truth.

Third-party precedent informs interaction patterns, not claims that Dex_WIFI already implements their exact features or achieves their numerical performance.

### 18.2 Design-approval gate

The user's approval of the **direction** authorized this document, **not its implementation**. Review and explicit approval of this written design will decide:

1. Safe ownership/signing/data migrations precede all major feature work.
2. Physical meters, valid room topology, verified calibration states and revision mapping govern apartment maps.
3. Session-bound raw hardware observations govern Wi-Fi interpretation, with explicit UNKNOWN/ESTIMATED classes.
4. Each implementation slice needs its own plan, tests, rollback and actual Samsung tablet proof.

A written-spec approval does **not** authorize selecting a permanent app package name without reviewing the installed identities, deleting data, distributing secrets, merging PR #2, or an ADB installation. Those remain separate evidence-backed actions.

**STOP HERE:** after commit and review, do not invoke implementation tools. The next Superpowers stage is `writing-plans` for Plan A, only after the user explicitly approves this document.
