# Refine log (branch refine-night2)

## Summary (morning of 2026-10-03)

Everything in REFINE_TONIGHT.md is done. 8 local commits on `refine-night2`; nothing merged into main, nothing pushed.

| Module | Before | After |
|---|---|---|
| homebase-notifshared | 301 | removed (module was empty) |
| homebase-api | 65,897 | 40,886 |
| homebase-common | 13,657 | 9,798 |
| homebase-auth | 3,430 | 3,427 |
| audio-app | 10,600 | 10,567 |
| androidApp / desktopApp / baselineprofile | 595 | 595 |
| **Kotlin total** | **94,480** | **65,273 (−29,207, −31%)** |

- Release APK: **111.5 MB → 35.9 MB** (FFmpeg-kit's native libraries were most of it).
- Repo: ~190 MB of binaries gone (154 MB iOS ffmpegkit xcframework, 33 MB local Maven repo with the FFmpeg-kit AAR).
- Branch diff: 1,425 files, +609 / −290,300 (most of the deletions are the xcframework).

What went: the video pipeline and FFmpeg-kit (A); peer websockets, push notifications (kmpnotifier/Firebase
Messaging, Nucleus, PDFBox) and contacts lookups (B); the outbox pipeline, six chat-only database wrappers and further
dead code from the reachability / unused-name / R8 passes (C); unused strings, dependencies and catalog entries (D);
stale comments and the profile-editing classes they exposed (E); the empty homebase-notifshared module.

Verification: every milestone passed the full gate (JVM, Android, iOS-simulator compiles of main and tests; all
jvmTests; debug APK; desktop distributable), the R8 release build, liveTest 10/10 against the real server, and a
release-APK launch on the emulator with no crash; D and E also launched the desktop app cleanly. Final liveTest on the
last commit: 10/10.

Behaviour changes (all intended):
- No push call after sign-in (it was failing with 403 on the phone); no c2dm permission; Firebase messaging gone.
- The auth coordinator no longer toggles/flushes the (always empty) outbox; the outbox table and schema remain.
- Sign-in name/avatar now come only from the public profile — which is where they already came from, since the
  Contacts drive is never synced in Soundhouse.

Kept on purpose:
- VideoQuality / VideoMetadata / VideoProcessingPhase (types inside server-returned or stored formats).
- Desktop ffmpeg (FFmpegBinaryManager) — audio playback and metadata on desktop use it.
- The Outbox table and every other table, their SQLDelight adapters, and the schema (logout still drops/recreates all).
- Runtime-discovered libraries with no imports: Ktor engines, SQLite JDBC, JNA, Coil artifacts.
- Headless mode in AuthConnectionCoordinator: it can no longer turn on in Soundhouse (startsHeadless defaults to false
  and nothing sets it), but removing it is a logic change rather than dead code — left for you to decide.
- ~100 R8/name candidates that are used on desktop, iOS or in tests, or that the cut script can't remove cleanly.

Process notes: two silent tooling traps were found and fixed in scripts/deadcode (cut-loop resets unstaged edits,
so script edits must be staged; `--fast` skips iOS, so an R8 cut once removed an iOS-only member — caught both times by
the full compile before any commit). Every cut was audited: each removed hunk starts with a candidate declaration.

Your call: review with `git log main..refine-night2` and merge when happy; decide on headless mode; give the
app its own launcher test on the phone after merging (sign-in, import, play, record, offline, collections).

Instructions: REFINE_TONIGHT.md. Commits are local; nothing merged into main.

## Baseline (main @ 00fbd03)

| Module | Kotlin lines |
|---|---|
| homebase-notifshared | 301 |
| homebase-api | 65,897 |
| homebase-common | 13,657 |
| homebase-auth | 3,430 |
| audio-app | 10,600 |
| androidApp | 528 |
| desktopApp | 40 |
| baselineprofile | 27 |
| **total** | **94,480** |

Release APK: 111,510,189 bytes.

## Plan

- [x] 0. Tooling in scripts/deadcode/ (portable paths, `--fast`, cut-loop, R8 usage script)
- [x] A. Video pipeline + FFmpeg-kit (keep old outbox rows readable)
- [x] B. Chat-only services: peer websockets, push plumbing, contacts sync (outbox paths: see C)
- [x] C. Dead-code passes again (reach, names, R8 methods) + outbox + chat DB wrappers
- [x] D. Resources / dependencies / catalog freed by A–C
- [x] E. Comments in touched files
- [x] Final: gate, release, liveTest, summary

## Log

### A. Video pipeline and FFmpeg-kit — commit below
- Cut the three links from live code: DriveFileProvider no longer implements VideoPrefetchDriveAccess (its methods stay,
  with the interface's default arguments moved onto them; audio reads payloads through them), the Koin binding went,
  and VideoQuality's KDoc link to FFmpegUtils (comments count as references) was replaced.
- Reachability then dropped 39 video files (compression, decoders, preloading, HLS, thumbnails, FFmpeg bridges on all
  platforms) and 21 video tests/fixtures. Kept: VideoQuality, VideoMetadata, VideoProcessingPhase (types inside
  persisted PayloadFile / server PayloadDescriptor / BackendEvent — formats unchanged; OutboxSerializerTest still
  round-trips videoQuality), and FFmpegBinaryManager (desktop ffmpeg used by audio playback and metadata).
- Removed the FFmpeg-kit, smart-exception and mp4parser dependencies, the 33 MB local Maven repo that only held the
  FFmpeg-kit AAR, the 154 MB ffmpegkit xcframework checked into homebase-api/libs (used only to link an iOS test) and
  its cinterop, and a test video. The iOS simulator test binary keeps its -lsqlite3 link option.
- Verified: all targets incl. iOS compile; gate --apps exit 0; assembleRelease exit 0; liveTest 10/10;
  release APK on the emulator launches to login, no crash, cold start 0.43 s.
- Release APK 111.5 MB → 37.1 MB. Kotlin lines → 84,585.

### B. Chat-only services — commit below
- Peer websockets: they only open for drives hosted on another identity (ownerOdinId != null); Soundhouse mounts only
  its own Audio drive with the registry off, so only reset() ever ran. Removed PeerWebSocketManager from
  AuthConnectionCoordinator (mountDrive keeps its own-drive path), ApiModule and AudioModules; then dropped five peer
  provider registrations and ProfileRepository/ProfileProvider, which no code requested. 8 peer files went.
- Push notifications: NotificationService ran with a no-op backend; its only effect was reRegisterAsync() after sign-in
  calling the server's push endpoint, which answered 403 (seen on the phone). Removed it from LoginViewModel and the app
  setup, with PushNotificationApi, kmpnotifier (and with it Firebase Messaging and the c2dm permission), the Nucleus
  desktop-notification libraries and PDFBox. 25 files (notification display, badges, background sync) and 14 tests went.
- Contacts: ContactInfoGateway checked the synced Contacts drive before the public profile, but Soundhouse never syncs
  that drive, so the list was always empty. The gateway now serves only what is used (profileCard, avatarBytes,
  clearCaches) from the public profile cache; the Contacts provider/repository/readers went (17 files, 14 tests).
  Sign-in name/avatar behave the same.
- Behaviour changes: no failing push call after sign-in; no c2dm permission; no Firebase messaging init.
- Verified: all targets incl. iOS; gate --apps exit 0; assembleRelease exit 0; liveTest 10/10; release APK on the
  emulator launches to login, no crash.
- Release APK 37.1 MB → 36.1 MB. Kotlin lines → 75,396.

### C. Outbox, chat database wrappers, and the dead-code passes — commit below
- Outbox: nothing in Soundhouse ever enqueued; AuthConnectionCoordinator only toggled it online/offline and flushed an
  empty queue (the "OutboxSync: clearCheckout()" log lines). Removed OutboxSync, the drive/scheduled-push/composite
  uploaders, the outbox request models, failure classifier, upload validation, background-execution assertion and
  OutboxSerializer. With nothing processing the outbox, nothing reads stored outbox rows, so no old row can fail to
  decode; the Outbox table and schema are unchanged. (OutboxSync stayed "reachable" only through a local variable named
  `enqueued` colliding with one of its top-level names — confirmed dead by deleting it and compiling.)
- DatabaseManager: removed the wrappers for six chat-only tables nothing outside the database package used
  (autoSavedMedia, appNotifications, chatReadCount, outbox, locationPoint, connectionCache). The tables, adapters and
  schema stay (the generated OdinDatabase needs the adapters; logout still drops/recreates every table).
- Name pass (4 rounds) and R8 method pass (2 rounds) with the cut loop. Fixes to the tooling along the way: refine.py
  now treats a syntax error in an edited file as "bad cut, exclude the file"; cut-loop resets unstaged changes, so edits
  to the scripts themselves must be staged first (one fix was silently reverted that way); `--fast` let an R8 cut remove
  `DatabaseDriverFactory.databaseFiles`, which only iOS uses — caught by the full compile both times, restored, and
  noted in the README. Audit: every removed hunk starts with a candidate; one flagged hunk was a diff alignment artefact
  (removed `buildTransitFormData` has a body identical to the live `buildUploadFormData`; the result is correct).
- Tests: removed only those for removed code; DriveSyncManagerTest's logout-wipe case now seeds through the generated
  queries instead of the removed wrappers, and the route-through-lane tests keep their cases for live wrappers.
- Verified: all targets incl. iOS; gate --apps exit 0; assembleRelease exit 0; liveTest 10/10; emulator launch, 0 crashes.
- Release APK 36.1 MB → 35.9 MB. Kotlin lines → 65,382.

### D. Resources, dependencies, catalog — commit below
- Strings: a precise reference check (`string.x` / `plurals.x` / an import of the generated accessor) instead of last
  night's loose word match found 40 homebase-common strings never used as resources (`settings`, `delete`, `ok`, …
  only appeared as ordinary words); removed from English and Danish.
- Dependencies: removed 21 declarations no code in the module imports — homebase-api: FileKit core, the markdown
  parser, SQLDelight coroutine extensions; homebase-common/homebase-auth: kotlinx-datetime/io, viewmodel-compose
  (common), Ktor content negotiation/serialization (installed only in homebase-api) and the duplicate engine
  declarations (homebase-api already supplies OkHttp/Darwin/CIO), Coil in homebase-auth (re-exported by common);
  notifshared kotlinx-datetime; audio-app viewmodel-compose; desktopApp Kermit. Kept although import-free: Ktor
  engines (chosen at runtime), the desktop SQLite JDBC driver (loaded by name), JNA (FileKit on desktop), Coil
  artifacts (runtime discovery, re-exported), multiplatform-settings in audio-app (type crosses modules), and
  viewmodel-compose in homebase-auth (provides ViewModel there — restored after the compile said so).
- Catalog: 3 orphaned entries.
- Verified: all targets incl. iOS; gate --apps exit 0; assembleRelease exit 0; liveTest 10/10; Android launch 0 crashes;
  desktop distributable launched 25 s with no errors (database opened, login state machine running).
- Release APK 35.88 MB → 35.87 MB.

### E. Stale comments in touched files — commit below
- Found comments in files changed tonight that name deleted code; rewrote nine (PeerFileByGlobalTransitProvider in the
  payload cache, OutboxSync in outbox staging, BackgroundSyncOrchestrator ×4 in AuthConnectionCoordinator, the
  contacts/profile links in ProfileAttribute). Fixing them exposed ProfileProvider/ProfileRepository as referenced only
  by each other: removed. `readPayloadThrough` looked dead but tests drive the live read-through cache through it, so it
  stays with a corrected comment. A last name pass with the full (iOS-including) compile took 26 more lines.
- Verified: gate --apps exit 0; assembleRelease exit 0; liveTest 10/10; Android launch 0 crashes; desktop launch clean.
