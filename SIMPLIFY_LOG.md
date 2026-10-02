# Simplify log (branch simplify-overnight)

## Summary (2026-10-02)

Stopped at a natural point: the whole-file, Koin, resource, dependency and declaration passes have converged, and
the last rounds were finding a few hundred lines each.

| Module | Before | After | Change |
|---|---|---|---|
| homebase-notifshared | 314 | 301 | −13 |
| homebase-api | 77,840 | 65,897 | −11,943 |
| homebase-common | 50,541 | 13,657 | −36,884 (−73%) |
| homebase-auth | 3,440 | 3,430 | −10 |
| audio-app | 10,058 | 10,053 | −5 |
| androidApp / desktopApp / baselineprofile | 587 | 587 | 0 |
| **Kotlin total** | **142,780** | **93,925** | **−48,855 (−34%)** |

Also removed: 3,825 string/plural resources (2,163 in homebase-common plus their Danish copies), 89 resource files,
42 dependency declarations, 90 version-catalog entries, 5 unused root plugins. Branch diff: 743 files,
+250 / −53,683 (the additions are this log, two small app helpers, explicit dependency lines, one rewritten test
provider). Release APK 112.9 MB → 111.5 MB. 13 commits, all local.

Verification at the end: gate --apps exit 0 (JVM, Android, iOS-simulator compiles of main and tests; all jvmTests;
debug APK; desktop distributable), assembleRelease exit 0 with R8 reporting no missing classes, release APK launched
on the emulator to the login screen with no crash, liveTest 10/10 against the real server (also 10/10 at two earlier
milestones), desktop distributable launched cleanly after the dependency removal.

Behaviour changes (all intentional, all logged below):
- Chat-only manifest entries are gone from the app: a boot receiver that could never fire (no
  RECEIVE_BOOT_COMPLETED), a location-updates receiver, a Thunderbird package query. The biometric library's
  USE_BIOMETRIC/USE_FINGERPRINT permissions disappear with it; Firebase Crashlytics (which never initialised) no
  longer tries to start.
- TrackImporter's queue save now reads its state under the write lock (the race fixed earlier in ListeningHistory).

Suspected dead, kept on purpose:
- The video pipeline and FFmpeg-kit (≈40% of the release APK's native libraries). It stays reachable only through
  `PayloadFile.videoQuality`, a field of a @Serializable class persisted in the outbox; removing it changes a
  serialized format, which this run was told not to do. Worth a deliberate decision.
- Coil network/svg/gif/video add-ons (runtime ServiceLoader discovery; avatars may be SVG/GIF).
- `StartupCacheAudit` (created at start on purpose), peer websockets, outbox, contacts and notification services
  still wired into live code (AuthConnectionCoordinator, DriveFileProvider, LoginViewModel): removing them changes
  runtime behaviour, not just code size.
- About 180 candidate declarations the scripts couldn't cut cleanly, and English-word string names (`settings`,
  `delete`, …) that the conservative word check keeps.
- Class-level KDoc in audio-app: each explains a non-obvious why, as CLAUDE.md allows.

Not done: `/simplify` itself — it is not invocable from this session; each batch got the equivalent review by
hand. Run `/simplify` over `git diff main...simplify-overnight` before merging.

Goal: smaller, simpler code with zero behaviour change. Commits are local only.

## Baseline (main @ 4da8caa)

| Module | Kotlin lines |
|---|---|
| homebase-notifshared | 314 |
| homebase-api | 77,840 |
| homebase-common | 50,541 |
| homebase-auth | 3,440 |
| audio-app | 10,058 |
| androidApp | 520 |
| desktopApp | 40 |
| baselineprofile | 27 |
| **total** | **142,780** |

## Method

Candidates come from R8: a throwaway release build without the `-keep id.homebase.api.**` rule, with
`-dontoptimize -printusage` (rules file restored afterwards, never committed). A file is a candidate when every class
and file facade it compiles to is in the usage report. R8 sees only Android sources, so every batch is verified by the
gate (JVM + Android + iOS-simulator compiles, all jvmTests, debug APK, desktop distributable) and `assembleRelease`.
Code that only desktop or iOS uses shows up as a compile error and is kept.

R8 alone over-reported (it can't see jvmMain/nativeMain, and library-manifest components keep partial files alive),
so whole-file candidates come from a source-level reachability pass (/tmp/reach.py, not committed): files are nodes,
A→B when A mentions a top-level name B declares (name-based, so collisions only keep extra code alive), expect and
actuals share names so they live or die together, roots = audio-app/androidApp/desktopApp/baselineprofile sources plus
every name in AndroidManifest.xml, *.pro and META-INF/services files. Tests are never roots; a failing test is removed
only when everything it exercises was deleted (checked by grep of the tested symbols in main sources).

`/simplify` is not available as a skill in this session, so each batch gets the same review by hand (leftover
references in docs/config, reuse, quality) and that is recorded per commit.

## Plan

- [x] 1–2. Whole files unreachable from the app on every platform (reachability pass), all vendored modules
- [x] 3. Stricter reachability (package/import-aware) and repeat
- [x] 4. Koin definitions that nothing injects (registered but dead), then their classes
- [x] 5. Unused members inside live files (name-based fixpoint), vendored layer
- [x] 6. Unused Compose string resources (all modules, incl. values-da) and resource files
- [x] 7. Unused declarations in audio-app / androidApp / desktopApp (covered by the name pass; none found)
- [x] 8. Unused Gradle dependencies, plugins, version-catalog entries
- [x] 9. Comments violating CLAUDE.md in touched audio-app code; duplicated audio-app helpers
- [x] 10. Final review (by hand; /simplify not invocable here), gate, liveTest, summary

## Commits

### 1. Unreachable files across the vendored layer
- Removed 201 source files (camera, widgets, emoji, location/live-share, clipboard, PDF, gallery, web-drop, reactions,
  mentions, composer, notification banners, settings rows, image metadata/EXIF, waveform generators, etc.) — none
  reachable from the app on any platform — and the 51 tests that exercised only them (each tested symbol confirmed
  absent from main sources).
- Verified: all-target compile (main + tests), gate --apps exit 0, assembleRelease exit 0.
- Review: CLAUDE.md pointed at the deleted `ScrollPosition.kt` helper; kept the guidance, dropped the pointer.
- Kotlin lines 142,780 → 123,917 (−18,863).

### 2. Koin registrations nothing injects, and the code behind them
- Dropped 9 `ApiModule` definitions whose types no live code names (Follow, Mail, ConnectionNetwork,
  ConnectionRequest, ConnectionIntroduction/IntroductionSender, LiveRelay, IdentityUpgrade, LinkPreview,
  VideoPreloadService); Kotlin must name a type to inject it, so nothing could resolve them. The reachability pass
  then freed 17 files (connections, follow, mail, live relay, link preview). 4 tests covered only those.
- `ServerExceptionMessageTest` checks live error mapping and only used FollowProvider as a vehicle: rewritten
  with a minimal test-local provider; all 9 cases still pass.
- Kept: `StartupCacheAudit` (nothing injects it, but it is created at start on purpose; removing it changes startup).
- Verified: all-target compile, gate --apps exit 0.
- Kotlin lines → 121522.

### 3. Stricter reachability: names resolve through packages and imports
- The first pass matched bare words, so shared names (`TAG`, `log`, `tag`, keywords) chained unrelated files together.
  The analysis now resolves a name only within the file's package or through its imports, skips private top-level
  declarations, and takes roots only from real class names in manifests (`android:name`), keep rules and services.
  (First attempt mis-parsed `fun interface NetworkMonitor` and broke 5 live files; caught by the compile, regex fixed,
  re-run clean.)
- Removed 167 files (camera capture UI/engine, location tracking, crash reporting, main-thread watchdog, haptics,
  clipboard image paste, full-payload image cache, HomebaseImageLoader/HomebaseImage, video decoders, GIF shrinker,
  AES-GCM, vault/moments/location preferences, Thunderbird mail launch, …) and 44 tests that covered only them (no
  symbol they need remains in main sources).
- Verified: all-target compile, gate --apps exit 0, assembleRelease exit 0, release build launched on the emulator to
  the login screen with no crash.
- Kotlin lines → 101657.

### 4. Dead components in homebase-common's Android manifest, and location tracking
- The library manifest merged two location receivers and a Thunderbird `<queries>` entry into Simply Audio.
  `LocationBootReceiver` can never fire (the app doesn't hold RECEIVE_BOOT_COMPLETED); `LocationUpdatesReceiver`
  only receives PendingIntents that LocationTracker creates, and only those receivers reach LocationTracker; the
  Thunderbird code is already gone. Removed all three; kept InAppBrowserActivity (LoginScreen launches it).
- Reachability then freed 31 location-tracking files; 5 tests covered only them.
- Milestone: liveTest 10/10 passed (before this commit, after commit 3).
- Verified: all-target compile, gate --apps exit 0.
- Kotlin lines → 98619.

### 5. More Koin registrations nothing injects
- Removed `VideoPayloadProcessor`, `VideoPreloader`, `LocationPreviewProvider`, `PayloadDownloadService`,
  `ServerIpStore`, `ServerIpCapture` definitions: every other mention of those types is KDoc, and
  ServerIpStore/ServerIpCapture only refer to each other (ServerIpCapture's init — which arms the IP capture — never
  ran because nothing injected it). Freed 4 files (location preview, web-mercator, payload download service) + 4 tests.
- Verified: all-target compile, gate --apps exit 0.
- Kotlin lines → 97745.

### 6. Unused Compose resources
- A resource is unused when its name appears in no .kt file (generated accessors are referenced by name; no
  `allStringResources`/`allDrawableResources`/`readBytes("files/…")`/computed-name lookups exist). Removed 3,825
  entries: homebase-common 2,154 strings + 9 plurals (and the matching 1,654 + 6 Danish ones), audio-app 2
  (`library_sort`, `home_title`); 89 files: dice and Thunderbird setup images, compose sample icon, emoji data JSON.
  homebase-common now has 79 strings. Common English words used as names (`settings`, `delete`, …) are kept even if
  unused — the word check is conservative.
- Verified: all-target compile, gate --apps exit 0 (incl. UI render tests).
- XML lines −4,239 (Kotlin unchanged).

### 7. Declarations whose names are never used (vendored layer)
- Candidates: functions, properties, classes and objects whose name appears in no file of the repo (Kotlin on every
  platform incl. tests, XML, ProGuard, SQL, JSON) other than their own declaration(s). Excluded: override, operator,
  external, expect/actual, infix, constructor parameters, properties of @Serializable classes, anything annotated
  @JvmStatic/@JvmName/@Keep/@ObjCName/@Throws or a test hook, `main`, `componentN`, the baseline-profile generator.
- Cut by script (declaration + its KDoc/annotations, ending at a balanced boundary), iterated to a fixpoint; a file
  whose cut didn't compile was reverted (the first version over-ran the closing brace of the enclosing scope; fixed).
- Audit: every removed hunk starts with a candidate whose name now appears nowhere in the repo; other declarations
  inside removed text are only locals and constructor params of removed classes; the other 80 hunks are blank lines.
  6 candidates left in place (their files didn't compile after cutting).
- Verified: all-target compile, gate --apps exit 0, assembleRelease exit 0.
- Kotlin lines → 95,945.

### 8. Methods R8 never reaches, where the name is shared with live code
- Candidates from a fresh R8 member report: methods in vendored classes R8 drops as uncalled on Android (no
  getters/setters/synthetics/serializer or data-class members), declared exactly once by that name in the file, not
  override/operator/expect/actual/abstract. Cut by the same script; a compile error anywhere (desktop and iOS callers
  included) reverted the declaring file — 63 of 77 files went back, 14 kept (drive HTTP/query helpers, peer/temporal
  providers, owner session, profile repository, YouAuth params, enum helpers, activity provider). Also one 16-line
  file the earlier pass had left orphaned (DriveQueryModel.kt).
- Audit: all 32 removed hunks start with a candidate method. Verified: all-target compile, gate --apps exit 0,
  assembleRelease exit 0.
- Kotlin lines → 95,241.

### 9. Same R8 method pass, refined per candidate
- Instead of reverting a whole file on any error, each round starts clean, cuts all remaining candidates, and turns
  errors into exclusions: a name an error mentions (or, for receiver-mismatch errors that quote nothing, any
  identifier on the failing source line) excludes that candidate; an edited file with an error naming no candidate
  excludes its candidates. Converged in a few rounds: 43 more methods in 21 files (contacts, credentials, crypto
  helpers, outbox wrapper, drive cache…). Over-exclusion only keeps code.
- Audit: all 38 hunks start with a candidate. Verified: all-target compile, gate --apps exit 0, assembleRelease exit 0.
- Kotlin lines → 94,565.

### 10. Unused library dependencies
- A dependency is unused when nothing in its module imports or fully-qualifies its package. Removed 42 declarations:
  homebase-api 15 (ExoPlayer/HLS/media3-ui, androidx.browser/appcompat, navigation, Koin-Compose, FileKit dialogs,
  kotlinx-datetime/html/immutable, Ktor logging, ktor-server-html, metadata-extractor), homebase-common 19 (CameraX x5,
  PDF viewer, biometric, accompanist, Play Services location, Firebase Crashlytics, rich editor, zoom-image, appcompat,
  lifecycle-process, FileKit dialogs, kermit-io, kotlinx-io, immutable collections, Ktor logging), homebase-auth 5,
  audio-app 1 (activity-compose), desktopApp 1 (FileKit dialogs).
- Kept although import-free: Coil add-ons (network/svg/gif/video — Coil discovers them through ServiceLoader, and an
  identity avatar may be SVG/GIF), smart-exception (ffmpeg-kit runtime), multiplatform-settings in audio-app (a
  Settings type crosses homebase-common's API), compose tooling preview in homebase-auth (imported as androidx.*).
- Made explicit what had only arrived transitively: compose foundation in homebase-api (it uses @Immutable, snapshot
  state and ImageBitmap), Material Components in androidApp (its manifest theme), coroutines-swing for homebase-auth's
  JVM UI tests (Dispatchers.Main).
- Firebase Crashlytics could never initialise (no google-services.json — the log said so on every start); the
  biometric library's USE_BIOMETRIC/USE_FINGERPRINT permissions are gone from the merged manifest. c2dm RECEIVE stays
  (kmpnotifier/Firebase Messaging, still used).
- Verified: all-target compile, gate --apps exit 0, assembleRelease exit 0 (R8 reports no missing classes), release
  APK launched on the emulator to login with no crash, desktop distributable launched for 20 s with no errors.
- Release APK 112.9 MB → 111.6 MB.

### 11. Version catalog and root plugins
- Removed 49 catalog libraries and 2 catalog plugins no build file references, 5 root `apply false` plugins no
  module applies (kotlinAndroid, composeHotReload, androidLint, googleServices, firebaseCrashlytics), then 34
  `[versions]` entries nothing points at, and the comment blocks that described removed entries.
- Verified: all-target compile, gate --apps exit 0, assembleRelease + :baselineprofile:assemble exit 0.

### 12. Shared helpers in audio-app
- `FileSystem.writeTextAtomically` replaces three copies of temp-file-then-atomicMove (ListeningHistory,
  TrackImporter, OfflineKeeper). `PlaybackController.playFrom(queue, track)` / `playAll(tracks, shuffle)` replace
  six copies of index-lookup and shuffle-or-not code across the Home, Library and Collection view models.
- One intentional behaviour change: TrackImporter's save now reads the queue under its write lock, the same race
  fixed in ListeningHistory before (two quick saves could land oldest-last).
- Verified: all-target compile, gate --apps exit 0.

### 13. Final sweep
- Re-ran both passes after the dependency and helper changes: 3 more unreachable files (profile-attribute parsing,
  markdown plain-text, peer file-exists response) with their 2 tests, and the last declarations whose names went
  unused (8 files). A comment that named the deleted parser as an example was trimmed.
- Verified: all-target compile, gate --apps exit 0.
- Kotlin lines → 93,925.
