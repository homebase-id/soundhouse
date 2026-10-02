#!/usr/bin/env bash
# Commit gate: JVM + Android + iOS-simulator compile (main and test) and jvmTest for every
# library module, in one Gradle call. Pass --apps to also assemble the Android and Desktop apps.
set -euo pipefail
cd "$(dirname "$0")/.."

MODULES=(homebase-api homebase-common homebase-auth)
APP_MODULES=()
[[ -d audio-app ]] && MODULES+=(audio-app)

TASKS=()
for m in "${MODULES[@]}"; do
  TASKS+=(":$m:compileKotlinJvm" ":$m:compileTestKotlinJvm" ":$m:compileAndroidMain"
          ":$m:compileAndroidHostTest" ":$m:compileKotlinIosSimulatorArm64"
          ":$m:compileTestKotlinIosSimulatorArm64" ":$m:jvmTest")
done
if [[ "${1:-}" == "--apps" ]]; then
  [[ -d androidApp ]] && TASKS+=(":androidApp:assembleDebug")
  [[ -d desktopApp ]] && TASKS+=(":desktopApp:createDistributable")
fi

exec ./gradlew --continue "${TASKS[@]}"
