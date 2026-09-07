package app.andrewliang.patches.line.fixlocationmaps

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * `fo/p.b(Context, eo/d$a)` — resolves the **maps module context**, the one choke point every LINE
 * map goes through.
 *
 * The body caches the result in a static field, and otherwise asks `DynamiteModule` for the
 * renderer: `maps_core_dynamite` (or `maps_legacy_dynamite` when the caller asked for the legacy
 * renderer), falling back to `maps_dynamite` and finally to `getRemoteContext`. `fo/p.c` then
 * class-loads `com.google.android.gms.maps.internal.CreatorImpl` from whatever context comes back.
 *
 * Its only three callers all live in `fo/p`, so redirecting this method covers the picker, the
 * viewer, both chat bubbles, and the Notes and Timeline location posts at once.
 *
 * Anchored on the three dynamite module names plus the failure string, all four of which sit in
 * this method. They belong to Google's bundled Maps client rather than to LINE's own code, so they
 * survive LINE's obfuscation and its version bumps. `fo/p` itself drifts, so it is never named.
 *
 * The first parameter is pinned because the patch injects `invoke-static { p0 }` against a
 * `(Landroid/content/Context;)` signature. If a bump ever reorders the parameters, this makes the
 * fingerprint stop resolving — a clean failure — instead of emitting a type-incorrect invoke that
 * fails class verification at run time. The second entry is the bare `L` prefix, because the
 * renderer-preference enum `eo/d$a` does drift and the patch never reads it.
 *
 * [instructionMatches] in program order: the legacy module name, the core module name, the
 * `maps_dynamite` fallback, then the "container context is null" log string.
 */
internal object MapsModuleContextFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/content/Context;",
    parameters = listOf("Landroid/content/Context;", "L"),
    filters = listOf(
        string("com.google.android.gms.maps_legacy_dynamite"),
        string("com.google.android.gms.maps_core_dynamite"),
        string("com.google.android.gms.maps_dynamite"),
        string("Unable to load maps module, maps container context is null"),
    ),
)
