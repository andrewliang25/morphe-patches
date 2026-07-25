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

## Release pipeline — do not fight it

Releases are fully automated by **semantic-release** (`.releaserc`, `.github/workflows/release.yml`). This drives several rules:

- **All development happens on `dev`.** `dev` produces pre-releases; merging `dev → main` (plain merge, **not squash**) produces a stable release. A push to `dev` auto-opens the `dev → main` PR (`open_pull_request.yml`).
- **Use conventional commits.** `fix:` → patch bump, `feat:` → minor bump (both create releases and appear in the changelog); `bump:`/`perf:` also release; `chore:`/`build:` do **not** create a release. The commit type determines the version and the user-facing changelog section.
- **Never hand-edit generated files:** `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, `gradle.properties` (`version`), and the `<!-- PATCHES_START -->`…`<!-- PATCHES_END -->` block in `README.md` are all rewritten during release. The README patches section is generated by `.github/scripts/generate_patches_readme.py`.
- **Never manually create/upload GitHub releases**, and never force-push a semantic-release commit — either breaks the release state.
- **`release.yml` uses a draft→upload→publish flow** (semantic-release creates the GitHub release as a *draft* with no assets, a workflow step uploads the `patches-*.mpp` to it, then publishes) plus a build-provenance **attestation**. This is deliberate — it's compatible with GitHub immutable releases, which reject asset uploads to an already-published release. Don't collapse it back to a direct publish. (`open_pull_request.yml` also tolerates a compare 404 so it never hard-fails.)

## Decompiled reference & prior art

Fingerprint authoring relies on inspecting LINE's bytecode. These live outside the repo / are gitignored:

- **`work/decompiled-line-<version>/`** (gitignored) — decompiled LINE. `apktool/` has smali (what fingerprints match against) + resources; `jadx/` has readable Java for understanding logic. Regenerate with `apktool d` / `jadx` from `work/apkm-extract/base.apk`. Anchor grep across `apktool/smali*` for strings/classes.
- **LIME-Reborn** (`../LIME-Reborn`) — a runtime **Xposed/LSPatch** module that hooks the same LINE app. Great prior art for *what* to patch: `app/src/.../hooks/*.java` (RemoveAds, PreventMarkAsRead, KeepUnread, …) and `hooks/Constants.java` (obfuscated target class/method names). Caveat: it hooks at runtime, so hooks that touch framework classes (e.g. `Notification.Builder`) or build runtime UI do **not** port to Morphe's ahead-of-time bytecode approach.
