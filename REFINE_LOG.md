# Refine log (branch refine-night2)

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
- [ ] C. Dead-code passes again (reach, names, R8 methods)
- [ ] D. Resources / dependencies / catalog freed by A–C
- [ ] E. Comments in touched files
- [ ] Final: gate, release, liveTest, summary

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
