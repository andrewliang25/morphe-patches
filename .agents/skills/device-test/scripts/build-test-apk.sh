#!/bin/bash
# Build a test APK of this branch through ../patched-apps (step 3 of the procedure in AGENTS.md).
#
#   build-test-apk.sh [--app LINE|Facebook] <tag> <app version> ["<patch name>" ...]
#
#   <tag>          A short name for this test, for example callrec or fontprobe. Letters, digits, '-'.
#   <app version>  The version on the phone: ui.py adb shell dumpsys package <pkg> | grep versionName
#   patch names    Patches to add to the app's default set, for example a probe or a patch that is
#                  off by default. With none, the build has the default set only.
#
# It uses the bundle in patches/build/libs, so run verify.sh first. It stages the bundle as
# temp/andrewliang25-rv/patches-<bundle version>-<tag>.mpp (build.sh finds a local bundle only by
# this name), writes temp/<tag>-test.toml, runs the Docker build (about 10 minutes: run this in the
# background) and renames the APK to build/<app>-<tag>-<version>.apk, so the next build does not
# overwrite it. The log is temp/build-<tag>.log. Exit 1 when a step fails or a named patch is not
# applied.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../../.." && pwd)
PA=$(cd "$REPO/../patched-apps" && pwd) || { echo "No ../patched-apps next to the repository."; exit 1; }

app=LINE
[ "${1:-}" = --app ] && { app=$2; shift 2; }
[ $# -ge 2 ] || { sed -n 2,16p "$0"; exit 2; }
tag=$1 version=$2; shift 2
[[ "$tag" =~ ^[A-Za-z0-9-]+$ ]] || { echo "The tag may hold only letters, digits and '-'."; exit 2; }
case $(echo "$app" | tr '[:upper:]' '[:lower:]') in
    line) app=LINE pkg=jp.naver.line.android extra="" ;;
    facebook) app=Facebook pkg=com.facebook.katana
        extra='uptodown-dlurl = "https://facebook.en.uptodown.com/android"' ;;
    *) echo "Unknown app $app. Add it to the case list in this script."; exit 2 ;;
esac
app_l=$(echo "$app" | tr '[:upper:]' '[:lower:]')

MPP=$(ls "$REPO"/patches/build/libs/patches-*.mpp 2>/dev/null | head -1)
[ -n "$MPP" ] || { echo "No bundle in patches/build/libs. Run verify.sh first."; exit 1; }
newer=$(find "$REPO/patches/src" "$REPO/extensions/extension/src" -type f -newer "$MPP" | head -1)
[ -z "$newer" ] || { echo "The bundle is older than $newer. Run verify.sh again."; exit 1; }
bundle_ver=$(basename "$MPP" .mpp); bundle_ver=${bundle_ver#patches-}

# Two Docker builds at once share temp/ and build/, and each overwrites the other's files.
LOCK="$PA/temp/.test-build.lock"
mkdir "$LOCK" 2>/dev/null || { echo "Another test build is running ($LOCK). Wait for it."; exit 1; }
trap 'rmdir "$LOCK"' EXIT

mkdir -p "$PA/temp/andrewliang25-rv"
cp "$MPP" "$PA/temp/andrewliang25-rv/patches-$bundle_ver-$tag.mpp"
included=""
for p in "$@"; do included+="'$p' "; done
{
    echo "parallel-jobs = 1"
    echo
    echo "# Test build $tag ($(git -C "$REPO" branch --show-current)), written by build-test-apk.sh."
    echo "[$app-Andrew]"
    echo "app-name = \"$app\""
    echo 'patches-source = "andrewliang25/morphe-patches"'
    echo "patches-version = \"v$bundle_ver-$tag\""
    echo 'cli-source = "MorpheApp/morphe-cli"'
    echo 'rv-brand = "Andrew"'
    echo 'build-mode = "apk"'
    echo 'include-stock = "disable"'
    echo "version = \"$version\""
    echo 'arch = "arm64-v8a"'
    [ -n "$included" ] && echo "included-patches = \"${included% }\""
    echo "archive-dlurl = \"https://archive.org/download/andrews-apks/apks/$pkg\""
    [ -n "$extra" ] && echo "$extra"
} > "$PA/temp/$tag-test.toml"

LOG="$PA/temp/build-$tag.log"
echo "Building $app $version with patches-$bundle_ver-$tag.mpp. Log: $LOG"
docker run --rm --platform linux/amd64 -v "$PA":/repo -w /repo rvmm-build \
    ./build.sh "temp/$tag-test.toml" > "$LOG" 2>&1
status=$?

OUT="$PA/build/$app_l-andrew-v$version-arm64-v8a.apk"
[ $status -eq 0 ] && [ -f "$OUT" ] || { echo "Build failed (exit $status). The end of the log:"; tail -20 "$LOG"; exit 1; }
APK="$PA/build/$app_l-$tag-$version.apk"
mv -f "$OUT" "$APK"

fail=0
for p in "$@"; do
    grep -qF "Applied: $p" "$LOG" || { echo "NOT APPLIED: $p"; fail=1; }
done
echo "$(grep -c 'Applied: ' "$LOG") patches applied."
grep -iE "SEVERE|failed to apply" "$LOG" | head -10
echo "APK: $APK"
exit $fail
