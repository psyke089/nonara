#!/usr/bin/env bash
# Cut a release: bump versionCode/versionName, update the About line, run the suite, build the
# signed APK, commit, tag and push. Pushing the tag makes GitHub Actions publish the release.
#
#   tools/release.sh 0.4.0            # full release
#   tools/release.sh 0.4.0 --dry-run  # edit + test + build only, leave changes in the tree
set -euo pipefail

usage() {
  echo "usage: tools/release.sh <version e.g. 0.4.0> [--dry-run]" >&2
  exit 2
}

version=
dry_run=false
for arg in "$@"; do
  case $arg in
    --dry-run) dry_run=true ;;
    -h | --help) usage ;;
    *) [[ -z $version ]] || usage; version=$arg ;;
  esac
done
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  echo "version must look like MAJOR.MINOR.PATCH" >&2
  exit 2
}

cd "$(git rev-parse --show-toplevel)"
[[ -z $(git status --porcelain) ]] || {
  echo "working tree is dirty; commit or stash first" >&2
  exit 1
}
[[ -z $(git tag --list "v$version") ]] || {
  echo "tag v$version already exists" >&2
  exit 1
}

gradle_file=app/build.gradle.kts
screen=app/src/main/java/app/nonara/notes/ui/NotesScreen.kt

code=$(sed -n 's/^val nonaraVersionCode = \([0-9]\+\)$/\1/p' "$gradle_file")
[[ -n $code ]] || {
  echo "cannot read nonaraVersionCode from $gradle_file" >&2
  exit 1
}
next_code=$((code + 1))

sed -i.bak -e "s/^val nonaraVersionCode = [0-9]\+$/val nonaraVersionCode = $next_code/" \
  -e "s/^val nonaraVersionName = \"[^\"]*\"$/val nonaraVersionName = \"$version\"/" "$gradle_file"
rm -f "$gradle_file.bak"
sed -i.bak -E "s/NONARA [0-9]+\.[0-9]+\.[0-9]+/NONARA $version/" "$screen"
rm -f "$screen.bak"

echo "versionCode $code -> $next_code, versionName -> $version"
git diff --stat

./gradlew testDebugUnitTest lintDebug assembleRelease --console=plain

apk=app/build/outputs/apk/release/nonara-$version.apk
[[ -f $apk ]] || {
  echo "expected $apk; is the release APK name still nonara-<version>.apk?" >&2
  exit 1
}
sha256sum "$apk" | tee "$apk.sha256"

if $dry_run; then
  echo "dry run: leaving version bump and APK uncommitted"
  exit 0
fi

git add "$gradle_file" "$screen"
git commit -sm "release $version"
git tag -a "v$version" -m "Nonara $version"
git push origin main
git push origin "v$version"
echo "pushed v$version — GitHub Actions publishes the APK for that tag"
