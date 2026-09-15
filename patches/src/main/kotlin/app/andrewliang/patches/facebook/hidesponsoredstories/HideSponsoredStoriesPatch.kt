package app.andrewliang.patches.facebook.hidesponsoredstories

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideSponsoredStoriesPatch = bytecodePatch(
    name = "[Stories] Hide sponsored stories",
    description = "Removes ad cards from the Stories tray and from the story viewer, so swiping " +
        "through stories only shows stories people posted.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Return the caller's list unchanged rather than filtering the result: Facebook does exactly
    // this when its own insertion gate is off, and it avoids depending on the ad bucket's numeric
    // type -- a bare integer with no name behind it, and the one thing here that could silently
    // change between releases.
    execute {
        val method = StoriesTrayAdInserterFingerprint.method

        val listParameter = method.parameterTypes.indexOfLast { it == IMMUTABLE_LIST }
        check(listParameter >= 0) {
            "Stories tray ad inserter takes no ImmutableList to pass through: " +
                method.parameterTypes.joinToString()
        }

        // p0 is `this` (the method is not static), so the Nth parameter is p(N + 1).
        method.addInstructions(0, "return-object p${listParameter + 1}")
    }
}
