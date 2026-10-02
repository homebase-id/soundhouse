# Simplify log (branch simplify-overnight)

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
- [ ] 5. Unused members inside live files (R8 member report), vendored layer
- [ ] 6. Unused Compose string resources (all modules, incl. values-da) and Android resources
- [ ] 7. Unused declarations in audio-app / androidApp / desktopApp
- [ ] 8. Unused Gradle dependencies, plugins, version-catalog entries
- [ ] 9. Comments violating CLAUDE.md in touched audio-app code; duplicated audio-app helpers
- [ ] 10. Final /simplify over the whole branch diff, gate, liveTest, summary

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
