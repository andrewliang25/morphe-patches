#!/bin/bash
# Cold-start LINE and open one row of its Settings screen, for example:
#   line-settings.sh "Fonts"
#   line-settings.sh "Andrew's Patch Setting"
# With no argument, it stops on the Settings screen. Exit 1 when a step does not find its target.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
UI="python3 -I $HERE/ui.py"
ADB=$(python3 -I -B -c "import sys; sys.path.insert(0, '$HERE'); import ui; print(ui.ADB)")

$UI device || exit 1
"$ADB" shell am force-stop jp.naver.line.android
"$ADB" shell monkey -p jp.naver.line.android -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
$UI wait "Home" 20 || { echo "LINE did not reach its main screen. Is the phone locked?"; exit 1; }
$UI tap "Home" || exit 1
$UI wait "Settings button" || exit 1
$UI tap "Settings button" || exit 1
[ $# -eq 0 ] && exit 0
$UI wait "Settings" || exit 1
$UI scroll-tap "$1" 8 || exit 1
sleep 3
