# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

Soundhouse — a Kotlin Multiplatform personal audio library stored on the user's Homebase
drive. Android and Desktop (JVM) are the shipping targets; the iOS framework must compile. No web
target. Compose Multiplatform UI, MVVM, Koin DI.

The `homebase-*` modules were imported from chat-kmp (see `AUDIO_PLAYER_LOG.md` for the source
commit). Treat them as a vendored layer: change the minimum needed, don't refactor them.

```
homebase-api          — HTTP client (Ktor), SQLDelight DB, crypto, drive sync, websocket
homebase-common       — theme, settings, auth coordinator, audio player/recorder actuals
homebase-auth         — YouAuth login screen + view model
    ↑
audio-app             — the app: drive service layer, streaming server, screens, Koin modules
    ↑
androidApp / desktopApp  — platform entry points
```

## Build & Run Commands

```bash
./gradlew androidApp:installDebug     # applicationId id.homebase.soundhouse.debug
./gradlew desktopApp:run              # data dir ~/Library/Application Support/SimplyAudioDev
./scripts/gate.sh                     # commit gate: compile JVM/Android/iOS-sim (main+test) + all jvmTests
./scripts/gate.sh --apps              # + androidApp:assembleDebug + desktopApp:createDistributable
./gradlew :audio-app:liveTest         # live server tests; skipped without ~/.config/homebase-audio-test/session.json
```

CI and release workflows, their secrets and one-time setup: `.github/RELEASING.md`.

### Per-module compile checks (type-check one KMP library across targets)

To verify a change in a library module (e.g. `audio-app`) compiles on each target without building
the whole app, use the **target-named** Kotlin compile tasks. KMP library modules do NOT have the
AGP app-style `assembleDebug` / `compileDebugKotlinAndroid` tasks — those exist only on
`androidApp`. Using them on a library fails with "task not found".

```bash
./gradlew :audio-app:compileKotlinJvm                  # JVM/Desktop
./gradlew :audio-app:compileAndroidMain                # Android (NOT compileDebugKotlinAndroid)
./gradlew :audio-app:compileKotlinIosSimulatorArm64    # iOS (only on a macOS host)
# test sources: :audio-app:compileTestKotlinJvm, :audio-app:compileAndroidHostTest,
#               :audio-app:compileTestKotlinIosSimulatorArm64
```

Backticked test names must not contain `,` `.` `:` `;` `/` `<` `>` `[` `]` — Kotlin/Native
rejects them, so `compileTestKotlinIosSimulatorArm64` fails even though JVM is happy.

## Live testing

`audio-app/src/jvmTest/.../live/` runs against a real identity using
`~/.config/homebase-audio-test/session.json`. Never print, log or commit its contents. Never call
the logout endpoint (`/api/apps/v1/auth/logout`) or anything that revokes the client — that kills the
session. Only touch the Soundhouse drive. The suite is its own Gradle task (`liveTest`) and is excluded
from `jvmTest`.

## Debugging & root cause

When you hit a freeze, ANR, crash, or unexplained behaviour, do not ship
a workaround that hides the symptom without identifying the cause first.
Capture concrete evidence — a stack trace, an ANR dump, a profiler
sample, a reproducible test — and prove what's broken before fixing it.
If you can't capture evidence, the fix is to install the instrumentation
that will (a watchdog, a logger, an `adb logcat` capture) — not to patch
around the symptom and move on.

Symptom patches to avoid:

- Wrapping a state read in `remember { }` because "without it the screen
  freezes" — the underlying read is doing something expensive or
  reactive on every recomposition; fix that, don't snapshot it.
- Adding `try { … } catch (_: Exception) { }` around code that's
  actually misbehaving, so the exception stops surfacing.
- Adding a `delay()`, an extra `LaunchedEffect`, or a manual redraw
  trigger to make a UI glitch "go away" without explaining why it
  helped.
- Reverting or hiding the feature that exposed the bug, when the bug
  itself is still there.

Each of these makes the bug invisible at the cost of leaving the cause
in place to resurface elsewhere later. If you find yourself reaching for
one of these patterns, stop and write down what you actually observed,
what you suspect, and what evidence you'd need to confirm — then go get
that evidence.

## KMP Source Set Convention

Each module follows the standard KMP layout:

- `src/commonMain/kotlin/` — Shared code (bulk of logic)
- `src/androidMain/kotlin/` — Android implementations (OkHttp, MediaPlayer, SQLCipher)
- `src/jvmMain/kotlin/` — Desktop implementations (ffmpeg-backed audio, JDBC SQLite)
- `src/nativeMain/kotlin/` — iOS implementations (Darwin networking, native SQLite)

Use `expect`/`actual` declarations for platform-specific code. The flag `-Xexpect-actual-classes` is
enabled.

## Comments

Default to none. The imported layer is over-commented; do not add to it.

Don't write:

- What the code already says. `// increment the counter`, `/** Returns the user's name. */`.
- KDoc restating parameter names, or a header block on every function.
- Narrative about how the code got here, alternatives considered, or how it was tested.
- Issue and PR numbers. `git blame` already links the line to its commit and PR.

Do write, as one terse line: a **why** that isn't derivable from the code — a landmine,
an ordering constraint, a workaround for someone else's bug, a deliberate simplification
and its ceiling.

If the comment is longer than the code it explains, delete it. Rename the variable
instead — a self-explaining name beats any comment. Put the reasoning in the commit
message or `AUDIO_PLAYER_LOG.md`, not the source.

## UI & Design Quality

All UI code must follow **Material 3** guidelines. Before writing or modifying any
screen/composable, verify:

- Use `Icons.AutoMirrored.*` for directional icons (back arrows, forward) — never `Icons.Default.ChevronLeft`
- Use `collectAsStateWithLifecycle()` — never `collectAsState()` for ViewModel StateFlows
- All user-facing strings must use `stringResource()` from compose resources — never hardcode text.
  This includes counters and badges (e.g. `"$count tracks"`). `homebase-common`'s
  `ArchitectureTest.kt` runs Konsist against every Composable in the project on JVM and fails the
  build if it sees a `Text("…")` / `Text(text = "…")` literal. Build the string outside the
  composable (e.g. `stringResource(AR.string.track_count, n)`) or pass a variable in.
  The audio app's strings live in `audio-app/src/commonMain/composeResources/values/strings.xml`
  (class `id.homebase.soundhouse.resources.AR`).
- Use `start`/`end` padding, not `left`/`right` (RTL support)
- Use Material 3 color roles from `MaterialTheme.colorScheme` — never hardcode colors
- Use Material 3 typography from `MaterialTheme.typography` — never hardcode text styles
- Provide `contentDescription` on all meaningful icons/images for accessibility
- UiState should be a flat `data class` with `_uiState.update { }` pattern
- One-time events (navigation, snackbar) should use separate `SharedFlow`, not stored in UiState

## Compose & Flow gotchas

- **Don't write `snapshotFlow { ... }.distinctUntilChanged()`** — `snapshotFlow` already
  dedups internally with structural equality (`!=`) before emitting, so the trailing
  `.distinctUntilChanged()` runs the *same* comparison a second time. For scalar samples
  it's just waste; for samples like `Pair<Int, List<...>>` the doubled O(n) `List.equals`
  on every snapshot commit can stall the Compose UI dispatcher (Main) for seconds during
  bursty mutations like `LazyListState.scrollToItem`. If you genuinely need a different equality (e.g.
  comparing only one field of a heavy value), shape the snapshotFlow block to *return*
  that key — don't bolt distinctUntilChanged on top.

- **Don't use `LazyListState.firstVisibleItemIndex` as an array index without
  clamping.** Compose's idiom for "land at the bottom on first frame, no flash" is
  `LazyListState(firstVisibleItemIndex = Int.MAX_VALUE)` — LazyColumn clamps the
  sentinel during its first measure pass, but anything reading the field *before*
  that measure runs (a `snapshotFlow {}` body, a `derivedStateOf`, a save-scroll
  effect on the same frame the state was created) gets `Int.MAX_VALUE` back. Using
  that as `for (i in firstVisibleIndex downTo 0)` walks ~2.1B iterations and
  freezes the UI dispatcher for seconds. Clamp it to `0 until items.size` (and
  treat an empty list as "no index") before using it.

## Strings & Unicode

User-entered text (track titles, file names) can contain emoji and other non-BMP characters that
Kotlin `String` stores as UTF-16 surrogate pairs. Chopping such a string with `take(n)`,
`substring(0, n)`, `dropLast`, `subSequence`, etc. can split a surrogate pair in half and produce a
lone surrogate that breaks rendering and downstream serialization.

- To truncate user content to a length budget, use `String.truncateToCodePoints(n)` from
  `id.homebase.api.util.StringExtensions` — it advances past surrogate pairs.
- `take`/`substring` remain correct for known-ASCII content: URLs, hex/base64, UUIDs,
  byte arrays, file extensions.

### `strings.xml` apostrophes

Do **not** escape apostrophes as `\'` in `composeResources/values/strings.xml`. Compose
Resources is not Android aapt — a plain `'` is correct and is the existing convention
(e.g. `Couldn't save the track.`). Write `won't`, `doesn't`, `you're`, not `won\'t`.
