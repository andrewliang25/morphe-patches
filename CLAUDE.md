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

### Metadata generation

`util/PatchListGenerator.kt` (`main()`, run by the `generatePatchesList` task) loads the built `.mpp` via `loadPatchesFromJar`, reads the bundle version from the JAR manifest, and serializes every patch's metadata (name, description, deps, compatibility, options) to `patches-list.json`. Third-party tools consume this file — do not hand-edit it.

## Release pipeline — do not fight it

Releases are fully automated by **semantic-release** (`.releaserc`, `.github/workflows/release.yml`). This drives several rules:

- **All development happens on `dev`.** `dev` produces pre-releases; merging `dev → main` (plain merge, **not squash**) produces a stable release. A push to `dev` auto-opens the `dev → main` PR (`open_pull_request.yml`).
- **Use conventional commits.** `fix:` → patch bump, `feat:` → minor bump (both create releases and appear in the changelog); `bump:`/`perf:` also release; `chore:`/`build:` do **not** create a release. The commit type determines the version and the user-facing changelog section.
- **Never hand-edit generated files:** `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, `gradle.properties` (`version`), and the `<!-- PATCHES_START -->`…`<!-- PATCHES_END -->` block in `README.md` are all rewritten during release. The README patches section is generated by `.github/scripts/generate_patches_readme.py`.
- **Never manually create/upload GitHub releases**, and never force-push a semantic-release commit — either breaks the release state.

## Decompiled reference & prior art

Fingerprint authoring relies on inspecting LINE's bytecode. These live outside the repo / are gitignored:

- **`work/decompiled-line-<version>/`** (gitignored) — decompiled LINE. `apktool/` has smali (what fingerprints match against) + resources; `jadx/` has readable Java for understanding logic. Regenerate with `apktool d` / `jadx` from `work/apkm-extract/base.apk`. Anchor grep across `apktool/smali*` for strings/classes.
- **LIME-Reborn** (`../LIME-Reborn`) — a runtime **Xposed/LSPatch** module that hooks the same LINE app. Great prior art for *what* to patch: `app/src/.../hooks/*.java` (RemoveAds, PreventMarkAsRead, KeepUnread, …) and `hooks/Constants.java` (obfuscated target class/method names). Caveat: it hooks at runtime, so hooks that touch framework classes (e.g. `Notification.Builder`) or build runtime UI do **not** port to Morphe's ahead-of-time bytecode approach.
