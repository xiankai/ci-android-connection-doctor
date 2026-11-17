#!/usr/bin/env bash
set -euo pipefail

tag=$1
gradle_file=app/build.gradle.kts

version_code_at() {
  git show "$1:$gradle_file" | sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p'
}

current=$(version_code_at "$tag")
test -n "$current" || { echo "no versionCode in $gradle_file at $tag"; exit 1; }

previous_tag=$(git tag -l 'v*' --sort=-v:refname | grep -vxF "$tag" | head -n 1 || true)
if [ -z "$previous_tag" ]; then
  echo "versionCode $current at $tag, no earlier release"
  exit 0
fi

previous=$(version_code_at "$previous_tag")
if [ "$current" -le "$previous" ]; then
  echo "versionCode $current at $tag is not above $previous at $previous_tag: Obtainium could not install it over $previous_tag"
  exit 1
fi
echo "versionCode $current at $tag is above $previous at $previous_tag"
