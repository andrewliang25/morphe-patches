package app.andrewliang.patches.facebook.hidesponsoredposts

import app.andrewliang.patches.facebook.shared.AddNewEdgeToCollectionFingerprint
import app.andrewliang.patches.facebook.shared.FEED_STORY_CATEGORY
import app.andrewliang.patches.facebook.shared.FEED_UNIT_EDGE
import app.andrewliang.patches.facebook.shared.storyCategoryField
import app.andrewliang.patches.facebook.shared.storyCategoryGetter
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

private const val FILTER_NAME = "isSponsoredFeedUnitEdge"
private val FILTER_DESC = "($FEED_STORY_CATEGORY)Z"

@Suppress("unused")
val hideSponsoredPostsPatch = bytecodePatch(
    name = "[Feed] Hide sponsored posts",
    description = "Removes sponsored posts from the news feed, with no gap left behind.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Rejecting at the collection boundary means nothing downstream sees the ad: no placeholder,
    // no impression. The edge already carries the server's SPONSORED category, so this costs one
    // cached-enum lookup rather than a walk of the story tree for sponsored_data.
    execute {
        val categoryGetter = storyCategoryGetter()
        val sponsored = storyCategoryField("SPONSORED")

        val method = AddNewEdgeToCollectionFingerprint.method
        val host = method.definingClass

        // The comparison against SPONSORED needs two registers (the category and the constant),
        // but the chokepoint itself can be trimmed to only one truly free register (its other
        // registers hold `this` and its declared parameters, read again right after this prelude).
        // A tiny static method of its own has registers no caller shares, so the comparison lives
        // there instead of borrowing a second register from the chokepoint.
        val filter = MutableMethod(
            ImmutableMethod(
                host,
                FILTER_NAME,
                listOf(ImmutableMethodParameter(FEED_STORY_CATEGORY, null, null)),
                "Z",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(2),
            ),
        )
        mutableClassDefBy(host).methods.add(filter)
        // p0 = the edge's category, v0 = scratch, reused after the comparison for the answer.
        filter.addInstructions(
            0,
            """
                sget-object v0, $FEED_STORY_CATEGORY->$sponsored:$FEED_STORY_CATEGORY
                if-ne p0, v0, :no
                const/4 v0, 0x1
                return v0
                :no
                const/4 v0, 0x0
                return v0
            """,
        )

        // p2 (the edge) must be copied down: invoke-virtual takes 4-bit register operands. Only v0
        // is touched here; the chokepoint's other registers (this and its own parameters) are read
        // again right after this prelude and must not be clobbered.
        method.addInstructionsWithLabels(
            0,
            """
                move-object/from16 v0, p2
                invoke-virtual { v0 }, $FEED_UNIT_EDGE->$categoryGetter()$FEED_STORY_CATEGORY
                move-result-object v0
                invoke-static { v0 }, $host->$FILTER_NAME$FILTER_DESC
                move-result v0
                if-eqz v0, :keep
                const/4 v0, 0x0
                return v0
            """,
            ExternalLabel("keep", method.getInstruction(0)),
        )
    }
}
