Overnight refinement of Soundhouse (repo /Users/todd/src/odin/homebase-audio). Work autonomously; never ask me questions. When unsure, take the safer option and note it in REFINE_LOG.md. Each wake-up: read REFINE_LOG.md, do the next unchecked plan item, update the log, continue.

SETUP (first iteration only)
- git checkout main && git checkout -b refine-night2 (branch names never contain "/").
- Copy the dead-code tooling from /tmp into scripts/deadcode/ (reach.py, deadmembers.py, cutmembers.py, refine.py, r8members.py, compile-all.sh, prune.sh), fix any hard-coded paths, add a short README with how to run them, and commit it.
- Record a baseline in REFINE_LOG.md: Kotlin lines per module (git ls-files '*.kt' | xargs cat | wc -l, per module), release APK size, and a plan checklist built from the SCOPE below.

SCOPE (in this order)
A. Video pipeline + FFmpeg-kit. Make PayloadFile.videoQuality (and the trim/inputBlobUrl video-only fields) no longer needed by live code: keep any serialized field names readable (old outbox rows must still decode: keep the field optional with a default, or rely on ignoreUnknownKeys after confirming OutboxSerializer uses it, and prove it with a test that decodes an old outbox JSON row). Then remove the video package, FFmpeg-kit and its native libs on Android, video decoders on iOS/desktop, and anything only they reach. Keep everything audio uses (check AudioTrackMetadata/readAudioMetadata, recording, desktop ffmpeg used for audio playback/metadata: those stay).
B. Chat-only services that still run: peer websockets (PeerWebSocketManager and friends), push-notification plumbing (kmpnotifier, Firebase Messaging, c2dm permission, PushNotificationApi), contacts sync, and outbox paths only chat used. Remove them and their wiring in AuthConnectionCoordinator/LoginViewModel/ApiModule. Soundhouse must still: sign in, sync the Audio drive over the websocket, upload/download/stream/record, run collections, offline listening and the upload queue.
C. Re-run the dead-code passes (reach.py, deadmembers.py, R8 member pass) after A and B, since they free a lot.
D. Unused resources/dependencies/catalog entries freed by A–C.
E. Comment cleanup in files you already touched (CLAUDE.md "Comments" rules); no comment-only sweeps of untouched vendored files.

FAST MODE (how to work)
- Cut in big batches. Inner loop = JVM + Android compile only (main + tests), not iOS:
  ./gradlew --continue -q $(for m in homebase-notifshared homebase-api homebase-common homebase-auth audio-app; do printf ":$m:compileKotlinJvm :$m:compileTestKotlinJvm :$m:compileAndroidMain :$m:compileAndroidHostTest "; done) :androidApp:compileDebugKotlin :desktopApp:compileKotlinJvm
- Use the refine loop (exclude what breaks, recut from clean) instead of reverting whole batches.
- Per scope item (A, B, C, D): one milestone = full gate (./scripts/gate.sh --apps > /tmp/gate.log 2>&1; echo "exit=$?", read the exit code directly, never through a pipe), then ./gradlew :androidApp:assembleRelease (R8 must report no missing classes), then ./gradlew :audio-app:liveTest (record pass/skip counts), then commit. Never commit a failing gate. Never delete or weaken a test to make it pass; tests for removed code may go with it.
- After A and after B: install the release APK on the emulator (start it if needed: ~/Library/Android/sdk/emulator/emulator -avd Pixel_8_Pro -no-snapshot-save &), launch it, confirm no crash in logcat, and note it.

HARD RULES
- /Users/todd/src/odin/chat-kmp is READ-ONLY.
- ~/.config/homebase-audio-test/session.json: never print, log, commit or copy its contents.
- Never call the logout endpoint (/api/apps/v1/auth/logout) or anything that revokes or deletes the client.
- Only touch the Audio drive on the server; nothing else on the identity.
- Do not touch my phone (R5CW333ERCK). Do not merge into main, push, or open a PR.
- Do not change: AppConfig APP_ID/slug, drive alias/type, file types 4410/4411, payload key, the DB schema, or the JSON formats of imports.json / offline.json / listening-history.json.
- Follow CLAUDE.md (Material 3, stringResource, no Text("literal"), no \' in strings.xml, no , . : ; / < > [ ] in backticked test names).
- Before every commit run git branch --show-current and abort if it prints main.

STOP
When A–E are done (or what remains is logged as "kept, why"): run the full gate, release build and liveTest once more, then write a summary at the top of REFINE_LOG.md (lines and APK size before/after per module, commits, behaviour changes, anything kept and why, anything I must decide) and end the loop.
