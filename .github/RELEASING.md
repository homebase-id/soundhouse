# CI and releases

| Workflow | Trigger | Needs secrets |
|---|---|---|
| Build Check | push to main, every PR | no |
| Lint | push to main, every PR | no |
| Silent-revert check | every PR | no |
| Build Mobile Release Dev | nightly 02:00 UTC, manual | Android (skips quietly without them) |
| Build Mobile Release Production | manual, tag push | Android |
| Promote Android Release to public | manual | Play |
| Build Desktop Release Dev | manual | Desktop |
| Build Desktop Release Production | manual | Desktop + signing |

There are no iOS release workflows: the iOS framework only has to compile (Build Check links it).

Versions: `version.code.base` + the workflow run number is the version code, and the name is
`MAJOR.MINOR.<code>` (`.github/scripts/ci-version.sh`). Bump `version.code.base` if a run number
ever collides with a code already on Play.

## Android

One-time, outside GitHub:

1. Create two apps in the Play Console: `id.homebase.audio` (Soundhouse) and
   `id.homebase.audio.dev` (Soundhouse Dev). Play can't create an app from the API, so upload the
   first bundle of each by hand (build it with the release workflow and take the artifact).
2. Give the Play service account release access to both apps.

Repository secrets (same names as chat-kmp, so the values can be copied if the same upload key and
service account are used):

| Secret | |
|---|---|
| `ANDROID_KEYSTORE_FILE_BASE64_ENCODED` | upload keystore, `base64 -i upload.keystore` |
| `ANDROID_KEYSTORE_ALIAS` | key alias |
| `ANDROID_KEYSTORE_STORE_PASSWORD` | store password |
| `ANDROID_KEYSTORE_KEY_PASSWORD` | key password |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | service account JSON key |

Local release builds without these fall back to the debug key, so `installRelease` still updates a
sideloaded copy. A Play-installed copy is signed differently: switching between the two needs an
uninstall.

## Desktop

One-time, outside this repo: create `homebase-id/soundhouse-desktop-release-bleeding` and
`homebase-id/soundhouse-desktop-release-production`, each with one commit on `main`. Installed apps
update from their `releases/latest/download`.

| Secret | |
|---|---|
| `CONVEYOR_DESKTOP_GITHUB_TOKEN` | token with contents write on both release repos |
| `CONVEYOR_SIGNING_KEY` | Conveyor root key (`conveyor keys generate`, or chat-kmp's) |

Production only (same as chat-kmp's desktop production workflow):
`AZURE_CODESIGN_CLIENT_ID`, `AZURE_CODESIGN_TENANT_ID`, `AZURE_CODESIGN_SUBSCRIPTION_ID` (the Azure
federated credential must trust this repo), `APPLE_SIGNING_KEY` (base64 Developer ID .p12),
`APPLE_SIGNING_KEY_PASS`, `APPSTORE_API_KEY_ID`, `APPSTORE_API_ISSUER_ID`,
`APPSTORE_API_PRIVATE_KEY`.

Conveyor builds every platform from the Linux runner. Each machine gets its own Compose runtime and
ffmpeg/ffprobe jar (`desktopApp/build.gradle.kts`); the workflow sets
`soundhouse.bundleHostFfmpeg=false` so `audio-app`'s jar doesn't also carry the runner's Linux pair.
