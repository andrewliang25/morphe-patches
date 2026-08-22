package app.andrewliang.patches.line.hidehomefeed

import app.morphe.patcher.Fingerprint

/**
 * `x72.h$a.<init>(List<m52.z>, ...)` — the constructor of the Home Compose UI state that holds
 * the rendered module list (stored into field `a`, the first ctor arg). Every feed build path
 * (the v52.g / v52.j assemblers, and state copies) funnels through this constructor, so
 * filtering the list argument here covers the whole rendered feed in one place.
 *
 * Deliberately a copy of `hidehomemodules`' fingerprint of the same method rather than a shared
 * object: the two patches are independent and either can be applied alone. They both prepend a
 * `List -> List` filter call at index 0 of this constructor, which composes in any order (see
 * HideHomeFeedPatch).
 */
internal object HomeStateCtorFingerprint : Fingerprint(
    definingClass = "Lx72/h\$a;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/util/List;",
        "Z", "Z", "Z", "Z", "Z",
        "Ljava/lang/String;",
        "Ljava/lang/Long;",
        "Ljava/lang/Long;",
        "I",
        "Z",
    ),
)
