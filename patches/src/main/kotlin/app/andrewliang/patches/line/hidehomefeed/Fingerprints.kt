package app.andrewliang.patches.line.hidehomefeed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.string

/**
 * `toString` of the GCS page info (`GcsPageWithModules`). The patch takes the page type from it,
 * to build [loadMoreContentFingerprint].
 */
internal object GcsPageToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("GcsPageWithModules(pageId=")),
)

/**
 * The "load more module content" action of the GCS page view model
 * (`com.linecorp.line.gcs.page.ui`, obfuscated, which implements the page action interface). The
 * method asks the page repository for the next feed page.
 *
 * The patch makes this method return at once. The filter makes the Home tab short, thus LINE's
 * pager trigger (`lastVisibleIndex + 6 >= itemCount`) stays true. The app then asks for page
 * after page, and the filter discards each one. See HideHomeFeedPatch.
 *
 * The whole app calls this action from one place: the pager observer
 * `GcsPageState$observeEventsForLoadingModuleContent$1`. LINE also ships an empty implementation
 * of the action interface (every method empty), thus an empty action is a state that LINE builds
 * itself.
 *
 * Anchored on shape: the `(Z)V` signature, the read of the page repository field, and the cast of
 * the page info to [pageType]. R8 renames both types, so the patch builds this fingerprint at
 * patch time. The repository is the outer class of the Home state class.
 */
internal fun loadMoreContentFingerprint(repositoryType: String, pageType: String) = Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(type = repositoryType),
        checkCast(pageType),
    ),
)
