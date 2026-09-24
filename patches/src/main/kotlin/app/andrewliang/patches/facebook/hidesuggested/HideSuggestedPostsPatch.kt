package app.andrewliang.patches.facebook.hidesuggested

import app.andrewliang.patches.facebook.shared.AddNewEdgeToCollectionFingerprint
import app.andrewliang.patches.facebook.shared.FEED_UNIT_EDGE
import app.andrewliang.patches.facebook.shared.feedUnitGetter
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val FILTER_NAME = "isSuggestedOrPromotedFeedUnit"
private const val FILTER_DESC = "(Ljava/lang/Object;)Z"

/**
 * Units Facebook injects into the feed that are not paid ads. All keep their real names through
 * Redex, so `instance-of` needs no obfuscated identifier.
 *
 * Excluded: `GraphQLFriendsLocationsFeedUnit` (a real feature), and People You May Know — its
 * container feed unit is not name-kept, so dropping the reachable item types would not remove the
 * row.
 */
private val SUGGESTED_FEED_UNITS = listOf(
    // "Pages you may like" and its variants.
    "Lcom/facebook/graphql/model/GraphQLPagesYouMayLikeFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLPaginatedPagesYouMayLikeFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLCreativePagesYouMayLikeFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLPYMLWithLargeImageFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLPagesYouMayFollowFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLPagesYouMayAdvertiseFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLPymgfFeedUnit;",
    // Facebook's own in-feed upsell nags.
    "Lcom/facebook/graphql/model/GraphQLQuickPromotionFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLQuickPromotionNativeTemplateFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLEndOfFeedUpsellCustomNTFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLExploreFeedUpsellNTUnit;",
    "Lcom/facebook/graphql/model/GraphQLGreetingCardPromotionFeedUnit;",
    // In-feed prompts and experiment slots.
    "Lcom/facebook/graphql/model/GraphQLStoryGallerySurveyFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLBusinessPageReviewFeedUnit;",
    "Lcom/facebook/graphql/model/GraphQLHoldoutAdFeedUnit;",
)

@Suppress("unused")
val hideSuggestedPostsPatch = bytecodePatch(
    name = "[Feed] Hide suggested and promoted posts",
    description = "Removes posts that Facebook adds to the feed, such as \"Pages you may " +
        "like\", upsells and surveys.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Same chokepoint as "Hide sponsored posts"; both prepend a guard returning false, so they
    // compose in either order.
    //
    // The instance-of chain needs labels, and a label inside injected smali resolves against the
    // injected block's addresses instead of the method's, which ART rejects. In a method we build
    // ourselves the two address spaces coincide, so the chain lives in a new static method.
    execute {
        val feedUnits = SUGGESTED_FEED_UNITS.filter { classDefByOrNull(it) != null }
        check(feedUnits.isNotEmpty()) {
            "No suggested feed unit model classes found; the model package was renamed or moved"
        }

        val target = AddNewEdgeToCollectionFingerprint.method
        val host = target.definingClass

        val filter = MutableMethod(
            ImmutableMethod(
                host,
                FILTER_NAME,
                listOf(ImmutableMethodParameter("Ljava/lang/Object;", null, null)),
                "Z",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(2),
            ),
        )
        mutableClassDefBy(host).methods.add(filter)
        // p0 = the feed unit. instance-of on null is false, so a null unit falls through to the
        // original code. v0 = scratch.
        filter.addInstructions(
            0,
            buildString {
                feedUnits.forEach { feedUnit ->
                    appendLine("instance-of v0, p0, $feedUnit")
                    appendLine("if-nez v0, :hit")
                }
                appendLine("const/4 v0, 0x0")
                appendLine("return v0")
                appendLine(":hit")
                appendLine("const/4 v0, 0x1")
                appendLine("return v0")
            },
        )

        val feedUnitGetter = feedUnitGetter()
        // p2 (the edge) must be copied down: invoke-virtual takes 4-bit register operands.
        target.addInstructionsWithLabels(
            0,
            """
                move-object/from16 v0, p2
                invoke-virtual { v0 }, $FEED_UNIT_EDGE->${feedUnitGetter.name}()${feedUnitGetter.returnType}
                move-result-object v0
                invoke-static { v0 }, $host->$FILTER_NAME$FILTER_DESC
                move-result v0
                if-eqz v0, :keep
                const/4 v0, 0x0
                return v0
            """,
            ExternalLabel("keep", target.getInstruction(0)),
        )
    }
}
