# Soundhouse

A personal audio library stored on your own [Homebase](https://homebase.id) drive. Import or record audio,
stream it encrypted from your identity, organise it into collections, and keep recent listening on the device.

Kotlin Multiplatform with Compose Multiplatform: Android and desktop (JVM) are the shipping targets; the iOS
framework compiles.

## Build and run

```bash
./gradlew androidApp:installDebug     # Android (debug build, id.homebase.soundhouse.debug)
./gradlew desktopApp:run              # Desktop
./scripts/gate.sh --apps              # compile every target and run the JVM tests
```

`CLAUDE.md` describes the module layout and conventions. `scripts/deadcode/` holds the dead-code tooling.

## License

See [LICENSE](LICENSE), [LICENSE-GPL](LICENSE-GPL) and [NOTICE](NOTICE).
