#!/usr/bin/env bash
# Delete files the reachability pass finds dead, compile, and list failing files (main vs test).
cd "$(dirname "$0")/../.."
WORK="${DEADCODE_WORK:-/tmp/deadcode}"; mkdir -p "$WORK"
python3 scripts/deadcode/reach.py | head -1
python3 -c "import json,sys;print('\n'.join(json.load(open(sys.argv[1]))))" "$WORK/reach-dead.json" > "$WORK/batch.txt"
[ -s "$WORK/batch.txt" ] && xargs git rm -q < "$WORK/batch.txt"
scripts/deadcode/compile-all.sh "$@"
sed -E 's#^e: ([^:]+):.*#\1#' "$WORK/compile-errors.txt" | sort -u > "$WORK/err-files.txt"
echo "main-source error files: $(grep -viE '/src/[a-zA-Z]*test/' "$WORK/err-files.txt" | wc -l | tr -d ' ')"
grep -viE '/src/[a-zA-Z]*test/' "$WORK/err-files.txt"
echo "test error files: $(grep -iE '/src/[a-zA-Z]*test/' "$WORK/err-files.txt" | wc -l | tr -d ' ')"
grep -iE '/src/[a-zA-Z]*test/' "$WORK/err-files.txt"
