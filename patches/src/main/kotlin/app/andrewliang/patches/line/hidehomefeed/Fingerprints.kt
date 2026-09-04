package app.andrewliang.patches.line.hidehomefeed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess

/**
 * `lb2.g$a.<init>(List<y82.j0>, ...)` — the constructor of the Home Compose UI state. The state
 * holds the module list that the tab shows, in field `a`, the first ctor argument. Every feed
 * build path goes to this constructor: the feed assemblers, and the state copies.
 * One filter on the list argument here thus covers the whole feed.
 *
 * This object is a copy of the fingerprint in `hidehomemodules` for the same method, and not a
 * shared object. The two patches are independent, and the user can apply either one alone. Both
 * prepend a `List -> List` filter call at index 0 of this constructor. The order does not matter
 * (see HideHomeFeedPatch).
 */
internal object HomeStateCtorFingerprint : Fingerprint(
    definingClass = "Llb2/g\$a;",
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

/**
 * `jb2.m2.H1(Z)V` — the "load more module content" action of the GCS page view model
 * (`com.linecorp.line.gcs.page.ui`, obfuscated `jb2.m2`, which implements the page action
 * interface `jb2.h2`). The method asks the page repository `lb2.g` for the next feed page.
 *
 * The patch makes this method return at once. The filter makes the Home tab short, thus LINE's
 * pager trigger (`lastVisibleIndex + 6 >= itemCount`) stays true. The app then asks for page
 * after page, and the filter discards each one. See HideHomeFeedPatch.
 *
 * The whole app calls `h2.H1` from one place: the pager observer
 * `GcsPageState$observeEventsForLoadingModuleContent$1`. LINE also ships an empty `d2` of its
 * own (`pb2.y`, every method empty), thus an empty `H1` is a state that LINE builds itself.
 *
 * Anchored on shape, and not on the drift-prone name `H1`: the `(Z)V` signature, the read of
 * the `lb2.g` repository field, and the cast of the page info to `y82.t0`. On LINE 26.11.0
 * that combination matches one method in the APK. This object never names the obfuscated
 * types `jb2.m2`, `jb2.h2` or `lb2.q`.
 */
internal object LoadMoreContentFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(type = "Llb2/g;"),
        checkCast("Ly82/t0;"),
    ),
)
