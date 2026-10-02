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
- [ ] A. Video pipeline + FFmpeg-kit (keep old outbox rows readable)
- [ ] B. Chat-only services: peer websockets, push plumbing, contacts sync, chat-only outbox paths
- [ ] C. Dead-code passes again (reach, names, R8 methods)
- [ ] D. Resources / dependencies / catalog freed by A–C
- [ ] E. Comments in touched files
- [ ] Final: gate, release, liveTest, summary

## Log
