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
    // (repository eq5.a.a) wraps the whole path in a catch-all and degrades to "no banner".
    execute {
        // getPrefetchableBanners: dedicated recv lambda -> return null immediately.
        GetPrefetchableBannersRecvFingerprint.method.addInstructions(0, RETURN_NULL)

        // getBanners: inject the null-return right after its `const-string "getBanners"`
        // (before the paired n.a recv call). On 26.11.0 this method was a shared
        // packed-switch and injecting past the string was required to spare the wallet
        // default branch; on 26.14.0 it is a dedicated lambda, so this is simply the first
        // safe point. instructionMatches[0] is the string("getBanners") filter match.
        val bannerStringIndex = GetBannersRecvFingerprint.instructionMatches.first().index
        GetBannersRecvFingerprint.method.addInstructions(bannerStringIndex + 1, RETURN_NULL)
    }
}
