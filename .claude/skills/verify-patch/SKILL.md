---
name: verify-patch
description: Build the bundle, apply patches to every pinned version of their app (LINE, Facebook), list the classes and instructions each patch changed, and run BranchSweep. Use after each patch edit, before a device test, and when a patch fails to apply or seems to change nothing.
---

# Verify a patch

`scripts/verify.sh "<patch name>"` does the whole check that CLAUDE.md asks for after an edit
("Always verify by APPLYING", the BranchSweep note). It takes 1 to 3 minutes per app version.
`--app NAME` selects `COMPATIBILITY_<NAME>` in `Constants.kt` (default `LINE`). `--help` lists
every option.

```bash
.claude/skills/verify-patch/scripts/verify.sh "[General] Custom font"            # build + both LINE versions
.claude/skills/verify-patch/scripts/verify.sh --no-build -v 26.15.1 "<a>" "<b>"  # one version, two patches
.claude/skills/verify-patch/scripts/verify.sh --all --no-diff                     # every LINE patch, fast
.claude/skills/verify-patch/scripts/verify.sh --app facebook "[Reels] Picture-in-picture"
```

Run it with `run_in_background` when it covers both versions or `--all`.

## Steps

1. **Run** `verify.sh` with the patches that you changed. Done when it prints a block for each
   pinned version.
2. **Read the result** of each version:
   - `applied N, failed 0`. A `FAILED` line names the patch and its exception. A fingerprint that
     resolves on one version only usually means drift: diff the matched method of the two
     versions (`work/decompiled-<app>-<v>/`, `work/decompiled-line-<v>/` for LINE).
   - `sweep: OK`. A failure lists each method with a branch, try range or handler that does not
     land on an instruction start.
   - `changed classes`: each class that you meant to change, and no other. A missing class means
     a `methodOrNull` site that skipped with no error.
3. **Read the diff** in `work/verify/<version>/diff.txt` for each changed method. Done when each
   `+` and `-` line is an edit that you meant, on both versions, and the registers in it are free
   at that point.

## Reference

### What the diff shows

- One line per instruction, with the registers and the reference, for example
  `invoke-static {v4}, Lapp/…/LineSettings;->customFont(Z)Z`. Branch targets print as `:L0`,
  `:L1` in address order, so an inserted block does not renumber the branches after it.
- `SUMMARY` counts the changed classes, the extension classes (`Lapp/andrewliang/`), and the other
  new classes by package. The extension bundle carries the Kotlin standard library, so on 26.15.1
  (which obfuscates its own copy) about 1,000 `kotlin` classes are new. Any other new package is
  worth a look.
- A class that the patcher moved to another dex file counts as changed only when its content
  changed.

### What it cannot prove

- That the code runs. Only a device run shows that (the `device-test` skill), and a probe shows
  which path runs (the `probe` skill).
- That the register types are right at run time. A `check-cast` or a `(Z)Z` call on an `int`
  register passes the verifier here and can still fail on the device. Read each injected block
  whole, with its neighbours.

### Files and inputs

- APKs: `../patched-apps/temp/<package>-<version>-<arch>.apk.apkm`, or a merged `.apk` with the
  same name (Facebook). The package and the pinned versions come from `COMPATIBILITY_<APP>` in
  `Constants.kt`. `--apk FILE` takes any other APK or APKM.
- Output: `work/verify/<label>/` holds `patched.apk`, `result.json`, `patch.log`, `diff.txt` and
  `sweep.txt`. The original dex files are cached in `work/verify/.orig/`.
- The patch CLI is `work/morphe-desktop-*-all.jar` (the `-all.jar` of MorpheApp/morphe-desktop).
  The build runs on JBR 21, and the Java tools run on the default `java` (17 or newer).
