package app.andrewliang.patches.facebook.hidesponsoredreels

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideSponsoredReelsPatch = bytecodePatch(
    name = "[Reels] Hide sponsored reels",
    description = "Stops ads being inserted into Reels and Watch, so scrolling only shows videos " +
        "from creators. Ads that play inside a video, such as mid-rolls, are not covered.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Blocking the page-load insert alone leaves three on-demand inserts running, which is why an
    // ad could still appear after a while of scrolling and was gone once the app restarted: those
    // three write straight into the item collection held in memory.
    //
    // Only the inserts are blocked, not the requests that feed them. Stopping the requests as well
    // would save data, but that belongs with the prefetch patch, and the request methods have not
    // been checked for organic side effects.
    execute {
        listOf(
            VideoHomeInsertAdsFingerprint,
            RealtimeIntentAdInsertFingerprint,
            SfdAdInsertFingerprint,
            PoeAdRenderFingerprint,
        ).forEach { it.method.addInstructions(0, "return-void") }
    }
}
