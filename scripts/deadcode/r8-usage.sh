#!/usr/bin/env bash
# Release build without the blanket API keep rule, with R8 -printusage, to list what Android never reaches.
# The ProGuard rules file is restored afterwards whatever happens.
cd "$(dirname "$0")/../.."
WORK="${DEADCODE_WORK:-/tmp/deadcode}"; mkdir -p "$WORK"
RULES=androidApp/proguard-rules.pro
cp "$RULES" "$WORK/proguard-rules.pro.orig"
trap 'cp "$WORK/proguard-rules.pro.orig" "$RULES"' EXIT
python3 - "$RULES" "$WORK/r8-usage.txt" <<'PY'
import sys
p, out = sys.argv[1], sys.argv[2]
s = open(p).read().replace('-keep class id.homebase.api.** { *; }', '')
open(p, 'w').write(s + f'\n-dontoptimize\n-printusage {out}\n')
PY
./gradlew :androidApp:assembleRelease -q > "$WORK/r8.log" 2>&1
echo "r8 exit=$? usage=$WORK/r8-usage.txt"
