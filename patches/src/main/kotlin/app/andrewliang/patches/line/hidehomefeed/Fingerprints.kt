package app.andrewliang.patches.line.hidehomefeed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess

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

/**
 * `v72.h2.B1(Z)V` — the "load more module content" action of the GCS page view model
 * (`com.linecorp.line.gcs.page.ui`, obfuscated `v72.h2`, which implements the page action
 * interface `v72.d2`). The method asks the page repository `x72.h` for the next feed page.
 *
 * The patch makes this method return at once. The filter makes the Home tab short, thus LINE's
 * pager trigger (`lastVisibleIndex + 6 >= itemCount`) stays true. The app then asks for page
 * after page, and the filter discards each one. See HideHomeFeedPatch.
 *
 * The whole app calls `d2.B1` from one place: the pager observer
 * `GcsPageState$observeEventsForLoadingModuleContent$1`. LINE also ships an empty `d2` of its
 * own (`b82.z`, every method empty), thus an empty `B1` is a state that LINE builds itself.
 *
 * Anchored on shape, and not on the drift-prone name `B1`: the `(Z)V` signature, the read of
 * the `x72.h` repository field, and the cast of the page info to `m52.i0`. On LINE 26.11.0
 * that combination matches one method in the APK. This object never names the obfuscated
 * types `v72.h2`, `v72.d2` or `x72.r`.
 */
internal object LoadMoreContentFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(type = "Lx72/h;"),
        checkCast("Lm52/i0;"),
    ),
)
