# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Andrew's Patches** — a **Morphe Patches** bundle (Morphe is a fork of the ReVanced patching ecosystem). It produces an `.mpp` patch bundle that the Morphe CLI / Manager applies to third-party Android APKs to rewrite their bytecode. The current focus is **LINE** (`jp.naver.line.android`). Base package/group is `app.andrewliang` (app-agnostic); per-app patches nest under `app.andrewliang.patches.<app>` (e.g. `app.andrewliang.patches.line`), and target-app compatibility lives in `app.andrewliang.patches.shared.Constants`. The developed-against version is pinned there.

## Commands

```bash
# Build the patch bundle -> patches/build/libs/patches-*.mpp
./gradlew buildAndroid

# Build, then regenerate patches-list.json from the compiled bundle
./gradlew generatePatchesList

# Compile-check without producing a release (what CI runs on non-release commits)
./gradlew :patches:buildAndroid clean --no-daemon
```

There is no test suite. Correctness is validated by applying the built `.mpp` with the Morphe CLI against a real target APK. `generatePatchesList` reflectively loads the built `.mpp` and re-emits `patches-list.json`, so it depends on `build` having run.

`settings.gradle.kts` pulls the `app.morphe.patches` Gradle plugin and patcher libraries from GitHub Packages (`maven.pkg.github.com/MorpheApp/registry`). Building requires `gpr.user`/`gpr.key` Gradle properties **or** `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars with a PAT that can read those packages.

**Local build/verify without a PAT:** if the patcher/plugin artifacts are already in the Gradle cache, build fully offline with *dummy* credential values — `./gradlew :patches:buildAndroid --offline --no-daemon -Pgpr.user=dummy -Pgpr.key=dummy` (the settings plugin only needs the credentials to be non-null when nothing is fetched). Then apply with the bundled Morphe CLI (`java -jar work/morphe-desktop-*.jar patch --exclusive -e "<name>" -o work/out.apk … work/apkm-extract/base.apk`) and inspect the patched dex with **dexlib2** (no `baksmali` CLI ships — STRIP_FAST writes modified classes into a small fresh `classes.dex`). Full recipe + the LINE class/anchor map live in **`docs/line-patch-map.md`**.

## Architecture

Two Gradle modules (`settings.gradle.kts`):

- **`patches/`** — Kotlin. The patches themselves, written against the `app.morphe.patcher` API. This is where nearly all work happens.
- **`extensions/extension/`** — Java, compiled as an Android library to `extensions/extension.mpe`. Holds complex runtime logic that is injected *into* the target app.

### How a patch works

The patching model is: **fingerprint → locate method → inject smali → optionally delegate to extension code**.

1. **Fingerprint** — declaratively describes a method in the *target app* by defining class, name, access flags, return type, parameters, and a list of instruction `filters` (field access, string references, method calls, opcodes, literals). Partial/obfuscation-tolerant matching applies. Prefer anchoring on **string literals** and non-obfuscated class names, since obfuscated names (`Sg1.c`, method `b`, …) change between LINE versions. Declaring fingerprints as named objects/classes means failures name the fingerprint in the stack trace.
2. **Patch** — `bytecodePatch { ... }` with `name`/`description`/`default`. In `execute { }` it resolves the fingerprint's `method` and mutates it via extensions like `addInstructions(index, smali)`. Injected smali calls the extension with `invoke-static {}, Lapp/andrewliang/extension/...;->method()Z`.
3. **`extendWith("extensions/extension.mpe")`** — bundles the compiled extension so injected smali can call it. Simple fixed-value overrides need no extension; use extension Java only for real logic.
4. **`compatibleWith(...)`** / `dependsOn(...)` — declare target-app compatibility (`Constants.COMPATIBILITY_LINE`) and patch dependencies.

**Patch visibility:** a `bytecodePatch` with a `name` is user-facing (shown in Manager/CLI); a nameless one is an internal dependency, hidden from users but pulled in via `dependsOn`.

**Compatibility** (`app/andrewliang/patches/shared/Constants.kt`) — `Compatibility` objects declare target `packageName`, app name, `apkFileType`, icon color, and `AppTarget` version list. `version = null` means "any/latest" (often `isExperimental = true`); always pin at least one confirmed-working version.

### Patcher API notes (hard-won)

- **Finding instruction indices:** there is no `indexOfFirstInstructionOrThrow` (that's ReVanced). Use `fingerprint.instructionMatches[i].index` — one match per instruction `filter`, in program order — or `instructionMatchesOrNull` for best-effort. `.method` and `.instructionMatches` are context-receiver accessors usable inside `execute { }`.
- **Instruction filter builders** (imported from `app.morphe.patcher`): `fieldAccess`, `methodCall`, `string`, `literal`, `opcode`, `checkCast`, `instanceOf`, `newInstance`. Two *identical* filters match the first two occurrences in program order — use this to grab both sites of a repeated `sget`+`add` pair in one method (see `hidewallettab`). `fieldAccess` matches both reads and writes, so pin the ctor (parameters) when a field is also `sput` in an enum `<clinit>`.
- **Reading a matched instruction's operands:** `fingerprint.instructionMatches[i].instruction` returns the dexlib2 `Instruction`; cast it (e.g. `as TwoRegisterInstruction`) to read `.registerA`. Lets you "replace this `iget` with a `const 0` into its *own* destination register" without hardcoding the register (see `hideevents`: `removeInstruction(idx)` + `addInstructions(idx, "const/16 v$reg, 0x0")`).
- **Hiding a UI list item:** LINE lists (attach-menu tiles, chat-menu rows, context-menu actions) render each entry through a per-item availability predicate; force *that* predicate false rather than editing the (often shared or looping) list builder. To hide a **whole server-driven category**, neuter the shared renderer's gate — stable, no ids (see `hideattachmenutools` → `hg1.d.f()`); to hide **one** server item you must match an id/type that can drift server-side — fragile, avoid. Details/anchors in `docs/line-patch-map.md`.
- **Register operand limits:** `invoke-*` (format 35c) and `iget/iput` (22c) take **4-bit register operands — only v0–v15**. Referencing v16+ there is silently dropped/mis-assembled (the filter/injection appears to apply but does nothing). Use a low free register, or the `/range` instruction variants.
- **Don't inject a backward-branching loop into an existing method** — it can corrupt that method's branch layout and throw a runtime `VerifyError` ("target dex pc … not at instruction start"). Instead extract the loop into a **new** method (`mutableClassDefBy(desc).methods.add(MutableMethod(ImmutableMethod(...)))`, then `addInstructions`) and inject only a branchless `invoke-static` + `move-result` at the call site (see `hidehomemodules`).
- **Targeting a method in an obfuscated class:** fingerprint a *sibling* on a stable anchor (a non-obfuscated framework/API call or string literal), then `mutableClassDefBy(fp.method.definingClass)` and select the target method by descriptor (`returnType` + `parameterTypes`) — see `keepunread` anchoring on `TalkServiceClient.j1`.
- **Manifest/resource edits:** `resourcePatch { … execute { document("AndroidManifest.xml").use { doc -> … } } }` — the `Document` is a standard W3C DOM.
- **Kotlin block comments NEST:** a `/*` inside a `/** … */` KDoc (e.g. writing a `line://home/*` scheme) opens a nested comment and eats the file — use `//` or reword.
- **Always verify by APPLYING**, not just building: fingerprints resolve at *apply* time (against the target APK), not build time, so a clean `buildAndroid` does **not** prove a fingerprint matches. Apply the `.mpp` with the Morphe CLI (`patch --exclusive -e "<name>"`) and disassemble the output to confirm the injected bytecode. Note the built `.mpp` filename carries the semantic-release version (e.g. `patches-1.0.0-dev.9.mpp`); wipe `patches/build/libs` before a verify run so a stale artifact isn't applied by mistake.

### Metadata generation

`util/PatchListGenerator.kt` (`main()`, run by the `generatePatchesList` task) loads the built `.mpp` via `loadPatchesFromJar`, reads the bundle version from the JAR manifest, and serializes every patch's metadata (name, description, deps, compatibility, options) to `patches-list.json`. Third-party tools consume this file — do not hand-edit it.

## Target app integrity (LINE) — what patching must respect

From decompiling LINE 26.11.0 (full detail in `work/decompiled-line-<ver>/NOTES-integrity-checks.md`, gitignored):

- The **core messenger has no enforcing** signature/integrity check — a re-signed patched APK runs fine for login/chat/general features.
- Every root/debugger/emulator/signature check in the general app is **telemetry only** (Firebase Crashlytics, obfuscated `es` package; Sentry `io.sentry.android.core`) — no `exit`/`finish`/`throw`.
- **No Play Integrity / SafetyNet** is bundled. The `attest` code is LINE's own *server-side* WebAuthn/FIDO2 and a fire-and-forget "DeviceAttestation" WorkManager job (always returns success).
- **Enforcement is confined to LINE Pay**, via the bundled native **VKey V-OS / V-Guard** engine (`libvosWrapperEx.so`; `VosWrapperBase.getAppSignerHash()` → native SHA-256 signer compare). It initializes only when entering Pay flows. The Block/Warn/Bypass decision per threat is **server-driven** (`TamperSettingsGetResDto`); on block, `VGuardDetectionActivity` ends the Pay flow — it does not kill the whole app.

**Implication for patches:** messaging patches are safe on a re-signed build. Defeating LINE Pay's protection would require neutralizing the VKey native library (out of scope). Prefer fingerprints anchored on **string literals / non-obfuscated class names** — LINE obfuscates class and method names (even `org.apache.thrift`'s), and they drift between versions.

### Re-signed builds & closed-app push notifications (FIXED in-APK by the "Fix push notifications" patch)

Re-signing (Standard install) breaks LINE's push while the app is *fully closed*, but — unlike the Google sign-in limitation below — this **is** fixable from the patch side. Patch: `patches/line/fixpushnotifications/`. Device-confirmed on a re-signed build (LINE 26.11.0).

- **Symptom (unpatched re-sign):** notifies while open/backgrounded (LINE's persistent **LEGY** socket) but not while fully closed (swiped from Recents); reopening flushes the queue. Closed, LINE's only wakeup path is **FCM**.
- **Root cause:** LINE's bundled Firebase Installations (FIS) SDK self-reports the app's signing-cert SHA-1 in the `X-Android-Cert` HTTP header (`ct.c.c(URL,String)` → `rl.a.a` = `getPackageInfo(pkg, GET_SIGNATURES)` → SHA-1 of `signatures[0]` → uppercase hex in `rl.h.b`). LINE's embedded Firebase API key is **restricted to LINE's official cert SHA-1**, so a re-signed APK's SHA-1 is rejected. Evidence: `/data/data/jp.naver.line.android/no_backup/PersistedInstallation.*.json` = `"Status":4` (REGISTER_ERROR) + `"FisError":"BAD CONFIG"` + `"TokenCreationEpochInSecs":0` → no FCM token minted, so LINE's servers have nothing to push to. Battery/App-Standby ruled out (bucket `5` EXEMPTED + deviceidle-whitelisted, still fails).
- **Why it's patchable (contrast with Google sign-in):** the cert is a **self-reported HTTP header built inside LINE's own process**, so a patch can overwrite it. (Google sign-in's cert check happens in GmsCore reading the real signature — not patchable; see below.) The patch anchors the FIS request builder on the `X-Android-Cert` string + its `addRequestProperty` call and rewrites the value register with LINE's **original** signing-cert SHA-1. Only the FIS call site is touched; the shared `rl.a`/`rl.h` helpers (also used by Remote Config) are left intact.
- **Which cert:** `base.apk` uses APK Signature Scheme **v3.1 key rotation** (two certs). `GET_SIGNATURES` returns the lineage-**root/original** cert, so the patch injects the SDK 24–32 signer `89396DC419292473972813922867E6973D6F5C50`. Fallback if `BAD CONFIG` persists on-device: the rotated SDK 33+ signer `6A2927D945AEA6571E1DA5566802F25045D367BD`. Re-derive both from the pinned APK with `apksigner verify --print-certs base.apk` when bumping the LINE version.
- **Verify:** apply the `.mpp` and disassemble `ct/c` — `const-string v$reg, "<sha1>"` must land in the `addRequestProperty` value register (`registerE`) immediately before the `X-Android-Cert` send. On-device, `PersistedInstallation*.json` `Status` should flip `4 → 3` (REGISTERED). **The on-device flip is the real proof** — a clean build/disassembly only proves the header is rewritten, not that GmsCore's own registration (which reads the real signature) also accepts it.

### Known limitation: Google account sign-in fails for chat-history backup/restore (re-signed builds)

Documented as a "Known limitation" in `README.md`. Same *class* of root cause as the FCM signature break (Google-side config pinned to LINE's official cert SHA-1), but a distinct mechanism — and, **unlike FCM, not fixable in-APK**. Decompile-confirmed (LINE 26.11.0), NOT yet device-confirmed; NOT a patch bug — no patch touches the backup/sign-in code:

- **Symptom:** on a re-signed build, picking a Google account to back up/restore chat history fails at the account-selection step, so Drive-based chat history backup/restore is unavailable.
- **Cause:** LINE stores chat history in Google **Drive** and gates it behind **Google Sign-In** (`com.google.android.gms.auth.api.signin.GoogleSignInOptions`) requesting the `https://www.googleapis.com/auth/drive.appdata` scope. Android OAuth clients are keyed by **package name + signing-cert SHA-1**; re-signing changes the SHA-1 so no OAuth client matches, and Play Services rejects the token grant (`ApiException` status code **10, `DEVELOPER_ERROR`**). Decompile anchors: `com/linecorp/line/settings/backuprestore/initialbackup/GoogleAccountSelectionFragment.java` (account picker); the `drive.appdata` scope + `GoogleAccountCredential` (`aq.a`) in `l18/d.java`, `hx4/i.java`, `ix4/a0.java`.
- **Distinct from FCM:** FCM fails on a Firebase **API key** restriction → `REGISTER_ERROR`/"BAD CONFIG"; this fails on an **OAuth client ID** match → `DEVELOPER_ERROR` (code 10). Both are Google Cloud config keyed to LINE's official cert.
- **Why it can't be patched like FCM:** the FCM fix works because LINE's own bundled SDK self-reports the cert SHA-1 in an HTTP header (`ct.c.c`'s `X-Android-Cert`), which a patch can overwrite. Here the Drive OAuth token is fetched via `GoogleAuthUtil` / `GoogleSignInClient` — binder IPC into Google Play Services (GmsCore), a *separate process* that reads the real installed signature itself. There is no cert value in LINE's APK to rewrite.
- **No patch-side fix:** can't register the re-sign's SHA-1 in LINE's Google Cloud project, and a substitute OAuth client wouldn't reach LINE's Drive appdata backup anyway. Workaround is install-side: **Root Mount** install (keeps original signature → sign-in works) instead of **Standard** (re-signs). To device-confirm: attempt the restore and watch `adb logcat | grep -iE "GoogleSignIn|ApiException|DEVELOPER_ERROR|10:"` at the account-selection tap. Re-check the anchors when bumping the pinned LINE version (obfuscated helper class names like `l18/d`, `hx4/i` drift).

## Release pipeline — do not fight it

Releases are fully automated by **semantic-release** (`.releaserc`, `.github/workflows/release.yml`). This drives several rules:

- **All development happens on `dev`.** `dev` produces pre-releases; merging `dev → main` (plain merge, **not squash**) produces a stable release. A push to `dev` auto-opens the `dev → main` PR (`open_pull_request.yml`).
- **Use conventional commits.** `fix:` → patch bump, `feat:` → minor bump (both create releases and appear in the changelog); `bump:`/`perf:` also release; `chore:`/`build:` do **not** create a release. The commit type determines the version and the user-facing changelog section.
- **Never hand-edit generated files:** `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, `gradle.properties` (`version`), and the `<!-- PATCHES_START -->`…`<!-- PATCHES_END -->` block in `README.md` are all rewritten during release. The README patches section is generated by `.github/scripts/generate_patches_readme.py`.
- **Never manually create/upload GitHub releases**, and never force-push a semantic-release commit — either breaks the release state.
- **`release.yml` uses a draft→upload→publish flow** (semantic-release creates the GitHub release as a *draft* with no assets, a workflow step uploads the `patches-*.mpp` to it, then publishes) plus a build-provenance **attestation**. This is deliberate — it's compatible with GitHub immutable releases, which reject asset uploads to an already-published release. Don't collapse it back to a direct publish. (`open_pull_request.yml` also tolerates a compare 404 so it never hard-fails.)

## Decompiled reference & prior art

Fingerprint authoring relies on inspecting LINE's bytecode.

- **`docs/line-patch-map.md`** (tracked) — the LINE `+` attach menu architecture (static `hg1.r` tiles vs server-driven `hg1.d` services), the Calendar vs Events vs Message-scheduler feature map, per-surface class/anchor references for the shipped patches, and the offline build + dexlib2 disassembly recipe. Update it when bumping the pinned LINE version (the obfuscated descriptors drift).

These live outside the repo / are gitignored:

- **`work/decompiled-line-<version>/`** (gitignored) — decompiled LINE. `apktool/` has smali (what fingerprints match against) + resources; `jadx/` has readable Java for understanding logic. Regenerate with `apktool d` / `jadx` from `work/apkm-extract/base.apk`. Anchor grep across `apktool/smali*` for strings/classes.
