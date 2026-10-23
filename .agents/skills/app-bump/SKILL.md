---
name: app-bump
description: Add support for a new version of a patched app (LINE, Facebook, or any app in Constants.kt) - decompile it, apply every patch of the app, re-anchor the ones that drift, prove each site changed on every supported version, and pin the version. Use when a new app release must be supported or audited.
---

# App version bump

R8 renames an app's classes on each release, so patches break by **drift**, not by removed
features. Every fingerprint must match every version that the app pins, with no table of names
per version. This skill uses the `verify-patch` skill for each apply (`--app <APP>` selects
`COMPATIBILITY_<APP>` in `Constants.kt`) and the `device-test` skill for the phone. Read the
notes for your app at the end of this file before step 1.

## Steps

1. **Get the APK.** Put it at `../patched-apps/temp/<package>-<new>-<arch>.apk.apkm`, or a merged
   `.apk` with the same name. If the user has none, ask for it (the `archive-dlurl` of the app in
   the test configs points at their archive). Read `minSdkVersion` and `versionCode` from
   `base.apk`, and check that the dex files are plain (`unzip -l base.apk | grep classes`). Done
   when the file is there and you know its minSdk.
2. **Decompile.** Unzip `base.apk` to `work/apkm-extract-<app>-<new>/`, then `apktool d` to
   `work/decompiled-<app>-<new>/apktool` and `jadx -q --no-res -j 8` to
   `work/decompiled-<app>-<new>/jadx` (jadx takes about 10 minutes, so run it in
   the background). LINE keeps its older name, `work/decompiled-line-<v>/`. Done when both
   folders exist.
3. **First apply.** On a `bump/<app>-<new>` branch off `dev`:
   `verify.sh --app <APP> --all --force --continue --no-diff -v <new>`. Done when you have the list
   of failed patches. A patch that applies can still have skipped a site: step 5 finds those.
4. **Re-anchor each failure,** one family of patches at a time, with a `verify.sh` run per family.
   Map each old anchor to the new class through a name that R8 keeps: a string literal, an enum
   constant name, a resource name, a Kotlin `toString` text, or the method shape. Read "Traps from
   earlier bumps" below first. A peer project that already supports the version (see the app notes)
   can name the drifted sites, but check each one in the decompile. Done when
   `verify.sh --app <APP> --all --force` applies every patch on every version, with 0 failed.
5. **Prove each site:**
   - Run `verify.sh --app <APP> --all --force` (with diffs) on every version to support.
   - Pair the changed methods of the versions by role. Each version must change the same number.
   - On the old version, run the released bundle
     (`verify.sh --app <APP> --all --mpp <released .mpp> --tag released -v <old>`) and compare its
     changed methods with this branch on the old version. They must be the same.
   - Read each site that uses a fixed register on the new version: the register must be dead there.
   Done when the counts match, the old version matches the release, and every sweep is OK.
6. **Check stored signer values.** Search the patches of the app for a hardcoded certificate
   (`grep -rnE '"[0-9A-Fa-f]{40}"' patches/src/main/kotlin/app/andrewliang/patches/<app>`). For each
   hit, compare it with `apksigner verify --print-certs` on the new `base.apk` (apksigner is in
   `~/Library/Android/sdk/build-tools/<newest>/`). Done when each stored value still matches, or the
   patch has the new one.
7. **Pin the version.** Add an `AppTarget` with the new version to `COMPATIBILITY_<APP>` in
   `Constants.kt`. Ask the user whether to keep the old version, and say why it matters (a minSdk
   increase drops older Android devices). Done when `verify.sh --app <APP> --all` applies with no
   `--force`.
8. **Device test** each version that the phone can run, with the `device-test` skill: the default
   set, and each surface that a re-anchored patch touches. Done when each re-anchored surface shows
   its change on the phone, with no crash.
9. **Update the docs** of the app (see the app notes): a "What changed in <new>" section with the
   new names, the traps and the method count, and the new descriptor names in the sections of the
   patches that moved. Done when no doc names a class that does not exist in a supported version.
10. **Commit, push, PR** as in step 6 of the procedure in AGENTS.md, with these differences: put
   the code in one `bump(<App>): add support for <App> <new>` commit (`bump:` files under
   "Updated App Support"), and the docs in separate `docs:` commits. If a version is dropped, that
   is a breaking change: ask the user first. Merge the PR as a plain merge, not a squash.

## Reference

### Traps from earlier bumps

- **When obfuscation eats your anchor, look for what R8 must keep:** Kotlin enum-constant names,
  string literals, resource names (get ids by name through `res/values/public.xml`, because ids
  change), Kotlin `@Metadata` module names (`"square-ad-impl"` located the whole AdManager wrapper
  package), inflated layout resource names, and classes kept by name for reflection (such as a
  WorkManager `ListenableWorker` subclass). Find an obfuscated class by searching for one of those
  and comparing the constant set or method shape with the old tree.
- **A hardcoded resource id** points at another view after a bump (on LINE it hid the bottom-bar
  LIVE badge). Get ids by name (`shared/ResourceIds.kt`).
- **A string literal is durable only while it stays in a method whose signature you also pin.**
  `hidepremium` anchored on `"LITE_ENJOY"` inside a `()Z` accessor. In 26.14.0 R8 deleted the
  accessor and moved the predicate into a suspend evaluator, so the fingerprint matched zero methods
  while the string still existed in the APK. Shape-based lookups worked better (`keepunsent`
  absorbed `h()` → `g()` with no edit). Prefer instruction shape over method name, and expect a
  pinned signature to be the part that breaks.
- **R8 moves code, not only names:** a field read or a lambda can move into another method, a method
  prologue, or one `packed-switch`. That move can empty a patch's "search after the guard" window
  while every descriptor in it stays valid. A method can also gain a parameter, which breaks a
  hand-built register range. When a patch fails while its fingerprint still resolves, diff the
  instruction order of the matched method across versions, not only its names.
- **A string can move into a shared lookup list,** so a fingerprint on it hits another method.
- **Never blind find/replace a descriptor map across versions.** Prefix renames chain (`fg1`→`wi1`
  and `wi1`→`nl1` in the same release, so a two-pass replace produced a class that did not exist).
  Member letters do not always survive (`hg1.r`→`yi1.p`, `hg1.k`→`yi1.j`). Also, `\b` does not match
  between `L` and the prefix of a JVM descriptor, so `\bfg1/` skips every `Lfg1/…;`. Map each
  `(prefix, member)` pair in a single pass through a lookup table.
- **A `methodOrNull` site that drifts** applies with no error. Only the method diff of step 5 shows
  it.
- **A `check-cast` satisfies the verifier, not the run time.** BranchSweep and a dump both pass, and
  the cast throws `ClassCastException` on the phone. A Kotlin default-argument constructor can store
  its own default in the field with another type than the argument.

### LINE (`--app LINE`)

- The bundle supports two versions (26.14.0 for Android 11 and 12, 26.15.1 for 12L and later).
  Read "Two supported versions" and "How to check a bump" in `docs/line-patch-map.md`.
- From 26.15.1, R8 also renames the Kotlin stdlib (`kotlin.Unit`, `Lazy`, function types).
- Shared helpers already absorb drift: `shared/ResourceIds.kt`, `line/shared/HomeState.kt`,
  `line/shared/AttachTile.kt`. Prefer them to a new anchor.
- Step 6 finds the two signer SHA-1s of "[Fix] Restore push notifications". `base.apk` uses
  key rotation: `docs/line-patch-map.md` ("Which cert") says which of the two certificates the patch
  uses.
- Docs: `docs/line-patch-map.md` and `docs/line-premium-map.md`. Past bumps: 26.14.0 (16 patches
  broke), 26.15.1 (18 of 30).

### Facebook (`--app FACEBOOK`)

- The Facebook patches are in keep-alive mode on the pinned version. Bump only when the user asks
  or the pinned version stops working. Do not bump early.
- Use the APKMirror "(Android 11+)" bundle. The "(Android 9+)" bundles keep most code in a
  Superpack `.spo` file that the patcher cannot read, so most patches fail with "Could not find
  class". That is the wrong file, not drift. One `classes.dex` in `base.apk` means this case.
- Facebook strips resource names and renames every class and method about every two weeks.
  Anchor on string literals, GraphQL names and method shape.
- SysAdminDoc/HushFacebook (a fork of these patches) publishes the drift fixes of newer versions
  in its changelog.
- Docs: `docs/facebook-ads-map.md` and `docs/facebook-theme-map.md`.

### Another app

Add a section here on the first bump of a new app: the docs file, the stored signer values, and
any trap that is special to the app.
