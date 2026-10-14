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
    name = "[Ad] Remove banner ads",
    description = "LINE no longer loads the Smart Channel banner ads. This patch makes the " +
        "getBanners and getPrefetchableBanners responses null.",
    default = true, // applied by default in Morphe Manager. Users can deselect it.
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Both RECV lambdas can sit in a shared packed-switch, so inject right after the op-name
    // `const-string` (instructionMatches[0]), inside the op's own case. That const-string has just
    // written v0, so v0 is free. Returning null is safe: the repository caller wraps the whole
    // path in a catch-all and degrades to "no banner".
    execute {
        listOf(GetBannersRecvFingerprint, GetPrefetchableBannersRecvFingerprint).forEach {
            it.method.addInstructions(it.instructionMatches.first().index + 1, RETURN_NULL)
        }
    }
}
