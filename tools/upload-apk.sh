#!/usr/bin/env bash
# Upload the newest signed Nonara release APK and its SHA-256 to Google Drive.
#
# One-time setup (credentials stay outside the repo):
#   sudo pacman -S rclone
#   rclone config          # new remote -> "gdrive" -> Google Drive -> OAuth in browser
#
# Usage:
#   tools/upload-apk.sh                          # gdrive:Nonara/releases
#   tools/upload-apk.sh gdrive:Nonara/0.2.0
#   NONARA_DRIVE_TARGET=gdrive:Backups tools/upload-apk.sh
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
apk_dir=$repo_root/app/build/outputs/apk/release
target=${1:-${NONARA_DRIVE_TARGET:-gdrive:Nonara/releases}}

command -v rclone >/dev/null 2>&1 || {
  echo "rclone is required: sudo pacman -S rclone, then 'rclone config' to add a Google Drive remote" >&2
  exit 1
}

apk=$(ls -t "$apk_dir"/nonara-*.apk 2>/dev/null | head -n 1)
[[ -n $apk ]] || {
  echo "no release APK in $apk_dir — run ./gradlew assembleRelease first" >&2
  exit 1
}

name=$(basename "$apk")
cd "$apk_dir"
sha256sum "$name" >"$name.sha256"

echo "uploading $name ($(du -h "$name" | cut -f1)) -> $target"
rclone copy "$name" "$target" --progress
rclone copy "$name.sha256" "$target"

echo "done: $target/$name"
