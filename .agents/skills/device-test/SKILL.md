---
name: device-test
description: Drive the connected Android phone through adb to test a patch build. Use when a patch reaches device testing (step 3 of the development procedure), or to install a build, open a screen, take a screenshot, sample a color or read logcat on the phone.
---

# Device test

Test a patch on the real phone yourself, including opening the screens. The user connects and unlocks the phone; ask them only for what adb cannot do (login, biometric prompts, judging a look that a screenshot does not settle).
`scripts/ui.py` drives the screen and `scripts/line-settings.sh` opens a LINE Settings row. Run
`python3 -I scripts/ui.py` with no argument for its command list.

## Steps

1. **Preflight.** Run `ui.py device`. Done when it exits 0. If it prints "No device" or "locked",
   ask the user to connect or unlock the phone, and wait for their reply. With two phones
   connected, set `ANDROID_SERIAL` to the phone under test on each command. Run raw adb commands
   as `ui.py adb ARGS...`, which finds the adb binary for you.
2. **Build the test APK** with `scripts/build-test-apk.sh <tag> <app version> ["<patch>" ...]`
   (`--app Facebook` for Facebook) after a `verify.sh` run, in the background: it takes about
   10 minutes. It adds the named patches to the default set, stages the bundle under the name
   that `../patched-apps` needs, and prints the APK path. Done when it exits 0: each named patch
   shows `Applied:` in the log.
3. **Install** with `adb install -r <apk>` (keeps the app data and the login). First read the
   version on the phone (`dumpsys package jp.naver.line.android | grep versionName`). A build of an
   older version fails with `INSTALL_FAILED_VERSION_DOWNGRADE`: build for the phone's version
   rather than uninstall, because an uninstall logs the user out. Done when adb prints `Success`.
4. **Drive and observe.** Clear logcat (`adb logcat -c`), open the surface, act, and take
   evidence: `ui.py shot <tmp dir>/<name>.png`, then view the `-small` copy. Done when the
   screen shows the expected change and `adb logcat -d | grep -E "FATAL|VerifyError"` is empty.
5. **Restore the phone.** Put back each setting you changed (theme, dark mode, a patch switch),
   and tell the user which test files you left on the phone. Done when the phone is as the user
   left it, apart from the new build.

## Reference

### `ui.py`

- `tap TEXT` matches the whole text or content description. Use `tap ~TEXT` for a part only when
  the whole text varies: a part also matches headers and hints (`~Apply` once tapped a sentence
  that contained "Apply", not the **Apply** button).
- `scroll-tap` and `wait` read the screen size and rotation each time, so they work in landscape.
  Fixed coordinates do not: the phone sometimes turns to landscape between runs.
- `pixel` takes a **full-size** screenshot (the `shot` PATH, not the `-small` copy). Use it to
  compare an added view with the page around it.
- uiautomator also sees Jetpack Compose text, so `list` works on Compose screens.

### Connection traps

- The USB link drops, most often during a large install (`adb: failed to install` with no reason,
  then `no devices`). Ask the user for another cable or port. Do not retry the same cable in a loop.
- A command that starts while the phone is away waits (`- waiting for device -`) and runs later,
  on whatever build is installed then. After a reconnect, run `ui.py device` and install again
  before you trust a result.
- macOS has no `timeout` command. Set a timeout on the shell call instead.
- A locked phone needs the user: `wm dismiss-keyguard` and swipes do not pass a PIN or an
  always-on display.

### LINE

- `line-settings.sh "<row>"` cold-starts LINE, then opens Home ▸ Settings ▸ `<row>`, for example
  `Fonts` or `Andrew's Patch Setting`.
- LINE is not debuggable, so `run-as` fails: you cannot read its files. Read state from the screen
  or from logcat.
- A probe logs with its own tag (`Log.d("Andrew…", …)`). Filter logcat by that tag, and log on
  entry, so a probe that never runs is visible as an empty log.
- LINE sometimes opens its own promo sheet over a page (for example the new-font sheet on the
  Font page). `ui.py has` its text, then close it with `adb shell input keyevent KEYCODE_BACK`.
- Themes: Settings ▸ Themes ▸ My Themes. Selecting cards there builds a rotation. Apply with the
  **Apply (n)** button. Dark mode: `adb shell cmd uimode night yes|no`.
