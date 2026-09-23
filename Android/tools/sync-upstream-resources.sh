#!/usr/bin/env sh
set -eu

# Run after pulling upstream. The Gradle task executes the same copy during APK builds.
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
upstream_dir="$project_root/../Source/GrasscutterTools/Resources"
target_dir="$project_root/app/src/main/assets/upstream"

if [ ! -d "$upstream_dir" ]; then
  echo "Upstream resource directory is missing: $upstream_dir" >&2
  exit 1
fi

mkdir -p "$target_dir"
for language in zh-cn zh-tw en-us ru-ru; do
  mkdir -p "$target_dir/$language"
  cp "$upstream_dir/$language"/*.txt "$target_dir/$language/"
done
