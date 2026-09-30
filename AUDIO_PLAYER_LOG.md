# Homebase Simple Audio — build log

## Status

- **Current phase:** Phase 1 done (app shell). Next: Phase 2.1 Library (service layer + liveTest first).
- **Source:** chat-kmp @ `6b083f6ffbddc19ab399c603f4fd38db2092bc2f` (clean working tree at copy time; files taken with `git archive HEAD`).

## Done

- Phase 0: imported `homebase-api`, `homebase-common`, `homebase-auth`, `homebase-notifshared` (+ tests), Gradle wrapper,
  version catalog, `gradle/local-repo` (ffmpeg-kit AAR), `buildsystem/` keystores, root `build.gradle.kts`.
  New `settings.gradle.kts` and `.gitignore`. Gate: `scripts/gate.sh` (JVM + Android + iOS-sim compile of main and
  test, all jvmTests) green.

- Phase 1: `audio-app` (Koin modules, Compose Navigation host with Loading → Login → Library auth gate, placeholder
  library screen), `androidApp` (`id.homebase.audio`, debug `.debug`), `desktopApp` (data dir
  `HomebaseSimpleAudioDev` for `run`, `HomebaseSimpleAudio` for packaged builds), CLAUDE.md. Desktop smoke-launched to
  the login screen. iOS framework `AudioApp` compiles.

## Next

- Phase 2.1 Library: drive service layer + jvmTests, then liveTest, then the list UI.

## Decisions

- **App ID is borrowed from Homebase Chat** (`2d78140138044b57b4aad8e4e2ef39f4`, `AppConfig.APP_ID`) because the
  live-test session was minted for it. **Known follow-up:** register a real app ID for Homebase Simple Audio before
  real use; change `AppConfig.APP_ID` *and* `AppConfig.APP_SLUG` together (the slug "chat" belongs to the Chat app ID,
  so it stays "chat" until then).
- **wasmJs dropped** from every copied module (target, `wasmJsMain`/`wasmJsTest` source sets, karma config, the
  `mirrorWebAppFfmpegAssetsForTest` task that copied ffmpeg JS out of `:webApp`, and the wasm-only test filter).
  No web target in this app, and it was the only link to `:webApp`.
- **AppConfig:** name "Homebase Simple Audio", deep-link scheme `homebase-audio` (also in the Android/iOS
  `RedirectConfig` actuals), login request = Read+Write on the Audio drive only (dashed UUIDs, slug/type slug "audio",
  same shape as `vaultTargetDriveAccessRequest`), app permissions none, `mandatorySyncDrives` = Audio drive only,
  no circle drive request. The other chat drive definitions stay (still referenced by copied code) but nothing
  requests them.
- **Collision-prone Chat identity renamed:** desktop data dirs `HomebaseSimpleAudio` / `HomebaseSimpleAudioDev`
  (Linux `homebase-simple-audio[-dev]`), production marker `app.rdns.name == id.homebase.audio`, `app_name` string,
  loopback-callback page titles, Android notification action ids, Linux polkit action id, iOS app-group fallback
  `group.id.homebase.audio`, desktop notification JRE-path check.
- **Big binaries kept:** `homebase-api/libs/ffmpegkit-bundled.xcframework` (iOS test cinterop) and
  `gradle/local-repo` ffmpeg-kit AAR (Android dep of homebase-api). Both are needed for the copied modules to build.
- **Gate script:** `scripts/gate.sh [--apps]` runs the whole commit gate in one Gradle call.

- **App shell (Phase 1):** reuses the copied `AuthConnectionCoordinator` (mounts the Audio drive, opens the notify
  websocket, runs `DriveSyncManager`), `YouAuthFlowManager` and `LoginViewModel`/`LoginScreen`. Coordinator is built
  with `startsHeadless = false` and no push fallback on every platform: this app has no background wake, so it
  connects as soon as the session is authenticated. `DriveRegistry` is still constructed (the coordinator needs it);
  it lives on the Chat drive, which this app never requests, so its server reconcile fails and it keeps the mandatory
  Audio drive only.
- **No push notifications:** `NoPushNotificationBackend` (no token, no local notifications). The copied
  `NotificationService` still exists because `LoginViewModel` depends on it.
- **Desktop Java Preferences node** renamed `/id/homebase/app/window` → `/id/homebase/audio/window` (would have shared
  theme/settings with Chat on the same machine).
- **Desktop release vs dev data dir:** `Main.kt` sets `app.rdns.name=id.homebase.audio` only when running under a
  jpackage launcher (`jpackage.app-version` is set), so `desktopApp:run` uses `HomebaseSimpleAudioDev`.
- **Android release build** is unminified and debug-signed for now (no store listing yet); debug build type has the
  `.debug` suffix as required.
- **AudioPlayer is a Koin `factory`** (one player per playback owner) rather than the chat app's single.

### Tests changed in Phase 0

- `ArchitectureTest."IdentityScoped types are never injected from the root scope"` — **deleted**. It asserts at
  least one `IdentityScoped` production type exists; all of them live in chat-kmp's `homebase-chat`/`homebase-core`,
  which were not copied, so the rule has nothing to check here.
- `HomebaseImageLoaderTest` — renamed one test (removed commas from the backticked name). Kotlin/Native rejects `,`
  in identifiers, so `compileTestKotlinIosSimulatorArm64` failed; chat-kmp never compiles that source set on iOS.

## Open problems

- **Flaky copied test:** `DriveRegistryTest.observerEmitsUnmountWhenBatchCarriesShrunkList` failed once in ~6
  full-suite runs (assertion at line 818: `unmounted` list), passes in isolation 3/3 and in full-suite reruns.
  Suspected: `reconcile()` DB work leaves `runTest` virtual time so `advanceUntilIdle()` can return early under load.
  Pre-existing chat-kmp code, not used by the audio app's own logic; left unchanged. Rerun if the gate trips on it.
