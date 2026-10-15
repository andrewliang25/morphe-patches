---
name: probe
description: Build a local logging build that shows which methods run, with which values, while a surface is on the phone screen. Use for step 1 of the development procedure (investigate), and when a patch applies cleanly but changes nothing on the device.
---

# Probe

A probe answers one question by measurement: does this method run while the surface is on the
screen, and with which values? The decompile gives candidates; only the device decides. A probe
is local work: commit it on the local branch if it must survive, and never push it.

## Steps

1. **Ask the question.** Write the question, and the methods that can answer it, from the
   decompile (`work/decompiled-line-<v>/jadx` to read, `apktool` for the smali). Done when each
   candidate has a fingerprint anchor: a kept class name, a framework override, or a string literal.
2. **Write the probe** from `templates/`: copy `ProbePatch.kt` to
   `patches/…/<app>/<topic>/ProbePatch.kt` and `TopicProbe.java` to the extension, then rename
   `TOPIC`/`Topic`. Log on entry of each site, unconditionally, and log each value that the
   question needs. Done when each candidate site has a log call.
3. **Verify the probe** with the `verify-patch` skill: `verify.sh "[Probe] <topic>"` on the version
   that the phone runs. Done when it applies, the sweep is clean, and the diff shows each log call
   where you meant it.
4. **Run it on the phone** with the `device-test` skill. In the test config, put the probe in
   `included-patches` with the patches that the surface needs, and name the bundle
   `patches-<version>-<topic>probe.mpp`. Clear logcat, open the surface, then read
   `adb logcat -d -s AndrewTopic`. Done when each site has logged, or the surface was on the
   screen and a site logged nothing. A silent site is an answer: the code runs somewhere else.
5. **Go one gate further when needed.** If a gate stops the path before the sites you need (a
   flag that is off, a check that fails), force that gate in the probe and run again. Done when
   the log answers the question of step 1.
6. **Bank the result.** Write the anchors, the measured path and the dead ends in the
   `docs/*-map.md` of the app. Then delete the probe files, or keep them only on the local branch.
   Done when the real patch can start from the doc, with no probe code in its diff.

## Reference

- **Log return values** before the `return-object` (the template does this). A `null` result is
  often the answer, for example a check that fails.
- **Register limits.** `invoke-static {vN}` takes v0 to v15 only. In a `.locals 0` method the
  only registers are the parameters, so pass `p0` (the object itself) and read the rest in Java.
- **Who calls it.** `TopicProbe.caller` logs a stack trace. Use it on one site, because a stack
  trace on a hot path floods logcat.
- **Facebook probes** use `COMPATIBILITY_FACEBOOK` and their own tag. Name each probe
  `[Probe] <topic>`, so `list-patches` shows what it is.
- Past probe configs are in `../patched-apps/*-probe-test.toml`: copy the newest one for the app.
