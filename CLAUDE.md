# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

**Andrew's Patches** — a **Morphe Patches** bundle (Morphe is a fork of the ReVanced patching ecosystem). It builds an `.mpp` bundle that the Morphe CLI / Manager applies to third-party Android APKs to rewrite their bytecode. Current focus: **LINE** (`jp.naver.line.android`).

Base group `app.andrewliang` is app-agnostic; per-app patches nest under `app.andrewliang.patches.<app>` (e.g. `…patches.line`). Target-app compatibility and the developed-against version live in `app.andrewliang.patches.shared.Constants`.

## Commands

```bash
# Build the patch bundle -> patches/build/libs/patches-*.mpp
./gradlew buildAndroid

# Build, then regenerate patches-list.json from the compiled bundle
./gradlew generatePatchesList

# Compile-check without producing a release (what CI runs on non-release commits)
./gradlew :patches:buildAndroid clean --no-daemon
```

There is no test suite — correctness is validated by applying the built `.mpp` against a real target APK. `generatePatchesList` reflectively loads the built `.mpp`, so it requires a prior build.

`settings.gradle.kts` pulls the `app.morphe.patches` plugin and patcher libraries from GitHub Packages (`maven.pkg.github.com/MorpheApp/registry`), which needs `gpr.user`/`gpr.key` Gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars with a PAT that can read them.

**Without a PAT:** if the artifacts are already in the Gradle cache, build offline with *dummy* credentials — `./gradlew :patches:buildAndroid --offline --no-daemon -Pgpr.user=dummy -Pgpr.key=dummy` (the settings plugin only needs them non-null when nothing is fetched). Full offline build + apply + dexlib2 disassembly recipe: **`docs/line-patch-map.md`**.

## Architecture

Two Gradle modules (`settings.gradle.kts`):

- **`patches/`** — Kotlin, written against the `app.morphe.patcher` API. Nearly all work happens here.
- **`extensions/extension/`** — Java, compiled as an Android library to `extensions/extension.mpe`. Runtime logic injected *into* the target app.

### How a patch works

**fingerprint → locate method → inject smali → optionally delegate to extension code.**

1. **Fingerprint** — describes a method in the *target app* by class, name, access flags, return type, parameters, and instruction `filters` (field access, strings, method calls, opcodes, literals). Matching is partial and obfuscation-tolerant. Anchor on **string literals** and non-obfuscated class names; obfuscated names (`yi1.d`, method `b`) drift between LINE versions. Declare fingerprints as named objects so failures name them in the stack trace.
2. **Patch** — `bytecodePatch { }` with `name`/`description`/`default`. `execute { }` resolves the fingerprint's `method` and mutates it (`addInstructions(index, smali)`). Injected smali reaches the extension via `invoke-static {}, Lapp/andrewliang/extension/…;->method()Z`.
3. **`extendWith("extensions/extension.mpe")`** — bundles the compiled extension. Fixed-value overrides need no extension; use extension Java only for real logic.
4. **`compatibleWith(...)`** / **`dependsOn(...)`** — target-app compatibility (`Constants.COMPATIBILITY_LINE`) and patch dependencies.

**Patch visibility:** a `bytecodePatch` with a `name` is user-facing (shown in Manager/CLI); a nameless one is an internal dependency, pulled in via `dependsOn`.

**Compatibility** (`shared/Constants.kt`) — declares `packageName`, app name, `apkFileType`, icon color, and an `AppTarget` list. `version = null` means any/latest (usually with `isExperimental = true`); always pin at least one confirmed version.

### Patcher API notes (hard-won)

- **Instruction indices:** there is no `indexOfFirstInstructionOrThrow` (that's ReVanced). Use `fingerprint.instructionMatches[i].index` — one match per filter, in program order — or `instructionMatchesOrNull`. `.method` and `.instructionMatches` are context-receiver accessors usable inside `execute { }`.
- **Filter builders** (from `app.morphe.patcher`): `fieldAccess`, `methodCall`, `string`, `literal`, `opcode`, `checkCast`, `instanceOf`, `newInstance`. Two *identical* filters match the first two occurrences in program order — how `hidewallettab` grabs both `sget`+`add` pairs in one method. `fieldAccess` matches reads *and* writes, so pin the ctor's parameters when a field is also `sput` in an enum `<clinit>`.
- **Reading a match's operands:** `instructionMatches[i].instruction` is the dexlib2 `Instruction`; cast it (`as TwoRegisterInstruction`) to read `.registerA`. This is how you replace an `iget` with a `const 0` into its *own* destination register without hardcoding it (`hideevents`).
- **Hiding a UI list item:** LINE lists (attach-menu tiles, chat-menu rows, context-menu actions) render each entry through a per-item availability predicate — force *that* false rather than editing the often-shared list builder. For a whole server-driven category, neuter the shared renderer's gate (`hideattachmenutools` → `yi1.d.f()`); hiding **one** server item requires an id that drifts server-side, so avoid it.
- **Register operand limits:** `invoke-*` (35c) and `iget`/`iput` (22c) take **4-bit operands — v0–v15 only**. v16+ there is silently dropped or mis-assembled, so the injection appears to apply and does nothing. Use a low free register or the `/range` variants.
- **Don't inject a backward-branching loop into an existing method** — it can corrupt the branch layout into a runtime `VerifyError` ("target dex pc … not at instruction start"). Extract the loop into a **new** method (`mutableClassDefBy(desc).methods.add(MutableMethod(ImmutableMethod(...)))` then `addInstructions`) and inject only a branchless `invoke-static` + `move-result` at the call site (`hidehomemodules`).
- **Targeting a method in an obfuscated class:** fingerprint a *sibling* on a stable anchor (framework call or string literal), then `mutableClassDefBy(fp.method.definingClass)` and select the target by descriptor (`returnType` + `parameterTypes`) — see `keepunread` anchoring on `TalkServiceClient.c1`.
- **Neutralising a numeric gate:** for `const-wide/32 vN, <limit>` + `cmp-long` + `if-*`, rewrite the **compared literal** (`replaceInstruction(idx, "const-wide/32 v$reg, 0x7fffffff")`), not the branch — control flow stays byte-identical and register allocation is untouched. Read the destination register off the match (`as OneRegisterInstruction`). Two gates in one method = two identical `literal(...)` filters.
- **Resolving an obfuscated class from a framework-typed field read:** within a matched method, the only `iget-object` whose `FieldReference.type` is a **framework** type (e.g. `Landroid/content/Context;`) yields both the obfuscated owner's descriptor and the field name for free; feed that descriptor back in to find the enclosing lambda's synthetic captures (`definingClass == method.definingClass`). Worked example in `docs/line-patch-map.md` (outbound photo pipeline: `u13.c1`, its `Context`, and `u13.y0`'s `Uri`/rotation captures). **Pass the captures the stock code reads rather than re-deriving their values** — that extension took `y0`'s rotation `Integer` instead of reading EXIF, because the sibling encoder writing the standard variant uses the same value and the two outputs must agree.
- **Forcing a config gate false only emulates "feature off" if you flip every flag that ships with it — and auditing a gate's *enum* branches is not auditing its *nullable* returns.** `hidepremium` forced the LYP market gate `a83.a.d()` false but left the premium chat-backup flag `nj4.d.m()` (a separate server-pushed config) true — a pairing LINE never ships. The premium-backup settings row stayed visible while its badge provider began returning `null`, and the one view holder without a null guard (`r55/c3.java`, was `ux4/d3.java:66`) called `getDrawable(0)` → `Resources$NotFoundException: Resource ID #0x0`, killing **Settings ▸ Chats**. So: flip a gate's server-side siblings in lockstep, and when a patch makes a method return `null` where it never did, grep its consumers for the ones that don't null-check. Detail in `docs/line-premium-map.md`.
- **Never write a label inside injected smali — use `addInstructionsWithLabels` + `ExternalLabel`.** An in-block label resolves against the *block's own* addresses and is **not** rebased, so at any non-zero injection index the branch points into the middle of an earlier instruction and ART rejects the whole class: `VerifyError: … target dex pc 0xN is not at instruction start` (device-confirmed: an `if-eqz` at 0x11 targeting 0xf, its block-relative address). Bind to a real instruction instead: `addInstructionsWithLabels(idx, smali, ExternalLabel("name", method.getInstruction(idx)))`. `hideadviews` survives an in-block label only because it injects at index 0, where the two address spaces coincide; `hidehomemodules` because it builds a whole method body. Neither is a pattern to copy.
- **Verified bytecode does NOT mean the code runs — instrument the path before adding more sites.** One investigation burned four device rounds on edits that disassembled perfectly and never executed: LINE duplicates the same `>= 20 MB / >= 100 MP` decision across *five* places on two independent send paths, and the flow under test touched one of them. When a patch applies cleanly and changes nothing on device, don't hunt the next gate in the decompile — inject `Log` calls at the decision points (LINE's own methods, not just your extension) and let one run say what executes. A probe that logs *nothing* is as informative as one that logs a value, so log unconditionally on entry: a hook whose every branch falls through is indistinguishable from a hook that never ran.
- **A clean `buildAndroid` plus a correct-looking instruction dump does NOT prove a branch is valid.** Order, registers and try-block alignment can all be right while the branch *offset* is wrong. Decode it: walk the method summing `getCodeUnits()`, then check `offset + OffsetInstruction.getCodeOffset()` is a key in the offset→index map — for every `OffsetInstruction`, plus each try range start/end and handler address. A whole-dex sweep costs seconds and covers every patch at once.
- **Redirecting a bundled SDK by rewriting strings takes several passes, and each site hides the next.** Actions, target packages, account types and explicit **`ComponentName` binds** are independent mechanisms — an explicit bind ignores every action redirect, so a flow can still reach the original service with all its actions rewritten. Budget one device round per site: the runtime error only names the next unfixed site once the previous stops failing (`gmscoreauth` needed five string sites plus an injection, found over five rounds). Watch namespace vs applicationId too: a library can ship `applicationId app.revanced.android.gms` while keeping `namespace com.google.android.gms`, so `<service android:name=".auth.GetToken">` is the class `com.google.android.gms.auth.GetToken` *inside* the app.revanced package — rewrite only the package half, or the bind silently targets a nonexistent class (generic app error, nothing in logcat, because no service starts).
- **When you replace a method's body, enumerate every input the original tolerated** — its leniency is often implicit rather than a guard. `aq.a.b(String)` accepted `""` only because its `getAccountsByType` scan matched nothing, so a replacement building `new Account(name, type)` crashes on the empty string LINE passes when a screen opens with nothing selected. Guard null *and* empty, and branch into the original body via `ExternalLabel` rather than reimplementing the fallback.
- **When obfuscation eats your anchor, look for what the build system must keep.** Re-anchoring 16 patches for 26.14.0, the decisive obfuscation-proof identifiers were things R8 *cannot* rename because something else depends on the name: Kotlin **enum-constant names**, **string literals**, **resource names** (resolve ids by name through `res/values/public.xml` — ids themselves shift), Kotlin `@Metadata` **module names** (`"square-ad-impl"` located the whole AdManager wrapper package), **layout resource names** of an inflated view, and classes **name-kept for reflection** (a WorkManager `ListenableWorker` subclass is never renamed). Map an obfuscated class by grepping one of those and comparing the full constant set or method shape against the old tree.
- **A string literal is a durable anchor only while it stays in a method whose signature you also pin.** `hidepremium` anchored on `"LITE_ENJOY"` inside a `()Z` accessor; 26.14.0 deleted the accessor and moved the predicate into a suspend evaluator, so the fingerprint matched **zero** methods while the string still existed in the APK. Shape-based lookups fared better in the same bump: `keepunsent`'s guard was found by instruction shape and absorbed its own rename (`h()` → `g()`) for free. Prefer "find the instruction pattern" over "find the name", and when you must anchor on a signature, expect it to be the thing that breaks.
- **R8 also moves code, not just names.** The same bump hoisted a field read out of a guarded block into its method's prologue, which silently emptied a patch's "search after the guard" window even though every descriptor in it was still valid. When a patch fails but its fingerprint still resolves, diff the *instruction order* of the matched method, not just its names.
- **Never blind find/replace a descriptor map across versions.** Prefix renames chain (`fg1`→`wi1` and `wi1`→`nl1` in the same release, so a two-pass replace produced a class that never existed), member letters do not always survive (`hg1.r`→`yi1.p`, `hg1.k`→`yi1.j`), and `\b` does not match between the `L` and the prefix of a JVM descriptor, so `\bfg1/` silently skips every `Lfg1/…;`. Resolve each `(prefix, member)` pair in a single pass through a lookup table.
- **Manifest/resource edits:** `resourcePatch { … execute { document("AndroidManifest.xml").use { doc -> … } } }` — a standard W3C DOM.
- **Kotlin block comments NEST:** a `/*` inside a `/** … */` KDoc (e.g. a `line://home/*` scheme) opens a nested comment and eats the file. Use `//` or reword.
- **Always verify by APPLYING, not just building.** Fingerprints resolve at apply time against the target APK, so a clean `buildAndroid` does not prove a fingerprint matches. Apply with `patch --exclusive -e "<name>"` and disassemble the output. The built `.mpp` filename carries the semantic-release version, so wipe `patches/build/libs` first or a stale artifact gets applied.

### Metadata generation

`util/PatchListGenerator.kt` (`main()`, run by `generatePatchesList`) loads the built `.mpp` via `loadPatchesFromJar`, reads the bundle version from the JAR manifest, and serializes every patch's metadata to `patches-list.json`. Third-party tools consume that file — do not hand-edit it.

## Target app integrity (LINE) — what patching must respect

From decompiling LINE 26.14.0 (detail in `work/decompiled-line-<ver>/NOTES-integrity-checks.md`, gitignored):

- The **core messenger has no enforcing** signature/integrity check — a re-signed patched APK runs fine for login, chat and general features.
- Every root/debugger/emulator/signature check in the general app is **telemetry only** (Firebase Crashlytics, obfuscated `es` package; Sentry `io.sentry.android.core`) — no `exit`/`finish`/`throw`.
- **No Play Integrity / SafetyNet** is bundled. The `attest` code is LINE's own *server-side* WebAuthn/FIDO2 plus a fire-and-forget "DeviceAttestation" WorkManager job that always returns success.
- **Enforcement is confined to LINE Pay**, via the bundled native **VKey V-OS / V-Guard** engine (`libvosWrapperEx.so`; `VosWrapperBase.getAppSignerHash()` → native SHA-256 signer compare), which initializes only on entering Pay flows. Block/Warn/Bypass per threat is **server-driven** (`TamperSettingsGetResDto`); on block, `VGuardDetectionActivity` ends the Pay flow without killing the app.

**Implication:** messaging patches are safe on a re-signed build. Defeating LINE Pay would mean neutralizing the VKey native library (out of scope). Anchor fingerprints on **string literals / non-obfuscated class names** — LINE obfuscates class and method names *including* `org.apache.thrift`'s, so a seemingly-stable framework type is not a safe anchor.

### Re-signed builds & closed-app push notifications (fixed by "Fix push notifications")

Re-signing (Standard install) breaks push while the app is *fully closed*. Unlike the Google sign-in limitation below, this **is** fixable in-APK — `patches/line/fixpushnotifications/`, device-confirmed on a re-signed LINE 26.11.0, descriptors re-anchored for 26.14.0.

- **Symptom:** notifies while open or backgrounded (LINE's persistent **LEGY** socket) but not when swiped from Recents; reopening flushes the queue. Closed, LINE's only wakeup path is **FCM**.
- **Root cause:** LINE's bundled Firebase Installations (FIS) SDK self-reports the signing-cert SHA-1 in the `X-Android-Cert` header (`ct.c.c(URL,String)` → `rl.a.a` = `getPackageInfo(pkg, GET_SIGNATURES)` → SHA-1 of `signatures[0]` → uppercase hex in `rl.h.b`). LINE's embedded Firebase API key is restricted to LINE's official cert SHA-1, so a re-signed APK is rejected. Evidence: `/data/data/jp.naver.line.android/no_backup/PersistedInstallation.*.json` = `"Status":4` (REGISTER_ERROR) + `"FisError":"BAD CONFIG"` + `"TokenCreationEpochInSecs":0` → no FCM token, so LINE's servers have nothing to push to. Battery/App-Standby ruled out (bucket `5` EXEMPTED + deviceidle-whitelisted still fails).
- **Why it's patchable:** the cert is a self-reported header built inside LINE's own process, so a patch can overwrite it. The patch anchors the FIS request builder on the `X-Android-Cert` string plus its `addRequestProperty` call and rewrites the value register with LINE's original SHA-1. Only that call site is touched; the shared `rl.a`/`rl.h` helpers (also used by Remote Config) stay intact.
- **Which cert:** `base.apk` uses APK Signature Scheme **v3.1 key rotation** (two certs) and `GET_SIGNATURES` returns the lineage-**root** one, so the patch injects the SDK 24–32 signer `89396DC419292473972813922867E6973D6F5C50`. Fallback if `BAD CONFIG` persists: the rotated SDK 33+ signer `6A2927D945AEA6571E1DA5566802F25045D367BD`. Re-derive both with `apksigner verify --print-certs base.apk` on a version bump — 26.14.0 carries the same v3.1 lineage, so both hashes are unchanged and this patch needed no edit.
- **Verify:** disassemble `ct/c` — `const-string v$reg, "<sha1>"` must land in the `addRequestProperty` value register (`registerE`) right before the `X-Android-Cert` send. On device, `PersistedInstallation*.json` `Status` should flip `4 → 3` (REGISTERED). **The on-device flip is the real proof;** disassembly only shows the header was rewritten.

### Known limitation: Google account sign-in on re-signed builds

Also documented user-facing as a "Known limitation" in `README.md` — update both together. Same *class* of cause as the FCM break (Google config pinned to LINE's official cert SHA-1) but a distinct mechanism, and only half fixable. Device-confirmed on LINE 26.11.0 / Android 16; not a patch bug. **Full investigation, anchors and the GmsCore route in `docs/line-patch-map.md` — read it before touching this; do not re-derive.**

LINE reaches a Google account by **two paths that share nothing**, and conflating them cost a round of wrong conclusions:

| | Path 1 — log in / link a Google account | Path 2 — Drive chat-history backup |
|---|---|---|
| Route | `jl0.t` → `kl0.i` → `androidx.credentials.CredentialManager` | `ix4.a0` → `AccountPicker.newChooseAccountIntent` → `GoogleAuthUtil` |
| Provider chosen by | the **system** (`system_server` picks `…credman.service.GoogleIdService`) | **LINE**, via a hardcoded `setPackage` |
| Patchable | **No** — LINE never names Play Services, so no package, action or string exists to rewrite, and MicroG-RE implements no `CredentialProviderService` | **Yes** — the `gmscoreauth` patch, device-confirmed 2026-08-12 |

Both fail because an Android OAuth client is keyed to (package name, signing cert): re-signing means no client matches and Google refuses the `drive.appdata` grant with `UNREGISTERED_ON_API_CONSOLE`. (Contrast FCM, which fails on an API-key restriction → `BAD CONFIG`.)

Path 1's only workaround is install-side: a **Root Mount** install keeps the original signature, so both paths work; **Standard** re-signs. On a version bump, re-check by watching `logcat | grep -iE "START u0|CredentialManager|Auth\.Api|SpoofUtils|UNREGISTERED_ON_API_CONSOLE"` at the account-selection tap — the obfuscated names (`ix4.a0`, `aq.a`, `kl0.i`, `u7.*`, `l18/d`, `hx4/i`) drift.

## Feature limits — client-side vs server-enforced (LINE)

Before patching any LINE "limit", establish **who decides**. A constraint LINE computes or self-reports **inside its own process** is patchable; one decided by a remote server or another process is not, and removing the client check just moves the failure later. Anchors for all of these are in `docs/line-patch-map.md`. **Descriptors in the table below are 26.11.0-era** for the surfaces that ship no patch (outbound photo, ringtone, video limits) — they were not re-derived for 26.14.0. Note `c81` there is the *old* video/file-size package and is unrelated to 26.14.0's `c81` (the context-menu action enum, formerly `j51`).

| Limit | Who decides | Verdict |
|---|---|---|
| Outbound photo compression | **client** — `u13.c1` resamples and JPEG-encodes locally; the server never sees the original | patchable, but **dropped**: one gate per entry point and only the chatroom flow was coverable |
| Unsend time window | **server** — `unsendMessage` carries only `(seq, messageId)`; `MESSAGE_NOT_DESTRUCTIBLE(71)` comes back | not patchable |
| Video length / file size | **server** — client checks in `c81.b.c()` mirror an OBS ceiling (`EXCEED_FILE_MAX_SIZE`) | not usefully patchable |
| LYP premium entitlements | **server** — account state; `hidepremium` only hides the upsells | not patchable |
| Call ringtone selection | **client** — `be7.c.a` can return an arbitrary `Uri`, played by LINE's own `MediaPlayer` in-process (`xx.c`) | **patchable** — investigated, deliberately not shipped |
| Ringback tone friends hear | **server** — the callee's tone reaches the *caller's* client in their connect info | not patchable |

- **Client-side config is not server enforcement.** Unsend windows and photo tiers are both pushed from server settings (`function.chatroom.message.unsend.timelimit`, `function.media.image_high`), but the photo tier is only consumed locally to decide how hard to compress whereas the unsend window pre-filters a decision the server re-makes. A server-supplied *value* is patchable; a server-made *decision* is not.
- **Look for the error code.** A dedicated Thrift/HTTP error for the exact condition (`MESSAGE_NOT_DESTRUCTIBLE`, `EXCEED_FILE_MAX_SIZE`, `EXCEED_DAILY_QUOTA`) proves the server enforces it independently; the client check just fails fast.
- **A client check with no server counterpart is the opportunity.** Nothing validates the *encoded* output of LINE's image pipeline: `c1.o()` is a single-shot `Bitmap.compress`, and the picker's gates test the **source** file.
- **Ask which process opens the resource, not just which decides.** The call ringtone is patchable because LINE's own `MediaPlayer` opens the URI in-process; Google sign-in is not, because GmsCore reads the real signature in another process.
- **"Patchable" is not "worth patching."** The ringtone came out patchable and shipped nothing: following the device ringtone is a feature nobody asked for, and an in-app picker needs a new Activity plus a settings row injected into obfuscated declarative Kotlin. Record the finding in `docs/line-patch-map.md` and stop.

## Release pipeline — do not fight it

Fully automated by **semantic-release** (`.releaserc`, `.github/workflows/release.yml`):

- **All development happens on `dev`.** `dev` produces pre-releases; merging `dev → main` (plain merge, **not squash**) produces a stable release. A push to `dev` auto-opens the `dev → main` PR (`open_pull_request.yml`).
- **Use conventional commits.** `fix:` → patch bump, `feat:` → minor bump, `bump:`/`perf:` also release; `chore:`/`build:` do not. The type determines the version and the changelog section.
- **Never hand-edit generated files:** `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, `gradle.properties` (`version`), and README's `<!-- PATCHES_START -->`…`<!-- PATCHES_END -->` block (generated by `.github/scripts/generate_patches_readme.py`).
- **Never manually create or upload GitHub releases**, and never force-push a semantic-release commit — either breaks the release state.
- **`release.yml`'s draft→upload→publish flow is deliberate** (semantic-release drafts the release, a step uploads the `patches-*.mpp`, then it publishes) plus a build-provenance attestation. It is what makes this compatible with GitHub immutable releases, which reject uploads to a published release. Don't collapse it. (`open_pull_request.yml` tolerates a compare 404 so it never hard-fails.)

## Decompiled reference

Fingerprint authoring relies on inspecting LINE's bytecode.

- **`docs/line-patch-map.md`** (tracked) — the offline build/disassembly recipe, per-surface class and anchor maps for the shipped patches, the investigated dead ends, and the areas deliberately not shipped. **`docs/line-premium-map.md`** covers LYP premium gating. Update both when bumping the pinned LINE version; the obfuscated descriptors drift.
- **`work/decompiled-line-<version>/`** (gitignored) — `apktool/` has the smali fingerprints match against, plus resources; `jadx/` has readable Java. Regenerate with `apktool d` / `jadx` from `work/apkm-extract/base.apk`, and grep `apktool/smali*` for anchors.
