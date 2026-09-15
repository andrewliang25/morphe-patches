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

    execute {
        VideoHomeInsertAdsFingerprint.method.addInstructions(0, "return-void")
    }
}
