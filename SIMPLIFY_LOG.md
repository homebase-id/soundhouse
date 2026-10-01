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
- [ ] 3. Re-run R8 analysis (removals expose more) and repeat 1–2
- [ ] 4. Koin definitions that nothing injects (registered but dead), then their classes
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
