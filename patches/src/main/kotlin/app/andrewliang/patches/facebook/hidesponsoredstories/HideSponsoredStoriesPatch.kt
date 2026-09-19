package app.andrewliang.patches.facebook.hidesponsoredstories

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideSponsoredStoriesPatch = bytecodePatch(
    name = "[Stories] Hide sponsored stories",
    description = "Removes ad cards from the story viewer, so swiping through stories only shows " +
        "stories people posted.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Return the caller's list unchanged rather than filtering the result: Facebook does exactly
    // this when a bucket data source has nothing to contribute, and it avoids depending on the ad
    // bucket's numeric type -- a bare integer with no name behind it, and the one thing here that
    // could silently change between releases.
    //
    // All four sources are neutered, not just one. They sit in the same chain, a factory picks
    // between two of them by flag, and one of them only fetches once the viewer is already open --
    // which is the ad that survives into a session and disappears on the next cold start.
    execute {
        listOf(
            InorganicClashResolverFingerprint,
            AdBucketDataSourceFingerprint,
            AdPaginatingBucketInserterFingerprint,
            StoryViewerMidCardFingerprint,
        ).forEach { fingerprint ->
            val method = fingerprint.method

            val listParameter = method.parameterTypes.indexOfLast { it == IMMUTABLE_LIST }
            check(listParameter >= 0) {
                "${method.definingClass}->${method.name} takes no ImmutableList to pass through: " +
                    method.parameterTypes.joinToString()
            }

            // p0 is `this` (the method is not static), so the Nth parameter is p(N + 1).
            method.addInstructions(0, "return-object p${listParameter + 1}")
        }
    }
}
