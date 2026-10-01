# Homebase Simple Audio — build log

## Status

- **Current phase:** Phase 2 features 1–6 done; stretch: mini-player done. Next: Android background playback.
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

- Phase 2.1 Library: `AudioDriveApi` (query/upload/rename/delete/range read/download on the Audio drive),
  `TrackStore` (local drive index, reloads on websocket `BatchReceived`, sync `Stopped`, own writes; clears on
  `SessionEnded`), Library screen (title, duration, date added; newest/title A–Z/Z–A; search by title).
  jvmTests: track mapping, range decryption at every offset through the real cached provider, TrackStore on an
  in-memory index, sort/search, formatting. **liveTest (green):** mp3 upload with progress → query-batch → decrypted
  ranges from byte 0, mid-file and tail → full download byte-identical → rename (+ range read after rename) →
  soft delete; plus drive sync pulling an upload into the local index and dropping it after delete.

- Phase 2.2 Import: Import FAB → FileKit multi-picker filtered to the platform's playable extensions → pick-time
  sandbox copy (`materializeForUpload`, keeps Android URI grants / iOS security scope valid) → `TrackImporter`
  (sequential, app-scoped queue; per-file progress panel on the Library; failures stay listed until dismissed).
  Metadata: Android `MediaMetadataRetriever`, Desktop bundled `ffprobe`, iOS `AVURLAsset` duration only; title falls
  back to the filename. jvmTests for the queue, title/MIME rules and real ffprobe on the fixture. liveTest adds an
  import through the real importer (title + duration read from the file, upload, header back).

- Phase 2.3 Play: `AudioStreamServer` (ktor-server CIO on 127.0.0.1, random path secret, `Range`/HEAD support,
  streams 256 KB decrypted chunks with one-chunk read-ahead), `PlaybackController` (single app player, serial
  player lane, queue = library as shown, play/pause, seek, next/previous with restart-after-3 s, auto-advance,
  failure + retry), Player screen (seek bar, elapsed/total, prev/play-pause/next). jvmTests: range parsing, server
  bytes/HEAD/404/416, first bytes delivered before later chunks exist, ffprobe+ffmpeg seek against the server,
  controller behaviour. liveTest: drive track → server → HTTP ranges from 0 and mid-file, ffprobe duration, ffmpeg
  decode from a 4 s seek.

- Phase 2.4 Record: Record screen (mic permission via the copied `rememberRecordAudioPermissionState`, timer,
  stop, preview with its own `AudioPlayer`, name field defaulting to "Recording <date time>", save / discard /
  record again). Save hands the temp file to `TrackImporter` (origin `Recorded`, title given, temp deleted after
  upload) and returns to the Library, where the import panel shows progress. Starting a recording pauses playback.
  jvmTests: record→name→save, empty recording, discard. liveTest: recorded-clip round trip (title/origin/duration,
  byte-identical download, range read).

- Phase 2.5 Download: `DownloadStore` (decrypted copy per track in app data — Android `filesDir/downloads`,
  desktop `<app data>/downloads`, iOS Application Support — written via `.part` + atomic move, valid only when the
  size matches the track; rescans on start and drops stray `.part` files), per-track overflow menu (Download /
  Remove download), progress ring, downloaded/failed badges. `DefaultTrackLocator` plays the local file when a
  complete copy exists, else streams. jvmTests found and fixed a bug where the old-copy cleanup also deleted the
  fresh `.part` (download stalled "in progress"). liveTest: stream URL before download → download → locator returns
  a local file with byte-identical content that ffmpeg decodes from a seek → remove → streams again.

- Phase 2.6 Manage: Rename (dialog) and Delete (confirmation dialog) in the track menu, via `TrackManager`: server
  first, then the local drive index, offline copy and play queue; failures show a snackbar. jvmTests for ordering,
  no-op/blank titles, failure leaving local state untouched. liveTest: rename + delete through `TrackManager` with a
  real `TrackStore` and `DownloadStore` (offline copy survives rename, removed on delete; server soft-deletes).

- Stretch — mini-player: now-playing bar (progress, title, play/pause, next; tap opens the Player) under the Library
  and Record screens, driven by the same app-wide `PlaybackController`.

## Next

- Stretch: Android background playback with notification/lock-screen controls; then playlists, iOS host app.

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

- **Streaming approach (researched, not guessed).** chat-kmp's video path (`VideoPayloadProcessor`) transcodes with
  ffmpeg, and above 5 MB segments into HLS with every `.ts` segment in ONE payload; players then fetch byte ranges
  of that payload through `DriveFileProviderCached.getPayloadBytesDecrypted(chunkStart, chunkLength)` (desktop:
  a loopback `HttpServer` that decrypts each VLC range request; Android: ExoPlayer data source; iOS: a local
  server). HLS exists there for video's reasons: re-encoding/compression, MP4 `moov` placement, segment-sized
  cache entries and AVPlayer's resource-loader model. The key insight is that the decryption itself doesn't need
  segments: payloads are AES-CBC, and any 16-byte-aligned ciphertext range decrypts on its own given the preceding
  ciphertext block as IV (`DriveFileHelpers.getRangeHeader` + `decryptChunkedBytes`). So audio is uploaded as-is
  (no transcode, no HLS) as one encrypted payload, and playback reads decrypted ranges through that same
  existing, disk-cached range path. Proven by `RangeDecryptTest` (every offset, incl. tail) and liveTest (byte 0,
  mid-file seek, tail, after rename). Players consume it via a loopback HTTP server (Phase 2.3).
- **Payload IV ≠ header IV.** Rename must send a fresh key-header IV (server: `400
  mustRotateKeyHeaderIvWhenUpdating`, found by liveTest), so the payload is always decrypted with
  `KeyHeader(payload descriptor iv, file aesKey)` (`AudioTrack.payloadKeyHeader`) — same as chat-kmp's media code.
- **Track file shape:** fileType 4410, payload key `audiotrack`, `AudioTrackContent` JSON (title, sizeBytes,
  mimeType, durationMs, fileName, origin) encrypted in `appData.content`. The plaintext size is stored because
  range reads need it and the server only knows the ciphertext size.
- **Uploads are direct** (`DriveUploadProvider.uploadFile` with progress), not via the outbox: the UI needs byte
  progress, and chat's outbox-based `UploadService` lives in the uncopied `homebase-upload`. The file is
  stream-encrypted into an upload temp first (same as `VideoPayloadProcessor.encryptVideoFile`).
- **Delete is a soft delete** (other devices see `fileState=deleted` through sync); live tests hard-delete their own
  tagged files (`LIVE_TEST_TAG`) at start and end.

- **Desktop ffmpeg/ffprobe binaries** (735 MB, all five platforms) copied from chat-kmp's
  `homebase-chat/src/jvmMain/resources/ffmpeg/` into `desktop-ffmpeg/`. `audio-app` puts only the build host's
  pair on the JVM classpath (`hostFfmpegResources`), so the macOS distributable is ~280 MB instead of carrying every
  platform. Cross-platform desktop packaging would need a per-target build like chat's conveyor setup.
- **iOS import** excludes Ogg/Opus (no AVFoundation decoder) and reads duration only.

- **Players get a URL.** The existing `AudioPlayer` actuals take a path string; Android `MediaPlayer` and the
  desktop ffmpeg pipeline both accept `http://` and seek with `Range`, so streaming needed no new player, only the
  loopback server. Desktop seek = ffmpeg restart with `-ss` → ranged GET at the new offset.
- **iOS playback does not stream** (stubbed): `IOSAudioPlayer` is `AVAudioPlayer`, which only opens local files.
  iOS is compile-only here; streaming there needs an `AVPlayer` actual (or local download first).
- **Android streaming is untested on a device** (no emulator attached this session); it relies on `MediaPlayer`
  HTTP + cleartext to 127.0.0.1 (allowed by `network_security_config`). Desktop streaming is proven by tests.

- **Live recording test uses a synthesized clip** in the desktop recorder's exact output format (44.1 kHz 16-bit
  mono WAV via `AudioSystem.write`, same as `JvmAudioRecorder`); a test run has no microphone. The mic capture
  itself is the unmodified copied recorder.

### Fixes to the copied layer

- `DriveFileHttpProvider.decryptChunkedBytes`: ranges starting at bytes 1–15 decrypted garbage-shifted output
  (the fetch starts at 0 but the "middle block" branch keyed off the original start ≠ 0, so ciphertext block 0 was
  used as IV and the first 16 bytes dropped). Now keyed on `chunkStart < 16` with the right slice. Found by
  `RangeDecryptTest`; the same logic in `DriveFileProvider.decryptChunkedBytes` (header-key variant, unused here) is
  left untouched.

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
