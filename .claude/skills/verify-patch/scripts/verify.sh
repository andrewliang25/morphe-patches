#!/bin/bash
# Build the bundle, apply patches to each pinned version of an app, list what changed, and sweep branches.
#
#   verify.sh [options] "<patch name>" ["<patch name>" ...]
#
#   --app NAME    The app: the NAME of COMPATIBILITY_<NAME> in Constants.kt, for example LINE or
#                 FACEBOOK. Default: LINE.
#   -v VERSION    A version to apply to. Repeat for more. Default: every version pinned in
#                 COMPATIBILITY_<NAME>.
#   --apk FILE    Apply to this APK or APKM instead of the pinned versions. Repeat for more.
#   --all         Apply every patch of the app in the bundle (default and non-default), not named ones.
#   --default     Apply the default patch set, as the Manager does.
#   --no-build    Use the bundle in patches/build/libs as it is.
#   --mpp FILE    Use this bundle (for example a released .mpp) instead of building. Implies --no-build.
#   --force       Apply to a version that Constants.kt does not pin yet (a new LINE version).
#   --continue    Keep applying the other patches after one fails.
#   --no-diff     Skip the class diff (it is the slow part with --all).
#   --tag TAG     Write to work/verify/<label>-<tag>/, so two runs (for example a released bundle
#                 and this branch) can be compared.
#
# Output goes to work/verify/<label>/: patched.apk, result.json, diff.txt, sweep.txt.
# Exit 1 when the build fails, a patch fails, or a sweep finds a bad branch.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../../.." && pwd)
APKS="$REPO/../patched-apps/temp"
OUT="$REPO/work/verify"
JBR=/Users/andrewliang/Library/Java/JavaVirtualMachines/jbr-21.0.6/Contents/Home
JAVA21="${JAVA21:-$JBR/bin/java}"

app=LINE; versions=(); apks=(); patches=(); mode=named; build=1; diff=1; mpp=""; extra=(); tag=""
while [ $# -gt 0 ]; do
    case "$1" in
        --app) app=$(echo "$2" | tr '[:lower:]' '[:upper:]'); shift 2 ;;
        -v) versions+=("$2"); shift 2 ;;
        --apk) apks+=("$2"); shift 2 ;;
        --all) mode=all; shift ;;
        --default) mode=default; shift ;;
        --no-build) build=0; shift ;;
        --mpp) mpp="$2"; build=0; shift 2 ;;
        --force) extra+=(-f); shift ;;
        --continue) extra+=(--continue-on-error); shift ;;
        --no-diff) diff=0; shift ;;
        --tag) tag="-$2"; shift 2 ;;
        -h|--help) sed -n 2,25p "$0"; exit 0 ;;
        *) patches+=("$1"); shift ;;
    esac
done
if [ "$mode" = named ] && [ ${#patches[@]} -eq 0 ]; then
    echo "Name a patch, or use --all or --default."; exit 2
fi

# The tool jars come from the Gradle cache that the build already fills.
DEXLIB=$(find ~/.gradle/caches/modules-2 -name "smali-dexlib2-*.jar" ! -name "*sources*" | head -1)
GUAVA=$(find ~/.gradle/caches/modules-2 -name "guava-3*-jre.jar" ! -name "*sources*" | head -1)
CLI=$(ls "$REPO"/work/morphe-desktop-*-all.jar 2>/dev/null | tail -1)
[ -n "$CLI" ] || { echo "No work/morphe-desktop-*-all.jar. Download the -all.jar from MorpheApp/morphe-desktop releases."; exit 1; }

if [ $build -eq 1 ]; then
    echo "### build"
    rm -rf "$REPO/patches/build/libs"
    token=$(gh auth token 2>/dev/null)
    (cd "$REPO" && JAVA_HOME=$JBR GITHUB_ACTOR=andrewliang25 GITHUB_TOKEN="$token" \
        ./gradlew :patches:buildAndroid --no-daemon -q 2>&1 | grep -E "^e:|error:|FAILURE")
fi
MPP=${mpp:-$(ls "$REPO"/patches/build/libs/patches-*.mpp 2>/dev/null | head -1)}
[ -n "$MPP" ] || { echo "Build failed: no patches/build/libs/patches-*.mpp"; exit 1; }

CONSTANTS="$REPO/patches/src/main/kotlin/app/andrewliang/patches/shared/Constants.kt"
block=$(awk "/val COMPATIBILITY_$app =/,/^    \\)/" "$CONSTANTS")
[ -n "$block" ] || { echo "No COMPATIBILITY_$app in Constants.kt."; exit 2; }
package=$(echo "$block" | grep -oE 'packageName = "[^"]+"' | head -1 | cut -d'"' -f2)
if [ ${#apks[@]} -eq 0 ]; then
    if [ ${#versions[@]} -eq 0 ]; then
        versions=($(echo "$block" | grep -oE 'version = "[^"]+"' | cut -d'"' -f2))
    fi
    for v in "${versions[@]}"; do
        apk=$(ls "$APKS/$package-$v"-*.apk.apkm 2>/dev/null | head -1)
        [ -n "$apk" ] || apk=$(ls "$APKS/$package-$v"-*.apk 2>/dev/null | head -1)
        [ -n "$apk" ] || { echo "Missing $APKS/$package-$v-<arch>.apk.apkm (or .apk). Put the $app $v file there."; exit 1; }
        apks+=("$apk")
    done
fi

args=()
case $mode in
    named) args+=(--exclusive); for p in "${patches[@]}"; do args+=(-e "$p"); done ;;
    all) args+=(--exclusive)
         while IFS= read -r name; do args+=(-e "$name"); done < <("$JAVA21" -jar "$CLI" list-patches \
             --patches="$MPP" -f "$package" -d=false 2>/dev/null | sed -n 's/^Name: //p') ;;
    default) ;;
esac

status=0
for apk in "${apks[@]}"; do
    label=$(basename "$apk" | sed -E "s/^$package-//; s/\.apkm$//; s/\.apk$//; s/-(arm64-v8a|armeabi-v7a|all|universal)$//")
    dir="$OUT/$label$tag"
    rm -rf "$dir"; mkdir -p "$dir"
    echo "### $label$tag"
    "$JAVA21" -jar "$CLI" patch -p "$MPP" "${args[@]}" ${extra[@]+"${extra[@]}"} -o "$dir/patched.apk" -r "$dir/result.json" \
        -t "$dir/tmp" "$apk" > "$dir/patch.log" 2>&1
    rm -rf "$dir/tmp"
    if [ ! -f "$dir/result.json" ]; then
        echo "  patching did not finish, see $dir/patch.log"; grep -E "SEVERE|Exception" "$dir/patch.log" | head -5
        status=1; continue
    fi
    python3 -I - "$dir/result.json" <<'EOF' || status=1
import json, sys
result = json.load(open(sys.argv[1]))
applied = [p["name"] for p in result.get("appliedPatches", [])]
failed = result.get("failedPatches", [])
print(f"  applied {len(applied)}, failed {len(failed)}")
for f in failed:
    print("  FAILED", f.get("name"), "-", str(f.get("exception") or f.get("error") or f)[:300])
sys.exit(1 if failed else 0)
EOF

    # Original dex files come from base.apk inside the APKM; the patched ones from the output.
    orig="$OUT/.orig/$label"
    if [ ! -d "$orig" ]; then
        mkdir -p "$orig/x"
        if [[ "$apk" == *.apkm ]]; then
            unzip -q -o "$apk" base.apk -d "$orig/x" && unzip -q -o "$orig/x/base.apk" 'classes*.dex' -d "$orig"
        else
            unzip -q -o "$apk" 'classes*.dex' -d "$orig"
        fi
        rm -rf "$orig/x"
    fi
    mkdir -p "$dir/dex"
    unzip -q -o "$dir/patched.apk" 'classes*.dex' -d "$dir/dex"

    if java -cp "$DEXLIB:$GUAVA" "$HERE/BranchSweep.java" "$dir"/dex/*.dex > "$dir/sweep.txt" 2>&1; then
        echo "  sweep: OK"
    else
        echo "  sweep: FAILED, see $dir/sweep.txt"; head -5 "$dir/sweep.txt"; status=1
    fi

    if [ $diff -eq 1 ]; then
        java -cp "$DEXLIB:$GUAVA" "$HERE/DexDiff.java" "$orig" "$dir/dex" > "$dir/diff.txt" 2>&1
        echo "  $(grep '^SUMMARY' "$dir/diff.txt")"
        echo "  changed classes:"; grep '^== ' "$dir/diff.txt" | sed 's/^== /    /'
        echo "  diff: $dir/diff.txt"
    fi
done
exit $status
