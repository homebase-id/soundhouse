#!/usr/bin/env bash
# Usage: ci-version.sh <run_number>
# Version code = version.code.base + run number; version name = MAJOR.MINOR.<version code>.
# Rewrites gradle/version.properties so builds that read it directly (desktop) agree, and writes
# VERSION_NAME/VERSION_CODE to $GITHUB_OUTPUT when set.
set -euo pipefail
cd "$(dirname "$0")/../.."
props=gradle/version.properties
base=$(grep '^version.code.base=' "$props" | cut -d= -f2)
name=$(grep '^version.name=' "$props" | cut -d= -f2)
code=$((base + $1))
full="$(echo "$name" | cut -d. -f1-2).$code"
sed -i.bak "s/^version.name=.*/version.name=$full/" "$props" && rm "$props.bak"
echo "Version: $full ($code)"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "VERSION_NAME=$full" >> "$GITHUB_OUTPUT"
  echo "VERSION_CODE=$code" >> "$GITHUB_OUTPUT"
fi
