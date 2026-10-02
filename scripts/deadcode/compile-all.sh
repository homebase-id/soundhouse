#!/usr/bin/env bash
# Compile every module (main + tests) for all targets, no tests run. --fast skips iOS.
# Errors land in $DEADCODE_WORK/compile-errors.txt as repo-relative paths.
cd "$(dirname "$0")/../.."
WORK="${DEADCODE_WORK:-/tmp/deadcode}"; mkdir -p "$WORK"
T=()
for m in homebase-api homebase-common homebase-auth audio-app; do
  T+=(":$m:compileKotlinJvm" ":$m:compileTestKotlinJvm" ":$m:compileAndroidMain" ":$m:compileAndroidHostTest")
  [[ "${1:-}" == "--fast" ]] || T+=(":$m:compileKotlinIosSimulatorArm64" ":$m:compileTestKotlinIosSimulatorArm64")
done
T+=(":androidApp:compileDebugKotlin" ":desktopApp:compileKotlinJvm")
./gradlew --continue -q "${T[@]}" > "$WORK/compile.log" 2>&1
rc=$?
grep -E "^e: " "$WORK/compile.log" | sed -E "s#file://$(pwd)/##" | sort -u > "$WORK/compile-errors.txt"
echo "compile exit=$rc errors=$(wc -l < "$WORK/compile-errors.txt" | tr -d ' ')"
