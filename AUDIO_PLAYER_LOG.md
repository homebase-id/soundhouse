# Homebase Simple Audio — build log

## Status

- **Current phase:** Phase 0 done (import committed on `main`). Next: Phase 1 app shell on branch `audio-player`.
- **Source:** chat-kmp @ `6b083f6ffbddc19ab399c603f4fd38db2092bc2f` (clean working tree at copy time; files taken with `git archive HEAD`).

## Done

- Phase 0: imported `homebase-api`, `homebase-common`, `homebase-auth`, `homebase-notifshared` (+ tests), Gradle wrapper,
  version catalog, `gradle/local-repo` (ffmpeg-kit AAR), `buildsystem/` keystores, root `build.gradle.kts`.
  New `settings.gradle.kts` and `.gitignore`. Gate: `scripts/gate.sh` (JVM + Android + iOS-sim compile of main and
  test, all jvmTests) green.

## Next

- Phase 1: `audio-app` (shared UI, Koin, Compose Navigation) + `androidApp` + `desktopApp`, login gate, CLAUDE.md.

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
