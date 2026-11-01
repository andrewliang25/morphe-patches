package app.andrewliang.patches.facebook.hidesponsoredposts

import app.andrewliang.patches.facebook.shared.AddNewEdgeToCollectionFingerprint
import app.andrewliang.patches.facebook.shared.FEED_STORY_CATEGORY
import app.andrewliang.patches.facebook.shared.FEED_UNIT_EDGE
import app.andrewliang.patches.facebook.shared.addInstructionsIfFacebookSetting
import app.andrewliang.patches.facebook.shared.facebookSettingsExtensionPatch
import app.andrewliang.patches.facebook.shared.markFacebookSettingIncluded
import app.andrewliang.patches.facebook.shared.storyCategoryField
import app.andrewliang.patches.facebook.shared.storyCategoryGetter
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideSponsoredPostsPatch = bytecodePatch(
    name = "[Feed] Hide sponsored posts",
    description = "Removes sponsored posts from the news feed, with no gap left behind.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    dependsOn(facebookSettingsExtensionPatch)

    // Rejecting at the collection boundary means nothing downstream sees the ad: no placeholder,
    // no impression. The edge already carries the server's SPONSORED category, so this costs one
    // cached-enum lookup rather than a walk of the story tree for sponsored_data.
    execute {
        val categoryGetter = storyCategoryGetter()
        val sponsored = storyCategoryField("SPONSORED")

        val method = AddNewEdgeToCollectionFingerprint.method
        // p2 (the edge) is v23 of a 25-register method and invoke-virtual takes 4-bit operands, so
        // it must be copied down first; passing p2 directly assembles to a broken instruction.
        method.addInstructionsIfFacebookSetting(
            "hideSponsoredPosts",
            """
                move-object/from16 v0, p2
                invoke-virtual { v0 }, $FEED_UNIT_EDGE->$categoryGetter()$FEED_STORY_CATEGORY
                move-result-object v0
                sget-object v1, $FEED_STORY_CATEGORY->$sponsored:$FEED_STORY_CATEGORY
                if-ne v0, v1, :stock
                const/4 v0, 0x0
                return v0
            """,
        )

        markFacebookSettingIncluded("hideSponsoredPosts")
    }
}
