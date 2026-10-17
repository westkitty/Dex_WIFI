# Dex_WIFI Phase A — Safe Ownership and Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make existing Dex_WIFI maps, photographs, survey observations, app updates and recovery **provably non-destructive** before introducing new apartment geometry or hardware measurement schemas.

**Architecture:** Preserve the existing Kotlin/Jetpack Compose and Room v1 source model while adding a read-only snapshot path, portable integrity-checked export, quarantine-first import, empty-target-only promotion, explicit Room v1→v2 migration, and recoverable startup. Gate stable release signing and any Android installation on verified app identity, certificate lineage, available storage and durable user-controlled backup. Do not rewrite the feature branch's new Survey Coach or Connection Doctor subsystems.

**Tech Stack:** Kotlin 2.3.21; Compose BOM 2025.11.01; Room 2.8.4 + KSP 2.3.8; Android compileSdk/targetSdk 36, minSdk 24; Java 17; Gradle 8.14.3 via the existing GitHub Actions workflow; JUnit 4, Robolectric 4.16.1, Android instrumentation, Python 3 standard library for a portable release preflight CLI.

**Spec:** `docs/superpowers/specs/2026-10-17-dexwifi-evidence-first-architecture-design.md` (approved October 17, 2026, Git commit `1a902189746f42f59c03f9a1c5ddaf7a1abc6cc3`)

**Planning baseline:** Source `f76682720dba7e42721791793e6ef1b9c51c3ead`, feature branch `feat/floorplan-studio-20261016`. Its GitHub Actions run https://github.com/westkitty/Dex_WIFI/actions/runs/39005555004 **PASSED** build/unit tests and emulator install/launch on Android API 35 and 36. The source already contains Survey Coach, Connection Doctor, `ScanFreshness` and related tests; no Phase A change is claimed implemented. This plan is written on a **documentation-only** branch; executors must inspect/compare the actual active source HEAD before starting and reconcile changes rather than blindly applying stale line numbers.

**Status:** PLANNED / NOT APPROVED FOR EXECUTION. Approval of the design specification authorizes this plan's creation only; execution method and plan approval are separate gates.

## Global Constraints

- Preserve both known package IDs: original `com.example` and debug preview `com.example.floorplan`. They own distinct Android sandboxes; never silently uninstall, overwrite, rename or cross-read private app data.
- Current Gradle application uses `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`, `versionCode = 2`. Preserve current runtime compatibility; a new release identity/version scheme requires proof before release.
- Current Room file is `wifi_survey.db`, entity table `survey_points`, database schema **v1**; `AppDatabase.kt` currently calls `fallbackToDestructiveMigration()`. Remove that fallback before **any** version increase and prove explicit migration.
- Current private floor plan is SharedPreferences `floorplan_studio_v1`, key `document`, JSON `version=1`. References live as `plan_photo_*.jpg` in the app's private `filesDir`. Never lose original bytes or infer missing calibration/provenance.
- Preserve all survey records across **every** layoutId, including user and preset layouts, and exact `id`/`posX`/`posY`/`primarySsid`/`rssi`/`frequency`/`channel`/`timestamp`/`notes` values. Do not label legacy records as verified fresh-hardware evidence.
- Existing Networks, Heatmap, Channels, Optimizer, Audit, Survey Coach, Connection Doctor and user-mediated router-admin browser handoff must continue to operate. Preserve local-first mode; no new mandatory cloud, telemetry, privileged rooting, Docker, external router settings API or secret collection.
- No file cleanup, app uninstall, DB reset, user-facing release, `git merge` or physical ADB installation under this plan without an explicit later execution gate and verified recoverable backup. Debug CI signing cannot update an unrelated installed certificate.
- No foreground scan-throttle violation or false “fresh” labeling. Phase A readiness is diagnostic; full immutable session provenance belongs to Phase C, and metric polygon editing belongs to Phase B.
- Before staging large backup/import data, available free bytes must be at least **2× estimated temporary working footprint + 100 MiB**; check Android package installation space separately. On failed admission: **zero live-data mutation**.
- Tests must prove behavior with captured fixtures and process-restored state, not just successful compilation. A green API 35/36 emulator is not a passed physical Samsung tablet acceptance.
- Do not invent a permanent public package ID or recoverable private signing key in code. The final ID and credential custody remain explicit user review decisions; fail closed until provided through a protected release environment.
- Repository commits use the actual source branch/worktree selected **at execution**, not this documentation branch. Append relevant milestones to `Dex_WIFI_bible.md` after verified work; never rewrite previous entries.
- **Execution bootstrap:** first invoke the Superpowers `using-git-worktrees` workflow if available, place the implementation on a new isolated worktree/branch from a freshly verified feature HEAD, run `git status --short`, and record source SHA. No implementation begins on the open PR branch itself without explicit approval.

## Review Focus — five subtle input classes that the owner tasks must test

1. **No custom plan, but existing preset-layout samples:** Task 2 test exports **all** Room `survey_points`, not just `user_floorplan_v1`, and retains layoutIds. A missing plan is not an empty dataset.
2. **Storage Access Framework returns null/throws or fails mid-stream:** Task 4 test returns `FAILED` and never displays “Backup complete” or changes source files, even if a partial destination document exists.
3. **Malicious/ambiguous ZIP names and repeated archive entries:** Tasks 3 and 5 reject duplicate or aliasing entries, `../` paths, over-budget decompression and mismatched hashes *before* promotion.
4. **Empty floor-plan preference but occupied database:** Task 8 test declares target `OCCUPIED` if **either** plan data **or any** survey row exists; zero overwrites.
5. **Installed signer unknown, ADB offline or storage unverified:** Tasks 1 and 11 tests forbid upgrade/install verdicts; an unknown certificate is never treated as equal.

---

## File ownership map (locked for this plan)

| File / new package | Owner and role |
|---|---|
| `tools/release/identity_gate.py`, `tools/release/tests/test_identity_gate.py` | Pure package/signature/space decision function plus read-only ADB/APK inspector |
| `app/src/main/java/com/example/backup/LegacySnapshot.kt` | Exact legacy survey/photo/raw-plan value types, archive version constants |
| `app/src/main/java/com/example/backup/LegacySnapshotReader.kt` | Read-only snapshot of Room, preferences and private plan-photo files |
| `app/src/main/java/com/example/backup/ProjectArchiveCodec.kt` | Versioned ZIP encode/verify, canonical manifest, hash/size/path validation |
| `app/src/main/java/com/example/backup/ProjectExportCoordinator.kt` | SAF stream export, truthful success/failure, source unaffected |
| `app/src/main/java/com/example/backup/ProjectImportVerifier.kt` | Import to private staging, validate/preview, no live restore |
| `app/src/main/java/com/example/backup/ProjectRestoreCoordinator.kt` | Empty-target-only promotion, journal recovery and import idempotence |
| `app/src/main/java/com/example/backup/RestoreJournal.kt` | Durable PREPARED/DB_WRITTEN/DONE state with consistent recovery |
| `app/src/main/java/com/example/data/DataStartupState.kt`, `DataStartupCoordinator.kt` | Report saved-plan corruption/DB mismatch without crashing or clearing data |
| `app/src/main/java/com/example/data/ImportReceiptEntity.kt`, `ImportReceiptDao.kt` | Room v2 receipt for applied archive and row count |
| `app/src/main/java/com/example/data/migrations/Migrations.kt` | Tested explicit Room `MIGRATION_1_2` |
| `app/src/main/java/com/example/data/AppDatabase.kt` | Schema export, v1→v2 migration registration and removal of destructive fallback |
| `app/src/main/java/com/example/data/SurveyDao.kt` | Read-all, count-all, collision-safe insert for owned backups |
| `app/src/main/java/com/example/data/FloorPlanStore.kt` | Raw plan read, status inspection and guarded plan write; photo naming preserved |
| `app/src/main/java/com/example/service/HardwareReadiness.kt` | Pure diagnostic state from actual platform/connection snapshot |
| `app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt` | One accessible Data Safety UI for SAF export/import, blocked/recovery states |
| `app/src/main/java/com/example/ui/WifiViewModel.kt` | Connect existing UI to new backup/startup/readiness state without changing core survey behavior |
| `app/src/main/java/com/example/ui/screens/RadioOperationsScreen.kt` | Data Safety entry under Audit, preserving all existing audit controls |
| `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml` | Explicit Android 12+ and earlier backup policy |
| `tools/release/release_gate.py`, `tools/release/tests/test_release_gate.py`, `ci/release-verify.sh` | Fail-closed release-artifact signing and version checks, no secret printing |
| `.github/workflows/android.yml`, `ci/smoke-launch.sh` | Preserve and extend API 35/36 emulator proof |
| `app/src/test/java/com/example/*Test.kt`, `app/src/androidTest/java/com/example/*Test.kt` | Pure, Robolectric and instrumented migration/runtime gates |
| `Dex_WIFI_bible.md` | Append-only final proof/blocked ledger after the corresponding implemented unit |

This is a **target ownership map**; newly named files do not yet exist. Existing file names and dependencies were checked against source `f7668272`. A future repository change may require explicit plan revision instead of silently writing the wrong path.

## Dependency and stop structure

```text
T1 identity preflight -> T11 signed release gate
T2 legacy snapshot -> T3 archive codec -> T4 user export
                                    \-> T5 quarantine/staged import
T6 safe startup / recovery ----------> T7 Room migration and receipt
T5 + T7 -----------------------------> T8 empty-target promotion
T9 hardware readiness ----------------> T12 integration / physical proof
T10 Android backup policy ------------> T12
T4 + T8 + T11 ------------------------> T12
```

**Hard stop A:** No destructive schema change, app removal, signed-release publication or installed-app upgrade before export/restore is demonstrated with fixtures. **Hard stop B:** Unknown package signer, inaccessible legacy data, unaffordable free-space requirement or no authorized tablet access means report **BLOCKED** and continue only non-device code/test work. **Hard stop C:** A failed required test prevents declaring Phase A complete.

## Task 1: Release identity and device admission preflight

**Files:**
- Create: `tools/release/identity_gate.py`
- Create: `tools/release/tests/test_identity_gate.py`
- Test: Python stdlib unittest; no device required for test execution.

**Interfaces:**
- Produces: `InstalledIdentity(package_id: str, version_code: int, cert_sha256: str | None)`, `CandidateIdentity(package_id: str, version_code: int, cert_sha256: str | None)`, `InstallDecision(status: str, reason: str)`.
- Produces: `decide_install(installed: InstalledIdentity | None, candidate: CandidateIdentity, backup_verified: bool, free_bytes: int | None, required_bytes: int) -> InstallDecision`. Status set: `SAFE_SAME_CERT_UPDATE`, `SAFE_NEW_PACKAGE_INSTALL`, `BLOCKED_SIGNATURE`, `BLOCKED_UNKNOWN_SIGNER`, `BLOCKED_STORAGE`, `BLOCKED_RECOVERY`.
- CLI inspection: `python3 tools/release/identity_gate.py inspect --serial SERIAL --candidate APK`; uses read-only `adb devices -l`, `adb -s SERIAL shell pm path PACKAGE` and installed-state queries plus `apksigner verify --print-certs APK`. If the installed certificate cannot be proven, status must be `BLOCKED_UNKNOWN_SIGNER`. Do not use a hardcoded tablet serial.

- [ ] **Step 1: Write failing tests** in `test_identity_gate.py` named `test_unknown_installed_signer_blocks_update`, `test_different_signer_blocks_update`, `test_matching_signer_and_newer_version_allows_update_only_with_recovery`, `test_empty_adb_target_blocks_device_admission`, `test_insufficient_storage_blocks`. Check the exact returned statuses, not printed reassurance.

**Representative red-test assertions** (create the fixtures named above):

```python
candidate = CandidateIdentity("com.example.floorplan", 3, "aa" * 32)
installed = InstalledIdentity("com.example.floorplan", 2, None)
self.assertEqual("BLOCKED_UNKNOWN_SIGNER",
    decide_install(installed, candidate, True, 512 * 1024 * 1024, 30 * 1024 * 1024).status)
```

- [ ] **Step 2: Verify red.** Run `python3 -m unittest discover -s tools/release/tests -p 'test_identity_gate.py' -v`. Expected FAIL: new module/types unresolved.
- [ ] **Step 3: Implement minimal decision model and read-only adapters.** Normalize fingerprint case/separators, reject missing/zero version, invalid required bytes and absent serial; handle `adb`/`apksigner` errors as blocked and redact raw package paths/serial if generating a shared diagnostic report.
- [ ] **Step 4: Verify green.** Run the same unittest command; expected all five named cases PASS. Run CLI `--help` and ensure no installation command, uninstall or wipe call is present.
- [ ] **Step 5: Commit** only the two files: `git add tools/release/identity_gate.py tools/release/tests/test_identity_gate.py && git commit -m "test(release): fail closed on unknown APK identity"`.

## Task 2: Read-only legacy ownership snapshot across all layouts

**Files:**
- Create: `app/src/main/java/com/example/backup/LegacySnapshot.kt`
- Create: `app/src/main/java/com/example/backup/LegacySnapshotReader.kt`
- Modify: `app/src/main/java/com/example/data/SurveyDao.kt`
- Modify: `app/src/main/java/com/example/data/FloorPlanStore.kt`
- Test: `app/src/test/java/com/example/LegacySnapshotTest.kt`

**Interfaces:**
- `LegacySurveyRow(id: Long, layoutId: String, posX: Float, posY: Float, roomName: String, primarySsid: String, rssi: Int, frequency: Int, channel: Int, timestamp: Long, notes: String)`; preserve exact legacy values and label provenance `LEGACY_UNVERIFIED` only as new metadata, without changing fields.
- `LegacyPhoto(name: String, bytes: ByteArray)`; include all safe `plan_photo_*.jpg` files, referenced or otherwise, with separate SHA-256. A missing referenced photo is recorded as `MISSING_REFERENCE`, not grounds for discarding other content.
- `LegacySnapshot(sourcePackageId: String, planJsonRaw: String?, rows: List<LegacySurveyRow>, photos: List<LegacyPhoto>, diagnostics: List<String>)`.
- Add `@Query("SELECT * FROM survey_points ORDER BY id ASC") suspend fun getAllForBackup(): List<SurveyEntity>` and `@Query("SELECT COUNT(*) FROM survey_points") suspend fun countAll(): Int` to `SurveyDao`.
- Add `fun rawDocumentForBackup(): String?` and `fun listPrivatePlanPhotos(): List<File>` to `FloorPlanStore`.
- `class LegacySnapshotReader(private val appContext: Context, private val dao: SurveyDao, private val store: FloorPlanStore) { suspend fun snapshot(): LegacySnapshot }`.

- [ ] **Step 1: Write failing Robolectric cases** `snapshotIncludesPresetLayoutWithoutCustomFloorPlan`, `snapshotPreservesEveryLegacyFieldAndAllLayouts`, `snapshotPreservesCorruptRawJsonInsteadOfNull`, `snapshotRetainsOlderUnreferencedPhoto`, `snapshotIncludesMissingPhotoWarningWithoutDroppingRows`. Use two survey layout IDs and at least one custom photo. Compare raw row values exactly.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
val snapshot = reader.snapshot() // test DB has IDs 17/18 on two different layoutIds
assertEquals(listOf(17L, 18L), snapshot.rows.map { it.id })
assertEquals(setOf("preset_fixture", "user_floorplan_v1"),
    snapshot.rows.map { it.layoutId }.toSet())
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:testDebugUnitTest --tests "com.example.LegacySnapshotTest"`. Expected FAIL at unresolved types/methods, no source DB mutation.
- [ ] **Step 3: Implement reader and two read-only data accessors.** Read Room's row list from a consistent transaction/snapshot, keep all preference JSON bytes even if the usual load fails, validate photo names via existing `photoFile` path policy, and bound memory consumption before loading photos (fail with descriptive `ARCHIVE_TOO_LARGE`, not OOM). Do not change the original saved data.
- [ ] **Step 4: Verify green.** Run the focused test then `gradle --no-daemon :app:testDebugUnitTest`. Expected each new test PASS and existing FloorPlanStore/SurveyCoach tests unchanged.
- [ ] **Step 5: Commit** the two new files, two specific data files and focused test: `git add app/src/main/java/com/example/backup/LegacySnapshot.kt app/src/main/java/com/example/backup/LegacySnapshotReader.kt app/src/main/java/com/example/data/SurveyDao.kt app/src/main/java/com/example/data/FloorPlanStore.kt app/src/test/java/com/example/LegacySnapshotTest.kt && git commit -m "feat(backup): snapshot all legacy survey and map data read-only"`.

## Task 3: Strict versioned project archive codec

**Files:**
- Create: `app/src/main/java/com/example/backup/ProjectArchiveCodec.kt`
- Test: `app/src/test/java/com/example/ProjectArchiveCodecTest.kt`

**Interfaces:**
- `object ProjectArchiveCodec` with `fun write(snapshot: LegacySnapshot, destination: OutputStream): ArchiveManifest` and `fun verify(source: InputStream): VerifiedArchive`.
- `ArchiveManifest(schemaVersion: Int, sourcePackageId: String, rowCount: Int, entries: List<ArchiveEntryHash>, planStatus: String)` and `VerifiedArchive(manifest: ArchiveManifest, snapshot: LegacySnapshot, archiveSha256: String)`.
- ZIP v1 entries: `manifest.json`, `legacy/plan.json` when raw exists, `legacy/survey_rows.jsonl` (all layouts), and `photos/<safe-original-filename>.jpg`. The manifest SHA-256 covers each data entry; the manifest does not hash itself. Retain historical raw invalid plan JSON as `CORRUPT_RAW_PRESERVED` rather than claim it is valid.
- Limits: max compressed input 64 MiB; per decoded entry 32 MiB; cumulative decoded bytes 128 MiB; 100 entries; 50,000 survey rows. Fail on duplicate/case-folded alias entry, absolute/`..` paths, invalid schema or hash, unsafe filenames and malformed numeric values. Never read an entire unbounded ZIP into RAM.

- [ ] **Step 1: Write failing tests** `exportRoundTripPreservesAllRowsAndPhotoHashes`, `corruptPlanRawRoundTripRemainsUnverified`, `duplicateAndTraversalEntriesRejected`, `badShaAndTruncatedZipRejected`, `compressedBombExceedsBound`, `unknownSchemaRejectedWithoutPartialSuccess`. For exact provenance preservation assert the same legacy row IDs/values rather than inventing BSSID.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
val verified = ProjectArchiveCodec.verify(encodedArchive.inputStream())
assertEquals(2, verified.snapshot.rows.size)
assertEquals(-67, verified.snapshot.rows.first().rssi)
assertArrayEquals(originalPhoto, verified.snapshot.photos.first().bytes)
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:testDebugUnitTest --tests "com.example.ProjectArchiveCodecTest"`; expected compilation failure or test failure before implementation.
- [ ] **Step 3: Implement stream encoder and verification.** Use `ZipOutputStream`/`ZipInputStream` plus JDK `MessageDigest(SHA-256)`, finite-number validation and overflow-safe byte counters; decode into verified memory-bounded snapshot only after every entry matches manifest. No external URI or network access.
- [ ] **Step 4: Verify green.** Run focused test and full `:app:testDebugUnitTest`. Expected exact round-trip and rejection cases PASS; no previous tests regress.
- [ ] **Step 5: Commit** codec and test: `git add app/src/main/java/com/example/backup/ProjectArchiveCodec.kt app/src/test/java/com/example/ProjectArchiveCodecTest.kt && git commit -m "feat(backup): add integrity-checked portable archive v1"`.


## Task 4: User-controlled backup export with truthful SAF results

**Files:**
- Create: `app/src/main/java/com/example/backup/ProjectExportCoordinator.kt`
- Create: `app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt`
- Modify: `app/src/main/java/com/example/ui/WifiViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/RadioOperationsScreen.kt`
- Test: `app/src/test/java/com/example/ProjectExportCoordinatorTest.kt`
- UI instrumentation later: `app/src/androidTest/java/com/example/BackupRecoveryUiTest.kt` (Task 12).

**Interfaces:**
- `sealed interface ExportOutcome { data class Verified(val sha256: String, val rowCount: Int): ExportOutcome; data class Unverified(val reason: String): ExportOutcome; data class Failed(val reason: String): ExportOutcome }`.
- `class ProjectExportCoordinator(private val context: Context, private val reader: LegacySnapshotReader) { suspend fun exportTo(uri: Uri): ExportOutcome }`; internally delegates to `ProjectArchiveCodec.write` and `verify`.
- Compose `BackupRecoveryScreen(viewModel: WifiViewModel, onClose: () -> Unit)` with `data_safety_screen`, `export_project_button`, `export_warning` and `export_verdict` test tags; the entry under Audit uses `open_data_safety_button`. A `CreateDocument("application/zip")` launcher receives the chosen destination URI and never asks for raw filesystem permissions.
- `WifiViewModel.exportProject(uri: Uri, callback: (ExportOutcome) -> Unit)` dispatches off main thread; callbacks complete after durability/integrity validation, not on picker selection.

- [ ] **Step 1: Write failing coordinator tests** `exportCreatesVerifiableArchiveWithAllLayouts`, `documentProviderNullStreamDoesNotClaimSuccess`, `documentProviderThrowsHalfwayNeverClaimsVerified`, `exportedBackupDoesNotChangeSourceSurveysOrPlan`. Use a fake ContentResolver/document backend for null/exception cases, two layout IDs and a plan JPEG.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
val result = coordinator.exportTo(uriWithNullOutputStream)
assertTrue(result is ExportOutcome.Failed)
assertEquals(2, dao.countAll()) // source records are untouched
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:testDebugUnitTest --tests "com.example.ProjectExportCoordinatorTest"`. Expected FAIL for missing coordinator.
- [ ] **Step 3: Implement coordinator and minimal Data Safety screen under Audit.** Display “Backup includes Wi-Fi names, physical map and photos” with explicit confirmation, invoke native CreateDocument, write on `Dispatchers.IO`, reopen destination when the provider allows, validate manifest/digest, and report `Verified` only after successful readback. If destination is not readable back, use `Unverified` and explain the limitation; if a partial file exists, report failure and never touch live source data. Keep Audit/Coach/Doctor existing tabs and navigation. No advanced redaction UI yet (Phase D).
- [ ] **Step 4: Verify green.** Run focused and full unit tests, then `gradle --no-daemon :app:assembleDebug`. Expected PASS; UI provides accessible backup entry and no new runtime permission.
- [ ] **Step 5: Commit** listed source/test files: `git add app/src/main/java/com/example/backup/ProjectExportCoordinator.kt app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt app/src/main/java/com/example/ui/WifiViewModel.kt app/src/main/java/com/example/ui/screens/RadioOperationsScreen.kt app/src/test/java/com/example/ProjectExportCoordinatorTest.kt && git commit -m "feat(backup): add user-controlled verified project export"`.

## Task 5: Verify imported archives into quarantine without touching live data

**Files:**
- Create: `app/src/main/java/com/example/backup/ProjectImportVerifier.kt`
- Modify: `app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt`
- Modify: `app/src/main/java/com/example/ui/WifiViewModel.kt`
- Test: `app/src/test/java/com/example/ProjectImportVerifierTest.kt`

**Interfaces:**
- `sealed interface StageResult { data class Ready(val candidate: ImportCandidate): StageResult; data class Blocked(val code: String, val reason: String): StageResult }`.
- `ImportCandidate(id: String, archiveSha256: String, pathInPrivateStaging: String, sourcePackage: String, rowCount: Int, photoCount: Int, planStatus: String)`. File paths are internal relative identifiers; never accept arbitrary paths from the ZIP manifest.
- `class ProjectImportVerifier(private val context: Context) { suspend fun stage(uri: Uri): StageResult; suspend fun inspectCandidate(id: String): StageResult }`.
- Storage admission threshold exactly `requiredFreeBytes = 2 * expectedTemporaryBytes + 100L * 1024 * 1024`, with overflow checks and separate non-mutating space query. Maximum encoded ZIP 64 MiB and decoded 128 MiB carry forward from Task 3.
- Stage path `filesDir/import-staging/<opaque-UUID>/source.zip` plus manifest receipt. Existing `wifi_survey.db` and `floorplan_studio_v1` remain untouched.

- [ ] **Step 1: Write failing tests** `validArchiveStagedWithoutChangingActiveData`, `tamperedOrDuplicatedEntriesRejectBeforeWrite`, `unknownSchemaOrMaliciousPathFailsClosed`, `insufficientFreeSpaceLeavesNoNewStagingOrDataWrites`, `unreadableUriReportsBlocked`. Assert no changes to live survey counts/preference/photo hashes.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
val result = verifier.stage(uriWithTamperedHash)
assertTrue(result is StageResult.Blocked)
assertEquals(originalSurveyCount, dao.countAll())
assertEquals(originalRawPlan, store.rawDocumentForBackup())
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:testDebugUnitTest --tests "com.example.ProjectImportVerifierTest"`. Expected FAIL for missing types/functions.
- [ ] **Step 3: Implement staged verification only.** Use SAF `OpenDocument()`, verify all ZIP entries/hashes before marking candidate READY, store a read-only receipt, and show origin app/source row count, plan/photo status and sensitive-data warning. A repeated exact archive may return the same candidate ID; it must never create a duplicate applied dataset. Do not add a “Replace current project” shortcut.
- [ ] **Step 4: Verify green.** Run focused/full unit tests. Expected stage/inspect tests PASS; old app data byte hashes remain unchanged.
- [ ] **Step 5: Commit** candidate verifier/UI/test: `git add app/src/main/java/com/example/backup/ProjectImportVerifier.kt app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt app/src/main/java/com/example/ui/WifiViewModel.kt app/src/test/java/com/example/ProjectImportVerifierTest.kt && git commit -m "feat(backup): quarantine and inspect imported projects"`.

## Task 6: Safe startup and corrupt-data recovery without a crash loop

**Files:**
- Create: `app/src/main/java/com/example/data/DataStartupState.kt`
- Create: `app/src/main/java/com/example/data/DataStartupCoordinator.kt`
- Modify: `app/src/main/java/com/example/data/FloorPlanStore.kt`
- Modify: `app/src/main/java/com/example/data/AppDatabase.kt` (remove destructive fallback before any version increase)
- Modify: `app/src/main/java/com/example/ui/WifiViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/MainNavigation.kt`
- Modify: `app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt`
- Test: `app/src/test/java/com/example/DataStartupRecoveryTest.kt`

**Interfaces:**
- `sealed interface StoredPlanState { data object Absent; data class Loaded(val plan: FloorPlanDocument); data class Corrupt(val reason: String, val rawSha256: String) }`.
- `FloorPlanStore.inspect(): StoredPlanState`; existing `load(): FloorPlanDocument?` is kept as a compatibility adapter and never clears raw corrupt preferences. Preserve `rawDocumentForBackup()` from Task 2.
- `sealed interface DataStartupState { data object Loading; data object Ready; data class Recovery(val code: String, val affected: Set<String>) }`.
- `class DataStartupCoordinator(private val context: Context, private val store: FloorPlanStore) { suspend fun inspect(): DataStartupState }`.
- `WifiViewModel.dataStartupState: StateFlow<DataStartupState>`; move eager Room-opening/DAO access behind a recoverable initialization boundary. No call to `fallbackToDestructiveMigration()` to work around a failure. `MainAppScreen` renders an accessible recovery surface while the usable parts of the app continue where safe.

- [ ] **Step 1: Write failing Robolectric tests** `truncatedPlanJsonPreservesOriginalPreferenceValue`, `missingPhotoShowsRelinkWarningWhileKeepingGeometry`, `databaseOpenFailureShowsReadOnlyRecoveryNotActivityCrash`, `healthyLegacyPlanLoadsWithSameNameAndCorners`. Test that a provider or SQLite exception does not trigger `clear()`, new default values over old prefs, or deleted DB files.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
val originalRaw = "{not valid json"
prefs.edit().putString("document", originalRaw).commit()
assertTrue(store.inspect() is StoredPlanState.Corrupt)
assertEquals(originalRaw, store.rawDocumentForBackup())
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:testDebugUnitTest --tests "com.example.DataStartupRecoveryTest"`. Expected FAIL while `inspect`/state types absent.
- [ ] **Step 3: Implement recoverable load boundary.** Separate preference decoding from data deletion; **remove `fallbackToDestructiveMigration()` in `AppDatabase.kt` now, before changing schema versions**; classify failures, preserve raw JSON, initialize DB only through an error-capturing DAO provider on IO, show `data_recovery_mode` and `view_backup_options` semantics, and allow safe export of readable data without attempting unsupported schema repair. An absent plan is not itself an error. No feature tabs or sample rows may be silently seeded as a “repair.”
- [ ] **Step 4: Verify green.** Run focused unit test, `FloorPlanStoreTest` and full suite; then compile APK. Expected PASS, with corrupt/missing data surfaced rather than discarded.
- [ ] **Step 5: Commit** explicit recovery boundary: `git add app/src/main/java/com/example/data/DataStartupState.kt app/src/main/java/com/example/data/DataStartupCoordinator.kt app/src/main/java/com/example/data/FloorPlanStore.kt app/src/main/java/com/example/data/AppDatabase.kt app/src/main/java/com/example/ui/WifiViewModel.kt app/src/main/java/com/example/ui/MainNavigation.kt app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt app/src/test/java/com/example/DataStartupRecoveryTest.kt && git commit -m "fix(storage): preserve corrupt data and show read-only recovery"`.

## Task 7: Capture authentic Room v1 schema and add explicit v1→v2 receipt migration

**Files:**
- Modify: `app/src/main/java/com/example/data/AppDatabase.kt`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/data/ImportReceiptEntity.kt`
- Create: `app/src/main/java/com/example/data/ImportReceiptDao.kt`
- Create: `app/src/main/java/com/example/data/migrations/Migrations.kt`
- Create/generated and commit: `app/schemas/com.example.data.AppDatabase/1.json` and `app/schemas/com.example.data.AppDatabase/2.json`
- Create: `app/src/androidTest/java/com/example/Migration1To2Test.kt`
- Test additions: `androidTestImplementation("androidx.room:room-testing:2.8.4")`, `androidTestImplementation("androidx.test.ext:junit:1.3.0")`, `androidTestImplementation("androidx.test:runner:1.7.0")`, `androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.01"))`, `androidTestImplementation("androidx.compose.ui:ui-test-junit4")`, and `debugImplementation("androidx.compose.ui:ui-test-manifest")` in `app/build.gradle.kts`. Keep unit-test dependencies unchanged.

**Interfaces:**
- `@Entity(tableName = "import_receipts") data class ImportReceiptEntity(@PrimaryKey val archiveSha256: String, val sourcePackage: String, val importedAtMillis: Long, val surveyRowCount: Int)`.
- `@Dao interface ImportReceiptDao { @Query("SELECT * FROM import_receipts WHERE archiveSha256 = :hash LIMIT 1") suspend fun find(hash: String): ImportReceiptEntity?; @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(receipt: ImportReceiptEntity) }`.
- `object DbMigrations { val MIGRATION_1_2: Migration }` using the Room 2.8.4 supported SQLite migration API; exact table schema must match generated v2 Room schema.
- `@Database(entities = [SurveyEntity::class, ImportReceiptEntity::class], version = 2, exportSchema = true)`; `AppDatabase.importReceiptDao(): ImportReceiptDao`; register `addMigrations(DbMigrations.MIGRATION_1_2)`. **Preserve the earlier Task 6 removal of `fallbackToDestructiveMigration()`; never reinstate it.**
- Configure KSP `room.schemaLocation` into `$projectDir/schemas` in module Gradle. Add `androidx.room:room-testing:2.8.4` and AndroidX instrumentation/JUnit dependencies in `androidTestImplementation`.

- [ ] **Step 1: Assert v1 exported baseline doesn't yet exist, then capture genuine v1 schema.** With DB annotated `version=1, exportSchema=true` and KSP configured, run `gradle --no-daemon :app:kspDebugKotlin`; verify `test -s app/schemas/com.example.data.AppDatabase/1.json`. This schema MUST be generated from code representing actual installed v1 tables; do not hand-write a synthetic v1 schema.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
val migrated = helper.runMigrationsAndValidate(
    "migration-plan-a", 2, true, DbMigrations.MIGRATION_1_2)
migrated.query("SELECT rssi, timestamp FROM survey_points WHERE id = 17").use { c ->
    assertTrue(c.moveToFirst())
    assertEquals(-67, c.getInt(0))
    assertEquals(1800000000000L, c.getLong(1))
}
```

- [ ] **Step 2: Write failing migration instrumentation test** `migration1To2RetainsAllSurveysExact` using `MigrationTestHelper`, inserting v1 rows with contrasting layoutId, nullable-like notes, negative dBm, distinct timestamps, then asserting byte/equality of all original row values after migrating. Add `missingMigrationFailsWithoutWiping` and `newReceiptTableIsEmptyAndValid`. The pre-implementation test cannot pass until version2/migration exist.
- [ ] **Step 3: Verify red.** Run instrumentation under a running Android emulator with `gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.Migration1To2Test`. Expected compile/test failure before creating `DbMigrations`/`ImportReceiptEntity`.
- [ ] **Step 4: Implement minimal explicit migration.** Create only `import_receipts`, leave `survey_points` unchanged, set Room v2 and add migration. Generate and commit actual `2.json`. Keep no fallback. Import receipt must be merely metadata, not a license to overwrite unrelated data.
- [ ] **Step 5: Verify green.** Re-run `:app:connectedDebugAndroidTest` on at least API 35; expected all migration assertions PASS. Run `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug` and `grep -R 'fallbackToDestructiveMigration' app/src/main/java`; expected no match. Unsupported future schema version must use recovery, not a wipe.
- [ ] **Step 6: Commit** exact DB/schema/test paths: `git add app/build.gradle.kts app/src/main/java/com/example/data/AppDatabase.kt app/src/main/java/com/example/data/ImportReceiptEntity.kt app/src/main/java/com/example/data/ImportReceiptDao.kt app/src/main/java/com/example/data/migrations/Migrations.kt app/schemas app/src/androidTest/java/com/example/Migration1To2Test.kt && git commit -m "feat(storage): explicit non-destructive Room v1-to-v2 migration"`.

## Task 8: Promote a staged backup into a truly empty app with crash-safe journal

**Files:**
- Create: `app/src/main/java/com/example/backup/RestoreJournal.kt`
- Create: `app/src/main/java/com/example/backup/ProjectRestoreCoordinator.kt`
- Modify: `app/src/main/java/com/example/data/SurveyDao.kt`
- Modify: `app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt`
- Modify: `app/src/main/java/com/example/ui/WifiViewModel.kt`
- Modify: `app/src/main/java/com/example/data/DataStartupCoordinator.kt` (reconcile interrupted journal before normal UI)
- Test: `app/src/test/java/com/example/ProjectRestoreCoordinatorTest.kt`

**Interfaces:**
- `enum class RestorePhase { PREPARED, DB_WRITTEN, PLAN_COMMITTED, DONE }`.
- `class RestoreJournal(private val context: Context) { fun read(candidateId: String): RestorePhase?; fun mark(candidateId: String, phase: RestorePhase): Boolean }`. Use durable file + fsync/rename where platform supports it; never delete live originals as a “rollback.”
- `sealed interface RestoreOutcome { data class Restored(val archiveSha256: String, val rows: Int): RestoreOutcome; data class Blocked(val code: String, val message: String): RestoreOutcome }`.
- `class ProjectRestoreCoordinator(private val context: Context, private val db: AppDatabase, private val store: FloorPlanStore, private val verifier: ProjectImportVerifier) { suspend fun promoteIntoEmptyTarget(candidateId: String, explicitUserConfirmation: Boolean): RestoreOutcome; suspend fun recoverInterruptedRestore(): RestoreOutcome }`.
- “Empty target” means **no plan preference value, zero survey rows for all layouts, no existing private `plan_photo_*.jpg`, and no conflicting import receipt**. An empty plan with survey rows is **OCCUPIED**. The source ZIP stays staged until full post-restore readback.
- Add `@Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRestored(points: List<SurveyEntity>)` to SurveyDao; existing `REPLACE`-based ordinary pin insert is not used for archive restore. Never silently overwrite colliding `id`.

- [ ] **Step 1: Write failing tests** `occupiedBySurveysEvenWhenPlanMissingBlocksPromotion`, `twoImportsSameArchiveRemainIdempotent`, `restorePreservesAllIdsAndPhotoSha`, `writeInterruptedAtEachJournalPhaseRecovers`, `crashBetweenDbCommitAndJournalWriteCannotDuplicateRows`, `userRefusesConfirmationCausesZeroWrites`, `lowSpaceRefusesBeforeCommit`. Assert original active app data hashes never change when target is occupied.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
// Plan preference is absent, but one Room row remains.
val verdict = coordinator.promoteIntoEmptyTarget(candidateId, true)
assertEquals("OCCUPIED", (verdict as RestoreOutcome.Blocked).code)
assertEquals(1, dao.countAll())
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:testDebugUnitTest --tests "com.example.ProjectRestoreCoordinatorTest"`. Expected FAIL before coordinator exists.
- [ ] **Step 3: Implement guarded promotion and recovery.** Reverify archive hash/entry sizes at promotion time, stage images with unique private names and map references, write durable journal, commit exact survey rows plus receipt in one Room transaction, persist plan preferences, mark `DONE` only after readback counts/hashes match. **Before retrying a DB insert, read the receipt inside a transaction:** a crash after DB commit but before updating the journal must resume from that durable receipt instead of duplicating rows. Keep photo files staged until promotion and reconcile final names via the journal. Failure before DB commit leaves target unchanged. A persisted incomplete journal is reconciled by `DataStartupCoordinator` on next start; if ambiguous, show recovery instead of blindly retrying.
- [ ] **Step 4: Verify green.** Run focused, full unit suite and Gradle build. All injected power-loss points and occupied-data tests PASS, including reuse of the same import in a second process instance.
- [ ] **Step 5: Commit** restoration owner and tests: `git add app/src/main/java/com/example/backup/RestoreJournal.kt app/src/main/java/com/example/backup/ProjectRestoreCoordinator.kt app/src/main/java/com/example/data/SurveyDao.kt app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt app/src/main/java/com/example/ui/WifiViewModel.kt app/src/main/java/com/example/data/DataStartupCoordinator.kt app/src/test/java/com/example/ProjectRestoreCoordinatorTest.kt && git commit -m "feat(restore): recoverable empty-target import with receipt"`.

**Critical boundary:** This first-phase restore is a recovery route for a genuinely empty **separate** app installation. An already populated target keeps a verified staged candidate and displays “Import blocked: existing data.” Phase B introduces multi-project switching; Phase A must not simulate it by replacing the single active plan. If a legacy app cannot export and has an unrelated signing key, migrating it remains **BLOCKED**, not solved by the existence of an importer.


## Task 9: Honest hardware-readiness panel (diagnostic, not Phase C provenance)

**Files:**
- Create: `app/src/main/java/com/example/service/HardwareReadiness.kt`
- Create: `app/src/main/java/com/example/ui/components/HardwareReadinessPanel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/HeatmapScreen.kt`
- Modify: `app/src/main/java/com/example/ui/WifiViewModel.kt`
- Test: `app/src/test/java/com/example/HardwareReadinessTest.kt`

**Interfaces:**
- `data class RadioReadinessInputs(val wifiHardwarePresent: Boolean, val wifiEnabled: Boolean, val fineLocationGranted: Boolean, val locationServiceEnabled: Boolean, val foregroundActive: Boolean, val simulated: Boolean, val connected: WifiNetwork?, val scanAgeMillis: Long?, val scanThrottled: Boolean)`.
- `enum class ReadinessIssue { NO_RADIO, WIFI_DISABLED, PERMISSION_DENIED, LOCATION_DISABLED, BACKGROUND, SIMULATION, DISCONNECTED, STALE_SCAN, AGE_UNKNOWN, THROTTLED, BSSID_REDACTED }`.
- `data class RadioReadinessReport(val canAttemptLegacyHardwareCapture: Boolean, val issues: List<ReadinessIssue>, val explanation: String)`.
- `object HardwareReadiness { fun evaluate(input: RadioReadinessInputs): RadioReadinessReport }`. No network or Room calls in the pure function.
- `HardwareReadinessPanel(report: RadioReadinessReport)` reports platform conditions and how to correct them; if Android does not expose a reliable scan age or BSSID, preserve an explicit UNKNOWN. **No “verified fresh-hardware survey” badge is permitted in Phase A**; that requires Phase C's persisted observation source metadata.

- [ ] **Step 1: Write failing tests** `simulationCannotClaimHardwareReady`, `noLocationPermissionOrServicesExplained`, `staleOrThrottledScanDoesNotClaimFreshness`, `redactedBssidRetainsIdentityWarning`, `validConnectedSnapshotDoesNotPromiseScanFreshness`. Assert `canAttemptLegacyHardwareCapture` cannot be true for demo/disconnected/no-permission states; an unknown scan timestamp is never assigned the current clock.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
val result = HardwareReadiness.evaluate(knownGoodInput.copy(simulated = true))
assertFalse(result.canAttemptLegacyHardwareCapture)
assertTrue(ReadinessIssue.SIMULATION in result.issues)
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:testDebugUnitTest --tests "com.example.HardwareReadinessTest"`. Expected FAIL due to missing model.
- [ ] **Step 3: Implement pure readiness + minimal Heatmap panel.** Adapt current `WifiScannerService` observable values and `ScanFreshness`; read `LocationManager`/`WifiManager` safely through existing Android permissions; perform no extra scan merely to refresh badges. Show actual OS data, explicit “age unknown” and the distinction between current connected snapshot and new scan event. Keep old record-button semantics conservative; don't introduce “verified fresh” until Phase C.
- [ ] **Step 4: Verify green.** Focused and full unit suite plus APK compilation. Existing startup test, Survey Coach source separation, and background scanner pause tests must remain green.
- [ ] **Step 5: Commit** new component, viewmodel/screen and tests: `git add app/src/main/java/com/example/service/HardwareReadiness.kt app/src/main/java/com/example/ui/components/HardwareReadinessPanel.kt app/src/main/java/com/example/ui/screens/HeatmapScreen.kt app/src/main/java/com/example/ui/WifiViewModel.kt app/src/test/java/com/example/HardwareReadinessTest.kt && git commit -m "feat(survey): show hardware readiness without invented freshness"`.

## Task 10: Explicit privacy-preserving Android backup policy

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/data_extraction_rules.xml`
- Create: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt` (explain privacy tradeoff)
- Test: `app/src/test/java/com/example/BackupPolicyTest.kt`
- Documentation after end-to-end verification: `README.md` (Task 12).

**Interfaces / policy decision for review:**
- Keep `android:allowBackup="true"` but apply explicit **exclude** rules to sensitive `database`, `sharedpref` and `file` domains for automatic cloud and device-to-device transfer. This prioritizes privacy and makes the explicit, checksum-verified user export from Task 4 the **portable migration route**. The previous implicit Android automatic backup of private project data is **not promised** under this new policy.
- For API 31+ reference `android:dataExtractionRules="@xml/data_extraction_rules"` with separate `<cloud-backup>` and `<device-transfer>` exclusions; for API 24–30 use `android:fullBackupContent="@xml/backup_rules"` with equivalent exclusions. Both exclude actual `wifi_survey.db`, `floorplan_studio_v1`, `plan_photo_*.jpg` and diagnostic baseline by domain scope rather than unsupported filename glob syntax.
- Never claim absolute privacy: Android/OEM transfer behavior may vary. A manual ZIP export is unencrypted unless the user later selects a separate encrypted-export feature; warn accordingly.

- [ ] **Step 1: Write failing policy tests** `cloudAndDeviceTransferRulesExplicitlyExcludeSensitiveDomains`, `pre31RulesExcludeSameProjectData`, `manifestDeclaresBothPolicyFiles`, `manualSAFExportRemainsAvailable`. Parse XML resources in Robolectric; assert no accidental backup include rule for private database/prefs/photo.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
assertEquals(setOf("database", "sharedpref", "file"), cloudExcludedDomains)
assertEquals(setOf("database", "sharedpref", "file"), transferExcludedDomains)
assertTrue(manifestHasDataExtractionRules && manifestHasFullBackupContent)
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:testDebugUnitTest --tests "com.example.BackupPolicyTest"`. Expected FAIL due to missing resources.
- [ ] **Step 3: Implement manifest and two XML policies.** Preserve FileProvider, camera, Wi-Fi permissions, existing app theme, and package identities. Explain on Data Safety screen that system backups intentionally exclude sensitive content and manual verified export is required to move to another device/app identity.
- [ ] **Step 4: Verify green.** Run focused test and full `:app:testDebugUnitTest :app:assembleDebug`. Test the generated merged manifest for both debug/other supported variants; never assume that `allowBackup=false` alone prevents OEM device transfer.
- [ ] **Step 5: Commit** policy/test: `git add app/src/main/AndroidManifest.xml app/src/main/res/xml/data_extraction_rules.xml app/src/main/res/xml/backup_rules.xml app/src/test/java/com/example/BackupPolicyTest.kt app/src/main/java/com/example/ui/screens/BackupRecoveryScreen.kt && git commit -m "fix(privacy): define explicit automatic backup boundaries"`.

## Task 11: Fail-closed stable-release artifact gate and signing handoff

**Files:**
- Create: `tools/release/release_gate.py`
- Create: `tools/release/tests/test_release_gate.py`
- Create: `ci/release-verify.sh`
- Modify: `app/build.gradle.kts` (only the release signing/property contract; never modify debug package suffix)
- Modify: `.github/workflows/android.yml` (manual protected release job, disabled while identity/credentials unknown)
- Documentation: `docs/release/signing-custody.md` (public non-secret procedure).

**Interfaces:**
- `ReleaseExpected(package_id: str, min_version_code: int, cert_sha256: str)` and `ReleaseObserved(package_id: str, version_code: int, cert_sha256: str, apk_sha256: str)`.
- `verify_release(expected: ReleaseExpected, observed: ReleaseObserved) -> ReleaseVerdict`, statuses `PASS`, `BLOCKED_IDENTITY`, `BLOCKED_SIGNER`, `BLOCKED_VERSION` and `BLOCKED_UNKNOWN`.
- `python3 tools/release/release_gate.py verify --apk APK --expected-id ID --expected-sha256-fingerprint FINGERPRINT --min-version VERSION` parses `aapt`/`apksigner` and returns nonzero on any missing/invalid identity or unreadable tool output.
- Release CI must require a protected environment `dexwifi-release` and secrets referenced only by environment, e.g. `DEX_WIFI_RELEASE_KEYSTORE_B64`, `DEX_WIFI_RELEASE_STORE_PASSWORD`, `DEX_WIFI_RELEASE_KEY_ALIAS`, `DEX_WIFI_RELEASE_KEY_PASSWORD` and separately pinned non-secret expected fingerprint/applicationId/version. **No actual secret creation, key rotation or user selection occurs during this plan.**

- [ ] **Step 1: Write failing tests** `matchingCertAndIncreasingVersionAllowsCandidate`, `missingInstalledOrExpectedCertificateBlocks`, `wrongAppIdBlocks`, `olderOrEqualVersionBlocks`, `signerOutputWithNoFingerprintBlocks`, `stderrAndReportsNeverEchoSecretEnvironmentValues`. Exercise a mocked `apksigner` output, not a known-real certificate.

**Representative red-test assertions** (create the fixtures named above):

```python
expected = ReleaseExpected("com.example.floorplan", 4, "aa" * 32)
observed = ReleaseObserved("com.example.floorplan", 5, "bb" * 32, "cc" * 32)
self.assertEqual("BLOCKED_SIGNER", verify_release(expected, observed).status)
```

- [ ] **Step 2: Verify red.** Run `python3 -m unittest discover -s tools/release/tests -p 'test_release_gate.py' -v`. Expected FAIL for unresolved verifier.
- [ ] **Step 3: Implement read-only artifact verifier and gated workflow.** Replace the current release keystore fallback `my-upload-key.jks` with explicit protected inputs for release tasks; keep `assembleDebug` functioning without secrets. Require manual `workflow_dispatch` plus approved protected environment and enable flag; **disabled** until user has reviewed installed identity/target package and stored a persistent recoverable key. Never publish an APK from a job that lacks a verified signing fingerprint. Keep `com.example.floorplan` only as preview/debug and do not silently ship `com.example` as a “new stable channel.”
- [ ] **Step 4: Verify green.** Run the Python verifier tests and `gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest`. Dry-run the release gate with missing env and a deliberately mismatched certificate; both must exit nonzero. `apksigner verify --print-certs` on any candidate is a requirement before release, but this task does not fabricate/provision one.
- [ ] **Step 5: Commit** verifier/gating/docs only: `git add tools/release/release_gate.py tools/release/tests/test_release_gate.py ci/release-verify.sh app/build.gradle.kts .github/workflows/android.yml docs/release/signing-custody.md && git commit -m "ci(release): block unsigned or mismatched Dex_WIFI releases"`.

**Manual decision gate before an actual signed release:** Which installed source identity and exact signer are recoverable? What future application ID is acceptable? Who holds the durable keystore backup and protected CI authority? Until resolved, mark **RELEASE_BLOCKED**, even if debug CI is green. Do not ask for credentials in chat.

## Task 12: End-to-end data safety proofs and production handoff

**Files:**
- Modify: `ci/smoke-launch.sh`
- Modify: `.github/workflows/android.yml`
- Create: `app/src/androidTest/java/com/example/BackupRecoveryUiTest.kt`
- Create: `docs/release/phase-a-acceptance.md`
- Modify: `README.md`
- Append: `Dex_WIFI_bible.md` once results actually exist.

**Interfaces:**
- CI test inventory: `unit-v1-snapshot`, `unit-archive-adversarial`, `unit-startup-recovery`, `android-migration-1-to-2`, `api35-smoke`, `api36-smoke`, `samsung-tablet-hardware-gate`.
- Acceptance states: `PLANNED`, `IMPLEMENTED_UNVERIFIED`, `EMULATOR_VERIFIED`, `TABLET_VERIFIED`, `BLOCKED`, `FAILED`, `SUPERSEDED`.
- `phase-a-acceptance.md` holds actual commit SHA, per-task commit refs, actual workflow URL/status, archive hashes and row equality receipts, APK hash/version/signature when released, exact physical tablet identity/space evidence if available, and **clear missing evidence** otherwise. Never put Wi-Fi passwords, full SSIDs/BSSIDs, personal photo bytes or app secrets into public CI logs.

- [ ] **Step 1: Write failing user-journey tests** `dataSafetyEntryVisibleUnderAudit`, `exportCancelPreservesCurrentProject`, `invalidImportShowsBlockedNotSuccess`, `recoverySurfaceAccessibleAfterCorruption`, `existingOtherTabsStillNavigate`. Include real semantic test tags for export/import/recovery. Add a persistent fixture that saves a legacy map and two survey rows, force-stops/restarts the app and verifies unchanged counts.

**Representative red-test assertions** (create the fixtures named above):

```kotlin
composeRule.onNodeWithTag("nav_item_audit").performClick()
composeRule.onNodeWithTag("open_data_safety_button").performClick()
composeRule.onNodeWithTag("data_safety_screen").assertExists()
composeRule.onNodeWithTag("export_project_button").assertExists()
```

- [ ] **Step 2: Verify red.** Run `gradle --no-daemon :app:connectedDebugAndroidTest` under emulator and `bash ci/smoke-launch.sh` via the existing emulator-runner harness. Expected at least one new flow unimplemented/unrecognized before integration.
- [ ] **Step 3: Extend real smoke and CI.** Keep existing Networks→Heatmap→Floor Plan→Audit→Survey Coach→Connection Doctor checks. Add Data Safety navigation, cancellation, recovery UI, import read-only staging and record persistence. On API 35 **and** 36 run `:app:connectedDebugAndroidTest` for the Room migration and UI suite; on a representative device restore from an actual archive, then relaunch and compare counts/hashes. Store only redacted proof outputs.
- [ ] **Step 4: Verify green in CI.** Execute `gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest`, then emulator CI/API 35+36 connected tests. Link actual run IDs. Require all new and existing suites to pass; if Android emulators cannot test a camera or Wi-Fi hardware path, mark **NOT_VERIFIED**, not PASS.
- [ ] **Step 5: Perform gated physical acceptance only when explicitly authorized and reachable.** Read-only ADB identity/storage preflight first; do not install until data export, signer and space requirements are met. Verify installed app package, signature and real saved-plan import on Samsung tablet. Exercise S Pen/finger, camera/SAF, Wi-Fi/Location permission, cold start/resume, and user-owned data retention. If ADB is offline or storage low, **STOP as BLOCKED**, preserve source/CI artifact and do not remove any user data.
- [ ] **Step 6: Document actual evidence and limits.** Append to README usage instructions and `Dex_WIFI_bible.md` only after verification. Create `docs/release/phase-a-acceptance.md` with PASS/FAIL/BLOCKED per gate and clear artifact identity. Never claim an APK shipped if only CI ran.
- [ ] **Step 7: Commit** only tested CI/docs/test surfaces: `git add ci/smoke-launch.sh .github/workflows/android.yml app/src/androidTest/java/com/example/BackupRecoveryUiTest.kt docs/release/phase-a-acceptance.md README.md Dex_WIFI_bible.md && git commit -m "test(release): prove Dex_WIFI Phase A recovery and launch paths"`.

## Completion contract: when Phase A is actually done

### Required PASS before declaring application changes ready for user acceptance

- T1–T12 tasks' focused tests pass, with no unresolved mandatory regression.
- The prior `fallbackToDestructiveMigration()` is absent from executable Room startup; v1 schema fixture and v1→v2 explicit migration tests pass; every original survey column/value is preserved exactly.
- Source backup ZIP includes **all** layouts, raw plan JSON, needed private photos, integrity receipts, and exposes no raw credential material. ZIP import refuses unsafe entries, low space, duplicate receipt, occupied target and unverified archive.
- Corrupt plan, missing image and incompatible DB yield recoverable UI, not a crash or stealth reset.
- Manual and automatic backup behavior is accurately documented; upload/cloud transfer is not implied by a local export.
- Existing Networks, Channels, Optimizer, Heatmap, Audit, Survey Coach, Connection Doctor and manual router browser workflow remain available.
- CI compiles/tests on supported Android API 35 and 36, replays the actual save/restore user path, checks process survival after foreground/background transition, and records results by source commit.

### Release/installation hold (G1 hard gate)

A **source-complete but physically BLOCKED** outcome is honest and acceptable if the user's tablet, source app or signing key remains inaccessible. It is **not a release**. Before a stable release or any migration-driven deletion, verify user-approved final applicationId, recoverable stable signer, real tablet identity/free space, usable backup/export and real-device restore. Never automatically uninstall the original.

**Rollback contract:** Every implementation task is a separate commit; use an isolated worktree and reviewable PR. Revert only authorized feature commits if a test fails. Do not revert a Room schema bump on a device that has already opened v2 DB without a verified recovery/export path. Rollback at the app binary layer requires compatible signer, correct version policy and proof of data readability; never presume “install older APK” restores a migrated database.

## Plan self-review checklist for implementing agent

- [ ] Re-read the approved specification, Task 1 identity constraints, active repository/Operational State and current HEAD before editing.
- [ ] Verify every created class/function in a later task is declared in an earlier task's Interfaces block; avoid changing names without plan revision.
- [ ] Demonstrate five Review Focus tests; all have explicit owning tasks above.
- [ ] No tasks require deleting user-owned source content. Any staging cleanup is limited to verified disposable temporary data.
- [ ] Run each test RED before code and GREEN afterward; quote exact test output in task receipt, do not invent PASS.
- [ ] Before each commit, inspect `git diff --check`, staged filenames and any unrelated changes; stop if scope expands.
- [ ] Do not commit secrets, sample real SSIDs/BSSIDs, exported apartment photos, APK keystores, device serials or full diagnostic logs.
- [ ] After final gates, independently verify Git changeset touches only authorized files. Do not merge PR #2 merely because this phase is finished.
- [ ] If the release signing/installed-data identity decision is unresolved, mark it `BLOCKED` and ask for the narrow decision; do not silently select one.
- [ ] Only after the written plan has been approved and the execution method selected should `superpowers:subagent-driven-development` or `superpowers:executing-plans` begin.

## Execution handoff (user review required)

This plan is intentionally **not executed** by its creation. The user must review/approve the document and select an execution method:

- **Subagent-driven:** one implementer and one reviewer per task, then independent branch review. Recommended for risky, interdependent data migration and signing work.
- **Native:** implement sequentially in the active execution session, run the same tests/gates, and obtain one independent review at the branch boundary. Less overhead but less isolated review per task.

**Recommended: Subagent-driven** where the runner supports it, because mistakes in legacy data conversion, Android signatures or import journals could permanently lose user work. If the chosen runtime cannot launch subagents, disclose that limitation and use the approved Native option rather than pretending to have run reviewers.

**Stop here for review.** Neither “Approved” for the design specification nor creation of this plan authorizes coding, provisioning credentials, installing an APK or pushing an implementation branch.
