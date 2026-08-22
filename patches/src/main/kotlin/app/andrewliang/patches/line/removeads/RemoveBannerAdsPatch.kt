package app.andrewliang.patches.line.removeads

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val RETURN_NULL = """
    const/4 v0, 0x0
    return-object v0
"""

@Suppress("unused")
val removeBannerAdsPatch = bytecodePatch(
    name = "Remove banner ads",
    description = "LINE no longer loads the Smart Channel banner ads. This patch makes the " +
        "getBanners and getPrefetchableBanners responses null.",
    default = true, // applied by default in Morphe Manager. Users can deselect it.
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Both RECV methods are `.locals 1`, so v0 is free. Returning null is safe: the caller
    // (repository fi5.a.a) wraps the whole path in a catch-all and degrades to "no banner".
    execute {
        // getPrefetchableBanners: dedicated recv lambda -> return null immediately.
        GetPrefetchableBannersRecvFingerprint.method.addInstructions(0, RETURN_NULL)

        // getBanners: SHARED packed-switch (default branch = a live wallet op). Inject the
        // null-return inside the getBanners branch only, right after its
        // `const-string "getBanners"` (before the paired o.a recv call), so the wallet
        // default branch (which returns earlier) is untouched. instructionMatches[0] is the
        // string("getBanners") filter match.
        val bannerStringIndex = GetBannersRecvFingerprint.instructionMatches.first().index
        GetBannersRecvFingerprint.method.addInstructions(bannerStringIndex + 1, RETURN_NULL)
    }
}
