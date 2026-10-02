# Homebase Simple Audio — build log

## Final summary (2026-09-30)

All six required features are built on branch `audio-player` (local commits only, no remote). Final run: every
jvmTest rerun from scratch across all modules, 2,348 tests, 0 failures; `liveTest` 7/7 against the real test
identity, output checked for the token and secret (none present). The gate (`scripts/gate.sh --apps`: JVM, Android
and iOS-simulator compile of main and test, all jvmTests, `androidApp:assembleDebug`,
`desktopApp:createDistributable`) is green at every commit.

### What works, per platform

| | Desktop (JVM) | Android | iOS |
|---|---|---|---|
| Sign in (YouAuth, Audio drive R/W only) | ✅ loopback callback | ✅ `homebase-audio://` deep link | compiles |
| Library: list, sort, search, live updates | ✅ | ✅ | compiles |
| Import with progress + title/duration | ✅ (bundled ffprobe) | ✅ (MediaMetadataRetriever) | compiles (duration only, no Ogg) |
| Streaming playback, seek, next/previous | ✅ (ffmpeg over loopback HTTP) | ✅ MediaPlayer over loopback HTTP — **not run on a device** | stub: `AVAudioPlayer` can't stream |
| Record → preview → name → save | ✅ (WAV) | ✅ (AAC/m4a) | compiles |
| Offline download, plays from disk, remove | ✅ | ✅ | compiles |
| Rename, delete (with confirmation) | ✅ | ✅ | compiles |
| Stretch: mini-player | ✅ | ✅ | compiles |
| Stretch: background playback + media controls | n/a (keeps playing) | ✅ verified on the emulator (background, other app, screen off, media keys) | — |

### What liveTest proved against the real server

1. Upload of an mp3 with monotonic progress, then query-batch finds it with the right title and size.
2. Decrypted range reads from byte 0, mid-file and the tail; a full download that is byte-identical to the original.
3. Rename, including a range read after the rename. This found the server's mustRotateKeyHeaderIvWhenUpdating rule.
4. Soft delete.
5. The copied drive sync pulls an upload into the local index the Library reads, and drops it after delete.
6. The real importer reads title and duration with ffprobe and uploads.
7. The loopback stream server serves drive ranges, and ffmpeg probes the URL and decodes from a 4 s seek.
8. A recorded-clip round trip, using a WAV synthesized in the desktop recorder's exact format.
9. An offline copy is byte-identical and plays from disk; after removal, playback streams again.
10. Rename and delete through `TrackManager`, the local index, the download store and the queue together.

### Stubbed / not done

- **iOS:** compiles, including test sources, but there's no Xcode host app (stretch) and no streaming (needs an
  `AVPlayer`-based `AudioPlayer`).
- **Android:** playback and background controls are built but have not been run on a device or emulator in this
  session. Desktop is the platform proven end to end.
- **Playlists:** not started (stretch).
- **Desktop packaging** bundles only the build host's ffmpeg. A Windows or Linux build has to be made on that OS.

### Fixes after first Android run (2026-10-01)

- **Login crashed on Android** (`NoDefinitionFoundException: coil3.ImageLoader`, from `PublicAvatar` on the
  login screen). chat-kmp registers a Coil `ImageLoader` in its platform module; the audio app didn't. Now each
  platform module registers one with `PublicImageFetcher` and no disk cache; `AudioModulesTest` resolves it.
- **Sign-in still requested Chat drive circle access** (circle drive Write+React for two connection circles), so it
  wasn't "Audio drive only". `circleDriveTargetRequest` and the new `loginCircleIds` are empty; `LoginRequestTest`
  pins the exact request.
- **DownloadStore race:** the startup rescan deletes stray `.part` files and could delete an in-flight download's
  `.part` (seen as a gate timeout under load). Downloads now wait for the scan; a test forces the interleaving and
  fails without the fix.
- **Debug APK limited to arm64-v8a + x86_64** (228 → 173 MB): the emulator ran out of install space.

### Visual refresh, batch 1 (2026-10-01)

- **Generated artwork** (`TrackArtwork`): per-track gradient tile with the title's first character, used in the
  list, player and mini-player. Colours rotate the theme primary's hue per track (keeping its saturation and
  lightness, deeper in dark themes) because the Homebase theme's colour roles are nearly one hue — using the roles
  directly produced identical pale-blue tiles.
- **Library**: large collapsing title, pill search, sort chips (Newest / A–Z / Z–A) plus a Downloaded filter,
  track count, artwork rows with the playing track highlighted and an animated equalizer, inline downloaded badge,
  collapsing Import FAB, account menu, empty state with Import / Record actions, expressive loading indicator.
- **Player**: large artwork that eases smaller when paused, background tinted from the track's artwork, expressive
  wavy seek bar (tap or drag, with progress semantics), shape-morphing play/pause, tonal previous/next.
- **Mini-player**: floating rounded card with artwork and a progress line.
- `UiRenderTest` renders Library, empty Library, Player (light/dark) and the mini-player to
  `audio-app/build/ui-renders/*.png` with sample data, so layouts can be checked without signing in.

### Album art (2026-10-01)

- Import reads the embedded cover (Android `MediaMetadataRetriever.embeddedPicture`, desktop ffmpeg copying the
  attached-picture stream; iOS none yet) and uploads 320/640 px thumbnails + the tiny preview with the payload,
  encrypted with the payload's key and IV so they keep decrypting after a rename (rename now carries the preview).
- `CoverLoader` fetches through the cached thumbnail path and keeps an LRU of decoded images; `TrackCover` shows
  the generated artwork and fades the real cover in. Used in list rows, player and mini-player.
- liveTest: import of an mp3 with a cover → thumbnails listed, cover decrypts and decodes, still readable after
  rename.

### Home tab, Library tab, listening history (2026-10-01)

- **Tabs:** Home and Library as top-level destinations (bottom NavigationBar on phones, NavigationRail at ≥600 dp);
  mini-player above the bar; record and account actions shared by both top bars.
- **Home:** time-of-day greeting, Play all / Shuffle, "Continue listening" cards (cover, progress, time left; resumes
  at the saved position), "Recently played" with relative times and the now-playing marker, "Recently added"
  tiles, and a hint until there's history. `HomeContent` is stateless and covered by `UiRenderTest`.
- **Listening history** (`ListeningHistory` + `ListeningRecorder`): position per track, updated every progress tick,
  written to `<app data>/listening-history.json` at most every 5 s and immediately on pause / track change; cleared
  on sign-out; forgotten tracks drop out because Home joins history with the library. **Decision: device-local
  for now** — resume does not follow you to another device. Syncing it would mean per-track writes to the drive
  (or its local app data) on every pause; worth doing once the drive-side shape is decided.
- `PlaybackController.playQueue(…, startAtMs)` resumes the first track part-way.

### Ghost tracks after hard deletes (found on the emulator, 2026-10-01)

- Live-test files showed up in the signed-in emulator's library after the tests had deleted them. Cause (proven by
  `LiveLibrarySyncTest."a track hard-deleted elsewhere disappears on reconcile"`): drive sync only reports
  changes, and a hard-deleted file never appears as one, so other devices keep the row forever. The app's own
  Delete is a soft delete and syncs fine.
- Fixes: `LibraryReconciler` lists the server's track ids once per signed-in session (after the library loads) and
  removes local rows the server no longer has — only when the listing succeeds completely. Test cleanup now soft
  deletes before hard deleting.

### Recording screen polish (2026-10-01)

- `AudioRecorder.currentLevel()` (new, default 0 so other actuals are untouched): Android
  `MediaRecorder.maxAmplitude`, desktop peak of each captured 16-bit buffer; iOS not measured yet.
- Record screen: large timer, live level meter (square-root scaled so speech reads naturally), stop button whose halo
  follows the input level; preview card with a waveform built from the levels captured while recording (no second
  decode), filled up to the play position, tap to seek. `RecordContent` is stateless and rendered by `UiRenderTest`.

### Android background playback verified on the emulator (2026-10-01)

- Added to `PlaybackService`: partial wake lock + Wi-Fi lock while playing (released on pause/stop; the
  foreground service alone keeps the process but not the CPU/Wi-Fi awake with the screen off), audio focus
  (pause when another app takes it), pause on `ACTION_AUDIO_BECOMING_NOISY`. `AndroidAudioPlayer` now tags its
  output `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` (was unknown).
- Verified with `dumpsys` on Pixel_8_Pro (Android 16, 16 GB data): playing → Home → Settings in front → screen
  off 25 s: session stays PLAYING and the position advances (6.8 → 21.8 → 31.9 → 49 → 74 s), wake lock held,
  service foreground with type mediaPlayback; media keys from outside the app pause/resume; the shade shows the
  media controls; "Continue listening" resumes at the saved position.
- The emulator's AVD data partition was raised from 6 GB to 16 GB (wipe required; `config.ini.bak-6G` kept).

### Design pass with the frontend-design + mobile-android-design skills (2026-10-01)

- **Direction:** a private sound archive. Brand type pairing from the Homebase design manual (Montserrat Alternates
  display/headline/title-large, Montserrat text; previously only login used it) via `AudioTheme`; one audio accent,
  VU-meter amber, as the `tertiary` role for "sound happening" (resume progress, now-playing bars, record meter);
  Homebase blue stays for actions; red only on the record button. No Material You dynamic colour (it would replace
  the identity).
- **The one bold element — soundprints:** seeded per-track waveform on a gradient instead of letter avatars.
  Hues limited to a band around the brand blue (indigo/violet/plum/cyan/teal + one amber-dusk); the full hue wheel
  produced lime/yellow-green that read as cheap.
- **Defaults removed after review:** time-of-day greeting heading → "audio" wordmark + one resume hero; identical
  continue cards → hero + rows; "1:01 · Sep 21" middle-dot meta → duration in a right column (tabular figures)
  and "Added …"/"Recorded …"; "MP3 · Added …" on the player → "Added …"; now-playing overlay on artwork → amber bars
  in the row's trailing slot; decorative circles on artwork removed.
- Tried and dropped: letter glyph artwork; full hue rotation; wavy seek bar (user asked for straight).

### Startup work (2026-10-01)

Baseline (Pixel_8_Pro emulator, debug build): first frame +12.6 s as installed, 8.3–10.4 s after
`cmd package compile -m speed`. The main thread spent 2.0 s in `MainApplication.onCreate` (HTTP client 1.25 s, SQLCipher open
0.5 s), and the first frames took 7.1 s with no app hotspot. Traced with `am start --start-profiler … --sampling 500`.

- **Audio drive only:** the copied `DriveRegistry` (registry file on the Chat drive) made the coordinator mount,
  sync and subscribe Moments, Location and Stickers (`WS[1] ctor (drives=4)`). That was possible because the borrowed Chat
  app ID grants 13 drives. `AuthConnectionCoordinator(useDriveRegistry = false)` skips the registry entirely;
  verified `WS[1] ctor (drives=1)` and Audio is the only `mountDrive`.
- **Startup off the main thread:** `AppStartup` (androidApp) starts Koin, opens the DB and builds the HTTP client
  in parallel on background threads, then starts the long-lived services (`Koin.startAudioServices()`, which replaces
  the `createdAtStart` flags). The AndroidX splash stays up until it's ready. `AudioApp` and the YouAuth callback/resume
  wait for it too. `MainApplication.onCreate` dropped from 2.0 s to 0.2 s, but the splash then waited about 4.3 s on the
  HTTP client, so the first frame barely moved (8.4–10.7 s).
- **The HTTP client's real cost was kotlin-reflect:** Ktor's plugin setup calls `typeOf()`, and with kotlin-reflect
  on the classpath that runs through `ReflectionFactoryImpl` (about 2 s cold). Only `ktor-server-core` (our stream
  server) brings it in, for config-file module loading that `embeddedServer` never uses. The Android runtime
  classpaths now exclude it. Streaming playback was re-verified on the device (PLAYING, position advancing,
  no errors). Desktop still ships it (not measured there).
- **After:** 5.5–9.0 s AOT-compiled (4 runs: 9.0, 7.6, 5.5, 6.5) vs 8.3–10.4 s before.

### Release build, own app ID, "Simply Audio" (2026-10-01)

- Release builds use R8 (shrink + optimise, `-dontobfuscate`, rules adapted from chat-kmp plus `-dontwarn` for the
  Ktor-server reflection paths that the kotlin-reflect exclusion removes). Launches and streams on the emulator and a
  Galaxy S23; R8 hasn't been exercised beyond that.
- `:baselineprofile` module (androidx.benchmark 1.5.0) with `StartupProfileGenerator` and `profileinstaller` in the app.
  Profile not generated yet: it needs a signed-in `id.homebase.audio` on the device to cover Home/Library.
- Own app registration: new `AppConfig.APP_ID` (generated), slug `audio`, name "Simply Audio" (launcher, window,
  desktop package, data dir `SimplyAudio[Dev]`). The borrowed Chat ID is gone, so existing installs must sign in again
  and `liveTest`'s session (minted for the Chat ID) likely needs replacing.

### Imports that survive the background (2026-10-01)

- Cause, reproduced on the emulator: after switching to another app the process becomes cached (oom adj 900) and
  ~10 s later `ActivityManager: freezing <pid> id.homebase.audio`. A frozen process can't send, so a long upload's
  connection died ("Software caused connection abort" on the S23) and nothing retried it.
- `UploadService` (androidApp): `dataSync` foreground service with progress notification plus wake/Wi-Fi locks while
  any import is queued/uploading/retrying; stops when the queue is idle; `onTimeout` handles Android 15's 6 h cap.
- `TrackImporter`: connection failures retry with backoff (10 s → 5 min, 5 tries) as `Retrying`; then `Failed` with an
  `ImportFailure` reason shown in the UI and a Retry button. The queue persists in `imports.json`; app-owned copies
  move from the cache to `imports/` and are deleted only on success or dismiss. Sign-out drops the queue.
- Home shows the import panel too. Not verified on a device with a real upload yet (no signed-in emulator).

### Listening controls (2026-10-01)

- Player row under the transport: back 10 s / forward 30 s (Material has Replay10/Forward30 icons, no 15), speed
  menu 0.5–2× (persisted in `AudioSettings`, applied on startup; players already keep speed across tracks), and a
  sleep timer (15/30/45/60 min with a live countdown, or end of track). The media session's rewind/fast-forward
  map to the same skips. `PlaybackController` keeps speed and the timer across `playQueue`; `stop()` keeps speed.

### Offline listening by default (2026-10-01)

- `OfflineKeeper` downloads recent listening in the background: resumable tracks first, then most recently
  played, within a limit (default 2 GB, Wi-Fi only by default). One download at a time. Its own copies are tracked
  in `offline.json` apart from the user's downloads, which it never removes and which don't count towards the limit.
  Removing a kept track skips it until it's played again; downloading one makes it the user's.
- Triggers on changes to what it wants (not every progress tick) plus a 15-minute recheck for Wi-Fi coming back;
  it waits for the download rescan, library and history to load so a cold start doesn't evict everything.
- Settings screen (account menu): keep-recent switch, Wi-Fi only, 1/2/5/10 GB, space used.
  `AudioSettings` (SharedPreferences-backed) also holds playback speed. Android needs ACCESS_NETWORK_STATE.

### Collections (2026-10-01)

- A collection is a header-only encrypted file on the Audio drive (file type 4411, content `{name}`, uniqueId = the
  collection's id). Membership is a tag on the track equal to that id, so a track can be in many collections, the
  server can query by collection, and there's no size cap on a collection. Tags that aren't collections (e.g. the
  live-test tag) are preserved by `retagged`. Deleting a collection untags its tracks first, then soft-deletes it.
- `CollectionStore` reads the local index like `TrackStore` (shared `queryLocalIndex`); `CollectionManager` writes
  server-first then upserts the header locally. Track header updates reuse the rename path (IV rotation).
- UI: track menu "Collections…" (checklist + create-and-add), collection chips + "New collection" in Library, a
  Collections shelf on Home (non-empty ones), and a collection screen with play all/shuffle, rename/delete, and
  "Remove from collection" on each track.
- `LiveCollectionTest` passes against the real server (create, tag, query by tag, rename, delete, untag).

### Listening history races (2026-10-01)

- Found when the gate failed under load (`sign-out and delete clear entries` timed out); reproduced
  deterministically with a filesystem whose reads wait on a latch. (1) The startup load merged the stored file
  in with `loaded + current`, so a sign-out or `forget` before the load finished was undone — the previous
  identity's history could come back. Removals before load are now recorded and applied inside the same
  atomic update. (2) `save()` snapshotted outside the write lock, so two quick saves could land oldest-last;
  the snapshot is now read under the lock.
- Process slip: commit b347ba3 (collections) was made although that gate run failed — the gate's exit code was
  hidden by a pipe. Fixed here; gate exit codes are now checked directly.

### App icon (2026-10-02)

- New icon: a house outline with a waveform pulse, in white on a violet gradient (replaced the first
  soundprint-bars version the same day). Android adaptive layers (foreground,
  background, monochrome for themed icons and notifications, legacy) at all five densities, desktop icns/ico/png,
  and the SVG masters in `icons/source/` for future edits. Checked: exact sizes, transparent foreground/monochrome,
  opaque background, glyph radius 111.7 px of the 132 px safe circle (no launcher clipping), previews in circle,
  squircle and rounded-square masks and at 48 px.

### Open problems

- **Cold start is still 5–9 s on the emulator** (debug build, AOT-compiled): what's left is Compose's first
  composition/measure/draw on the main thread with software GL. A release build with R8 and a baseline profile is
  the next lever (not done). Details in "Startup work" above.
- `DriveRegistryTest.observerEmitsUnmountWhenBatchCarriesShrunkList` (copied) failed once under load early on and
  never again. Details below.
- Copied homebase-common jvmTests write non-secret keys (`pending_upgrade_first_seen_ms`,
  `location_last_step_cumulative`) to the dev app dir's `shared_preferences.properties`, the same way they wrote to
  `HomebaseChatDev` in chat-kmp.

### How to sign in and try it by hand

- **Desktop:** `./gradlew desktopApp:run`. Enter your Homebase identity, approve in the browser (Read+Write on the
  Audio drive only), and the browser returns to the app via a localhost callback. Dev data lives in
  `~/Library/Application Support/HomebaseSimpleAudioDev`.
- **Android:** `./gradlew androidApp:installDebug` (`id.homebase.audio.debug`). Sign in the same way; the owner page
  redirects back through `homebase-audio://`.
- Then: **Import** (FAB) picks audio files; tap a track to stream it; the ⋮ menu has Download / Remove download /
  Rename / Delete; the mic in the top bar records.
- **Live tests:** `./gradlew :audio-app:liveTest` (needs `~/.config/homebase-audio-test/session.json`; never logs
  it out).

### Before real use / before the first PR

- **Give the app its own app ID.** It signs in as Homebase Chat (`2d78140138044b57b4aad8e4e2ef39f4`). Change
  `AppConfig.APP_ID` and `AppConfig.APP_SLUG` together.
- **Run `/simplify`** on the branch before opening the first PR.

## Status

- **Current phase:** Done (features 1–6 + mini-player + Android background playback). Remaining stretch: playlists, iOS host app.
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

- Stretch — Android background playback: `PlaybackService` (foreground `mediaPlayback` service, framework
  `MediaSession` + `Notification.MediaStyle`, so no new dependency): play/pause/next/previous in the notification and
  on the lock screen, seek from system controls, notification stays (not ongoing) while paused, service stops when
  the queue empties. Started by `MainApplication` whenever a track starts loading. **Built, not run on a device**
  (no emulator in this session). Desktop keeps playing when the window is in the background by design.

## Next

- Remaining stretch goals: playlists, iOS Xcode host app (and an `AVPlayer`-based iOS player for streaming).
- Also added: `AudioModulesTest` resolves the whole Koin graph (every screen's view model and every service) against
  a temp `user.home`, so wiring mistakes fail in jvmTest instead of after sign-in.

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
