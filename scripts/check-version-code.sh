#!/usr/bin/env bash
set -euo pipefail

tag=$1
gradle_file=app/build.gradle.kts

version_code_at() {
  local code
  code=$(git show "$1:$gradle_file" | sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p')
  test -n "$code" || { echo "no versionCode in $gradle_file at $1" >&2; exit 1; }
  echo "$code"
}

current=$(version_code_at "$tag")

highest=0
highest_tag=
for other in $(git tag -l 'v*' | grep -vxF "$tag" || true); do
  code=$(version_code_at "$other")
  if [ "$code" -gt "$highest" ]; then
    highest=$code
    highest_tag=$other
  fi
done

if [ -z "$highest_tag" ]; then
  echo "versionCode $current at $tag, no earlier release"
  exit 0
fi

if [ "$current" -le "$highest" ]; then
  echo "versionCode $current at $tag is not above $highest at $highest_tag: Obtainium could not install it over $highest_tag"
  exit 1
fi
echo "versionCode $current at $tag is above $highest at $highest_tag, the highest released"
