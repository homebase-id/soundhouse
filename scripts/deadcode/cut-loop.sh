#!/usr/bin/env bash
# Stage your own edits first: this resets unstaged changes between rounds.
# Cut every candidate in $WORK/deadmembers.json, compile, turn errors into exclusions, recut from clean,
# until it compiles. Needs a clean working tree (it resets unstaged changes between rounds).
cd "$(dirname "$0")/../.."
WORK="${DEADCODE_WORK:-/tmp/deadcode}"
cp "$WORK/deadmembers.json" "$WORK/r8cands.json"; rm -f "$WORK/excl.json"
for round in $(seq 1 14); do
  git checkout -q -- .
  python3 scripts/deadcode/cutmembers.py >/dev/null
  scripts/deadcode/compile-all.sh "$@"
  [ -s "$WORK/compile-errors.txt" ] || { echo "clean after round $round: $(git diff --shortstat)"; exit 0; }
  python3 scripts/deadcode/refine.py
done
echo "did not converge; resetting"; git checkout -q -- .; exit 1
