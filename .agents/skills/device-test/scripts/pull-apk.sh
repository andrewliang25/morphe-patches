#!/bin/bash
# Copy an installed app from the phone as an .apkm (step 1 of the app-bump skill).
#
#   pull-apk.sh <package>       for example: pull-apk.sh jp.naver.line.android
#
# It pulls base.apk and every split to ~/Downloads/<package>-<version>/, zips them into
# <package>-<version>-<abi>.apkm there, and copies that file to
# ../patched-apps/temp/<package>-<version>-<abi>.apk.apkm, where verify.sh and the Docker build look.
# It prints the versionName and versionCode to pin. With two phones, set ANDROID_SERIAL first.
set -eu
[ $# -eq 1 ] || { sed -n 2,9p "$0"; exit 2; }
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../../../.." && pwd)
pkg=$1
ADB=$(python3 -I -B -c "import sys; sys.path.insert(0, sys.argv[1]); import ui; print(ui.ADB)" "$HERE")
python3 -I "$HERE/ui.py" device > /dev/null || python3 -I "$HERE/ui.py" device

info=$("$ADB" shell dumpsys package "$pkg" | tr -d '\r')
ver=$(echo "$info" | grep -m1 -oE 'versionName=[^ ]+' | cut -d= -f2)
code=$(echo "$info" | grep -m1 -oE 'versionCode=[0-9]+' | cut -d= -f2)
[ -n "$ver" ] || { echo "$pkg is not installed on the phone."; exit 1; }
abi=$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')

D="$HOME/Downloads/$pkg-$ver"
mkdir -p "$D"
"$ADB" shell pm path "$pkg" | tr -d '\r' | sed 's/^package://' | while read -r path; do
    "$ADB" pull "$path" "$D/" > /dev/null
done
APKM="$pkg-$ver-$abi.apkm"
(cd "$D" && rm -f "$APKM" && zip -qX "$APKM" ./*.apk)
cp "$D/$APKM" "$REPO/../patched-apps/temp/$pkg-$ver-$abi.apk.apkm"
echo "$pkg $ver (versionCode $code, $abi): $(cd "$D" && ls ./*.apk | tr '\n' ' ')"
echo "$D/$APKM"
echo "patched-apps/temp/$pkg-$ver-$abi.apk.apkm"
