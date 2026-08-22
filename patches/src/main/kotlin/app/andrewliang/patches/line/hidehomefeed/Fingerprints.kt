package app.andrewliang.patches.line.hidehomefeed

import app.morphe.patcher.Fingerprint

/**
 * `x72.h$a.<init>(List<m52.z>, ...)` — the constructor of the Home Compose UI state. The state
 * holds the module list that the tab shows, in field `a`, the first ctor argument. Every feed
 * build path goes to this constructor: the v52.g and v52.j assemblers, and the state copies.
 * One filter on the list argument here thus covers the whole feed.
 *
 * This object is a copy of the fingerprint in `hidehomemodules` for the same method, and not a
 * shared object. The two patches are independent, and the user can apply either one alone. Both
 * prepend a `List -> List` filter call at index 0 of this constructor. The order does not matter
 * (see HideHomeFeedPatch).
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
