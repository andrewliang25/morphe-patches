package app.andrewliang.patches.line.hidehomemodules

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * `i52.c.e(f0, c): i0` — builds the Home UI state `m52.i0`, whose module list (`List<m52.z>`,
 * each `z.e` a typed `m52.a0` module) is assembled into a register and passed as the 3rd
 * constructor arg. We inject a filter right before that `m52.i0.<init>` call.
 *
 * `i52.c` is obfuscated (version-brittle); anchored on the return type `Lm52/i0;`, the
 * `(f0, c)` params, and the `m52.i0.<init>` call the injection is placed before.
 */
internal object HomeStateBuilderFingerprint : Fingerprint(
    definingClass = "Li52/c;",
    name = "e",
    returnType = "Lm52/i0;",
    parameters = listOf("Lm52/f0;", "Lm52/c;"),
    filters = listOf(
        methodCall(definingClass = "Lm52/i0;", name = "<init>"),
    ),
)
