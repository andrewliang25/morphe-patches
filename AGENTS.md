# AGENTS.md

Guidance for AI coding agents working in this repository. `CLAUDE.md` is a symlink to this file, and `.claude/skills` is a symlink to `.agents/skills`.

## What this is

**Andrew's Patches** is a **Morphe Patches** bundle (Morphe is a fork of the ReVanced patching ecosystem). It builds an `.mpp` bundle that the Morphe CLI / Manager applies to third-party Android APKs to rewrite their bytecode. Current focus: **LINE** (`jp.naver.line.android`).

Base group `app.andrewliang` is app-agnostic. Per-app patches nest under `app.andrewliang.patches.<app>` (for example `…patches.line`). Target-app compatibility and the developed-against version live in `app.andrewliang.patches.shared.Constants`.

## Commands

```bash
# Build the patch bundle -> patches/build/libs/patches-*.mpp
./gradlew buildAndroid

# Build, then regenerate patches-list.json from the compiled bundle
./gradlew generatePatchesList

# Compile-check without producing a release (what CI runs on non-release commits)
./gradlew :patches:buildAndroid clean --no-daemon
```

There is no test suite. Correctness is checked by applying the built `.mpp` to a real target APK. `generatePatchesList` loads the built `.mpp` by reflection, so it needs a prior build.

`settings.gradle.kts` pulls the `app.morphe.patches` plugin and patcher libraries from GitHub Packages (`maven.pkg.github.com/MorpheApp/registry`), which needs `gpr.user`/`gpr.key` Gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars with a PAT that can read them.

**Without a PAT:** If the artifacts are already in the Gradle cache, build offline with dummy credentials: `./gradlew :patches:buildAndroid --offline --no-daemon -Pgpr.user=dummy -Pgpr.key=dummy`. Full offline build, apply and disassembly recipe: **`docs/line-patch-map.md`**.

## Architecture

Two Gradle modules (`settings.gradle.kts`):

- **`patches/`**: Kotlin, written against the `app.morphe.patcher` API. Most work happens here.
- **`extensions/extension/`**: Java, compiled as an Android library to `extensions/extension.mpe`. Runtime logic injected into the target app.

### How a patch works

**fingerprint → locate method → inject smali → optionally delegate to extension code.**

1. **Fingerprint:** Describes a method in the target app by class, name, access flags, return type, parameters, and instruction `filters` (field access, strings, method calls, opcodes, literals). Matching is partial and obfuscation-tolerant. Anchor on **string literals** and non-obfuscated class names, because obfuscated names (`yi1.d`, method `b`) drift between versions. Declare fingerprints as named objects so failures name them in the stack trace.
2. **Patch:** `bytecodePatch { }` with `name`/`description`/`default`. `execute { }` resolves the fingerprint's `method` and edits it (`addInstructions(index, smali)`). Injected smali calls the extension via `invoke-static {}, Lapp/andrewliang/extension/…;->method()Z`.
3. **`extendWith("extensions/extension.mpe")`:** Bundles the compiled extension. Fixed-value overrides need no extension; use extension Java only for real logic.
4. **`compatibleWith(...)` / `dependsOn(...)`:** Target-app compatibility (`Constants.COMPATIBILITY_LINE`) and patch dependencies.

**Patch visibility:** A `bytecodePatch` with a `name` is user-facing (shown in Manager/CLI). A nameless one is an internal dependency pulled in through `dependsOn`.

**Compatibility** (`shared/Constants.kt`): Declares `packageName`, app name, `apkFileType`, icon color, and an `AppTarget` list. `version = null` means any/latest (with `isExperimental = true`). Always pin at least one tested version.

### Patcher API notes (hard-won)

- **Instruction indices:** Use `fingerprint.instructionMatches[i].index` (one match per filter, in program order) or `instructionMatchesOrNull`. `.method` and `.instructionMatches` are context-receiver accessors inside `execute { }`. (`indexOfFirstInstructionOrThrow` is ReVanced, not Morphe.)
- **Filter builders** (`app.morphe.patcher`): `fieldAccess`, `methodCall`, `string`, `literal`, `opcode`, `checkCast`, `instanceOf`, `newInstance`. Two identical filters match the first two occurrences in program order (`hidewallettab`). `fieldAccess` matches reads and writes, so pin constructor parameters when a field is also written in `<clinit>`.
- **Reading a match's operands:** Cast `instructionMatches[i].instruction` (`as TwoRegisterInstruction`) to read `.registerA` and write `const 0` into an `iget`'s own destination register (`hideevents`).
- **Register operand limits:** `invoke-*` (35c) and `iget`/`iput` (22c) take **4-bit operands (v0–v15 only)**. v16+ is dropped or mis-assembled, so the patch applies and does nothing. Use a low free register or a `/range` instruction.
- **Do not inject a backward-branching loop into an existing method.** It can corrupt branch offsets into a runtime `VerifyError` ("target dex pc … not at instruction start"). Put the loop in a new method (`mutableClassDefBy(desc).methods.add(MutableMethod(ImmutableMethod(...)))`) and inject a branchless `invoke-static` + `move-result` at the call site (`hidehomemodules`).
- **Never write a label inside injected smali. Use `addInstructionsWithLabels` + `ExternalLabel`.** An in-block label resolves against the block's own addresses and is not rebased, so at any non-zero index the branch points into an earlier instruction (`VerifyError: … target dex pc 0xN is not at instruction start`). Bind to a real instruction: `addInstructionsWithLabels(idx, smali, ExternalLabel("name", method.getInstruction(idx)))`.
- **Inject at a fall-through point, never before a pure branch target.** Inserting at index N leaves the label and every branch to it on the original instruction (now at N+len), so code inserted there never runs. Inject where the previous instruction falls through (for example right after `move-result`), and prove reachability in the disassembly.
- **`removeInstructions(index, count)` at several sites:** Remove the highest index first so earlier indices stay valid. `methodCall(name = "onCreate")` also matches `invoke-super`, which finds a super call to inject after (`index + 1`).
- **Targeting a method in an obfuscated class:** Fingerprint a sibling on a stable anchor (framework call or string literal), call `mutableClassDefBy(fp.method.definingClass)`, and select the target by `returnType` and `parameterTypes` (`keepunread` on `TalkServiceClient.c1`).
- **Resolving an obfuscated class from a framework-typed field read:** In a matched method, an `iget-object` whose `FieldReference.type` is a framework type (such as `Landroid/content/Context;`) gives the obfuscated owner's descriptor and field name. Use that descriptor to find enclosing lambda captures (`definingClass == method.definingClass`), and pass the stock captures rather than re-deriving them (`docs/line-patch-map.md`, "Outbound photo pipeline").
- **Neutralising a numeric gate:** For `const-wide/32 vN, <limit>` + `cmp-long` + `if-*`, replace the compared literal (`replaceInstruction(idx, "const-wide/32 v$reg, 0x7fffffff")`), not the branch. Control flow and registers stay unchanged. Two gates in one method use two identical `literal(...)` filters.
- **Hiding a UI list item or adding a Settings row:** Force the per-item availability predicate false rather than editing the shared list builder. For a Settings row, hook the list getter and copy a live row (`docs/line-patch-map.md`, "Chat '+' attach menu" and "Settings screen").
- **Forcing a config gate false:** Flip every server-side sibling flag that ships with it. When a patch makes a method return `null` where it never did, search its callers for missing null checks (`docs/line-premium-map.md`).
- **When you replace a method body, list every input the original accepted.** Its leniency is often implicit (for example `""` falling through a no-match scan). Guard null and empty, and branch into the original body with `ExternalLabel`.
- **The bundle supports two LINE versions, so each fingerprint must match both.** Look up resource ids by name at patch time: `literal({ resourceId("string", "...") })` from `shared/ResourceIds.kt` with `resourceIdsPatch` in `dependsOn`. From 26.15.1, R8 also renames the Kotlin stdlib (`kotlin.Unit`, `Lazy`, function types), so do not anchor on stdlib types. See the `app-bump` skill and `docs/line-patch-map.md` ("Two supported versions").
- **Manifest/resource edits & Root Mount:** Edit XML through `resourcePatch { … execute { document("AndroidManifest.xml").use { doc -> … } } }`. A Root Mount install mounts the patched `base.apk` over the Play Store install and keeps the original manifest, so any new Activity, service or provider fails with `ActivityNotFoundException`. Run extension UI inside LINE's own Activities (`Fragment` or `Dialog`), and restart with a new task (`docs/line-patch-map.md`, "Custom fonts").
- **Kotlin block comments nest:** A `/*` inside a `/** … */` KDoc (such as `line://home/*`) opens a nested comment and breaks the file. Use `//` or reword.
- **Verified bytecode does NOT mean the code runs — instrument the path before adding more sites.** When a patch applies cleanly and changes nothing on the phone, do not guess the next gate in the decompile. Use the `probe` skill to log unconditionally on entry at the candidate methods in LINE's own code, and let one run show what executes.
- **A clean `buildAndroid` plus a dump does NOT prove a branch is valid.** `BranchSweep` (in the `verify-patch` skill) checks every branch offset, try range and handler address against instruction starts across the whole dex.
- **Always verify by APPLYING, not just building.** Fingerprints resolve at apply time against the target APK. Run the `verify-patch` skill (`verify.sh`) after every edit, and wipe `patches/build/libs` before manual CLI runs so a stale `.mpp` is not applied.

### Metadata generation

`util/PatchListGenerator.kt` (`main()`, run by `generatePatchesList`) loads the built `.mpp` via `loadPatchesFromJar`, reads the bundle version from the JAR manifest, and writes `patches-list.json`. Third-party tools read that file. Do not edit it by hand.

## Target app integrity (LINE)

- The core messenger has no enforcing signature or integrity check. Root, debugger, emulator and signature checks in the general app are telemetry only (Crashlytics / Sentry), and no Play Integrity or SafetyNet is bundled.
- Enforcement is confined to LINE Pay through the native VKey V-OS / V-Guard engine (`libvosWrapperEx.so`; `VosWrapperBase.getAppSignerHash()`), which starts only on entering Pay flows. Defeating it is out of scope.
- **Never suggest a Root Mount install as a workaround for LINE Pay** (docs, issue replies, PRs). Nobody has tested payments on Root Mount, and it still runs modified code, so the account ban risk is unknown. For a merchant that refuses the standalone LINE Pay app, the only workaround is to pay another way.

### Re-signed builds — what breaks and why

A re-signed (Standard) APK breaks wherever LINE or Google checks the signing certificate against a server pin. Who reads the certificate decides whether a patch can fix it:

| Surface | Who reads the cert | Status |
|---|---|---|
| Closed-app push notifications (FCM `BAD CONFIG`) & Remote Config | LINE's own process (`X-Android-Cert` header in `ct/c`, `ConfigFetchHttpClient`, `remoteconfig.internal.d.i`) | **Fixed**: `fixpushnotifications` writes LINE's official SHA-1 |
| Location maps (8 layouts draw as empty grids) | Play Services process (`ApiTokenService`) | **Worked around**: `restorelocationmaps` redirects `fo/p.b` to MicroG-RE 7.0.0+ (real Play Services must stay installed) |
| Google account sign-in / link (Path 1: `CredentialManager`) | System `CredentialManager` → Play Services `GoogleIdService` | **Not patchable**: Root Mount only (also in `README.md` "Known limitation"; update both together) |
| Google Drive chat backup (Path 2: `GoogleAuthUtil`) | LINE's hardcoded `setPackage` / `ComponentName` binds | **Fixed**: `gmscoreauth` redirects 5 auth sites to MicroG-RE |
| Biometric FIDO2 link (issue #195) | LINE's WebAuthn `android:apk-key-hash-sha256` origin (`fido2.v.b`, `Fido2Util`) | **Fixed** (off by default): `fixbiometriclogin` writes LINE's official SHA-256 |

Read "App-signature reads & the re-signing family", "Google account access" and "Location maps" in `docs/line-patch-map.md` before touching any of these. Do not re-derive them.

## Feature limits — client-side vs server-enforced (LINE)

Before patching any LINE limit, find out **who decides**:

- **A limit that LINE computes or reports in its own process can be patched.** A decision made by a server or another process cannot, because removing the client check only moves the failure later.
- **Client-side configuration is not server enforcement.** A server-supplied value used locally (photo compression tier) can be patched. A server-made decision (`unsendMessage` → `MESSAGE_NOT_DESTRUCTIBLE`) cannot. A dedicated Thrift or HTTP error code for the condition proves server enforcement.
- **Check which process opens the resource.** Call ringtones can be patched because LINE's own `MediaPlayer` opens the `Uri` in-process. Google sign-in cannot, because Play Services checks the signature in another process.
- **"Patchable" is not "worth patching."** Lead every proposal with who is blocked today and what they get, then give the mechanism. If a design needs new UI or a new Activity in obfuscated code, say so first. Record "patchable but marginal" findings and the verdict table in `docs/line-patch-map.md` ("Feature limits — client-side vs server-enforced").

## Working principles

- **An absence is evidence only when the search covered the right place.**
  - Large apps route many surfaces through one shared implementation. Before you call a feature absent, find the generic path that the surface delegates to (the shared menu, controller or action enum) and search there. Enum constants and resource names are better evidence than guessed identifiers. If the user says a button exists, believe them and find it.
  - Peer patch repos (for example FroggoMorphePatches) use the same semantic-release layout, so their default branch is stale. List branches and tags and search `dev` or the newest pre-release tag, not only `HEAD`.
- **Verify an injected block whole.** Dump the full block and read every line in order. A grep for the lines you expect can never show a missing one. Look for a non-void `invoke-*` with no `move-result*` after it. BranchSweep checks branch offsets, not register types. Only the ART verifier on the device catches a type error.
- **Inventory before you widen a blocklist.** Before a change makes a filter patch (module types, tab constants, ad views) hide more, list every value the predicate can see as hidden, kept or left alone, with its evidence (device-confirmed or static only). Then propose how to package it. Check whether one value blocks two surfaces before you propose a split.
- **Keep a diff to its scope.** Mention an out-of-scope improvement in one line at most, and leave it out. If it is worth doing, it is a separate commit or PR.
- **Design review happens in prose.** The maintainer validates a design by asking pointed questions, and each one usually corrects the mechanism. Answer it directly with code anchors, say what it changes in the plan, and fold the correction in plainly. Ask for approval or a choice only after the objections stop.

## Development procedure

Every new patch or fix goes through these six steps, in order. Each step ends on its completion criterion.

1. **Investigate.** Locate the code that draws or decides the surface: first in the decompile, then by measurement on the device. Done when a logging build shows that the target method runs while the surface is on the screen (see "Verified bytecode does NOT mean the code runs"). Record the anchors and the dead ends in the `docs/*-map.md` of the app.
2. **Develop.** Work on a `feat/` or `fix/` branch off `dev`. Never commit onto `dev` itself. Stage only the files of the task, because the working tree often holds unrelated edits. Done when `buildAndroid` passes and the patch applies to the pinned APK.
3. **Device test.** Build the test APK through `../patched-apps`: stage the bundle in `temp/andrewliang25-rv/`, then `docker run --rm --platform linux/amd64 -v "$PWD":/repo -w /repo rvmm-build ./build.sh <test>.toml`. Install it with `../platform-tools/adb install -r`. Done when the disassembly shows the edit, `BranchSweep` is clean, and the device shows the change with no crash. For a rare or server-capped surface, force and observe in one build: a helper before the forced gate logs each case that the patch stops. Commit probe branches locally and never push them, because a pushed probe invites a PR for code that must not ship. Record the measured result in `docs/*-map.md` and in the PR body of the real patch.
4. **Clean up.** Remove probe code, dead code and backstory from comments. A comment keeps the rule and a one-clause reason. The story (issue numbers, how it was found) goes in the commit or PR. Done when the diff holds only the shipping change and its docs.
5. **Simple English.** Load the `simple-english` skill and run its self-check on the new comments, docs, patch descriptions, commit message and PR body. Keep the file's existing spelling (the Facebook AMOLED code uses "colour"). Say in the PR that steps 4 and 5 ran.
6. **Commit, push, PR.** Write one conventional commit, scoped to its app (see "Scope each app change" in the release pipeline), with no `Co-Authored-By` trailer, because semantic-release copies the message into the release notes. Push the branch and open a PR into `dev`. The PR body gives a summary, how the patch works, and a testing checklist. An item that was not observed on the device stays unchecked, with the reason. Squash or reword the branch's commits before merge: only `feat`/`fix`/`perf`/`bump` subjects reach the release notes, and `dev` cannot be rewritten after a release.

## Release pipeline — do not fight it

Automated by **semantic-release** (`.releaserc`, `.github/workflows/release.yml`):

- **All development happens on `dev`.** `dev` produces pre-releases. Merging `dev → main` (plain merge, **not squash**) produces a stable release. A push to `dev` opens the `dev → main` PR (`open_pull_request.yml`).
- **Use conventional commits.** `fix:` → patch bump, `feat:` → minor bump, `bump:`/`perf:` also release; `chore:`/`build:`/`docs:` do not. Use `docs:` for a change that is only docs or wording, so that users get no update for it. The content ships with the next real release, whose `prepareCmd` regenerates the README table and `patches-list.json`. The type sets the version and the changelog section.
- **Scope each app change to its app.** Use the app `name` from `Constants.kt` as the scope: `feat(LINE): …`, `fix(Facebook): …`. For one patch, write `App - Patch name`, for example `fix(LINE - Hide ads): …`. semantic-release writes the scope as `* **LINE:** …` in `CHANGELOG.md`. Morphe Manager reads that file on `main` and shows the update badge for a patched app only when a newer bullet has that app's scope. The Manager ignores bullets with no scope, so users of the app get no badge for an unscoped change (issue #172). If a change touches two apps, write one commit for each app. Leave out the scope only when the change touches no app.
- **Never hand-edit generated files:** `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, `gradle.properties` (`version`), and README's `<!-- PATCHES_START -->`…`<!-- PATCHES_END -->` block (generated by `.github/scripts/generate_patches_readme.py`).
- **Never manually create or upload GitHub releases**, and never force-push a semantic-release commit. Either action breaks the release state.
- **`release.yml`'s draft→upload→publish flow is deliberate** (semantic-release drafts the release, a step uploads `patches-*.mpp`, then it publishes) plus a build-provenance attestation. This flow is what makes the pipeline compatible with GitHub immutable releases, which reject uploads to a published release. Do not collapse it. (`open_pull_request.yml` tolerates a compare 404 so it never fails.)

## Skills

Procedures live in `.agents/skills/<name>/SKILL.md` ([Agent Skills](https://agentskills.io) format). If your tool does not load skills, read the file when its case applies:

- `verify-patch`: build, apply to every pinned version, diff the changed instructions, run BranchSweep. Run it after every patch edit.
- `probe`: build a logging build for step 1 (investigate), or when a patch applies but changes nothing.
- `device-test`: drive the phone over adb for step 3 (install, open screens, screenshots, logcat).
- `app-bump`: support a new version of a patched app.
- `simple-english`: write and check comments, docs, patch descriptions, commit messages and PR bodies in plain English (step 5).

## Decompiled reference

Fingerprint authoring relies on inspecting the target app's bytecode:

- **`docs/line-patch-map.md`** (tracked): the offline build/disassembly recipe, per-surface class and anchor maps for the shipped patches, investigated dead ends, and areas not shipped. **`docs/line-premium-map.md`** covers LYP premium gating. Update both when bumping the pinned LINE version, because the obfuscated descriptors drift.
- **`docs/facebook-ads-map.md`** (tracked) covers the Facebook ad surfaces, and **`docs/facebook-theme-map.md`** covers Facebook's two colour trees and the AMOLED patch. Facebook strips resource names and renames every class and method about every two weeks, so re-confirm the descriptors on each bump.
- **`work/decompiled-line-<version>/`** (gitignored): `apktool/` has the smali that fingerprints match against plus resources, and `jadx/` has readable Java. Regenerate with `apktool d` / `jadx` from `work/apkm-extract/base.apk`, and search `apktool/smali*` for anchors.
