package app.andrewliang.patches.facebook.hideaffiliatelinks

import app.andrewliang.patches.facebook.hidereelprompts.ReelOverlayTypeFingerprint
import app.andrewliang.patches.facebook.shared.enumConstantField
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val AFFILIATE_EYEBROW = "AFFILIATE_EYEBROW"
private const val FLOATING_CARD_PLUGIN = "Lcom/facebook/feedback/comments/plugins/indicatorpill/" +
    "organicaffiliatefloatingcta/OrganicAffiliateFloatingCtaPlugin;"

/**
 * A method of the feed video plugin that keeps its name. It asks for the id of the server-built
 * footer of the attachment, and that call is its only call that returns a string.
 */
internal object FooterHiddenFingerprint : Fingerprint(
    returnType = "Z",
    custom = { method, _ -> method.name.startsWith("isFooterHidden\$") },
)

/**
 * The model of the floating product card in the comment sheet. The plugin keeps its class name. Its
 * one static method with an argument reads the card from the comment sheet data, or returns null
 * when there is no card.
 */
internal object FloatingCardModelFingerprint : Fingerprint(
    definingClass = FLOATING_CARD_PLUGIN,
    returnType = "L",
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) && method.parameterTypes.size == 1
    },
)

@Suppress("unused")
val hideAffiliateLinksPatch = bytecodePatch(
    name = "[General] Hide affiliate product links",
    description = "Removes the product cards of affiliate shop links from Reels, feed posts and " +
        "comments. The \"Commission eligible\" label stays.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // A creator can attach a shop link (for example Shopee) to a post and earn a commission. The
    // server sends the link with the post, and Facebook shows a product card for it in three
    // places. Each place asks its own gate, and the patch makes each gate say "no link". The
    // "Commission eligible" label is a disclosure, and the patch does not change it.
    execute {
        hideReelProductCard()
        hideFeedProductFooter()
        hideCommentProductCard()
    }
}

/**
 * The product card on the reel is the overlay item `AFFILIATE_EYEBROW`. The builder of the
 * overlay list adds it only when a predicate is true:
 *
 * ```
 * invoke-static {…}, predicate(FbUserSession, reel, PlayerOrigin)Z
 * move-result vX
 * if-eqz vX, :skip
 * sget-object vY, AFFILIATE_EYEBROW
 * invoke-virtual {list, vY}, AbstractCollection.add
 * ```
 *
 * The overlay render also asks the predicate, so a false result removes the card everywhere.
 */
private fun BytecodePatchContext.hideReelProductCard() {
    val overlayType = ReelOverlayTypeFingerprint.method.definingClass
    val eyebrowField = enumConstantField(overlayType, AFFILIATE_EYEBROW)

    val predicates = mutableSetOf<Triple<String, String, String>>()
    classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            val instructions = method.implementation?.instructions?.toList() ?: return@forEach
            instructions.forEachIndexed { index, instruction ->
                if (index < 3 || instruction.opcode != Opcode.SGET_OBJECT) return@forEachIndexed
                val field = (instruction as ReferenceInstruction).reference as FieldReference
                if (field.definingClass != overlayType || field.name != eyebrowField) return@forEachIndexed

                val next = instructions.getOrNull(index + 1) ?: return@forEachIndexed
                val add = (next as? ReferenceInstruction)?.reference as? MethodReference
                if (add?.name != "add") return@forEachIndexed

                val call = instructions[index - 3]
                if (call.opcode != Opcode.INVOKE_STATIC ||
                    instructions[index - 2].opcode != Opcode.MOVE_RESULT ||
                    instructions[index - 1].opcode != Opcode.IF_EQZ
                ) return@forEachIndexed

                val predicate = (call as ReferenceInstruction).reference as MethodReference
                if (predicate.returnType != "Z") return@forEachIndexed
                predicates += Triple(
                    predicate.definingClass,
                    predicate.name,
                    predicate.parameterTypes.joinToString(""),
                )
            }
        }
    }
    check(predicates.size == 1) { "Expected 1 reel affiliate card predicate, found $predicates" }

    val (owner, name, parameters) = predicates.single()
    mutableClassDefBy(owner).methods.single {
        it.name == name && it.returnType == "Z" && it.parameterTypes.joinToString("") == parameters
    }.returnFalse()
}

/**
 * Under the video of a feed post, the product card is a server-built (Bloks) footer. One method
 * gives the id of that footer, or null when the attachment has none. The footer builder and the
 * video plugin both ask it. With a null id, the builder uses the plain footer, which is empty for
 * these posts.
 */
private fun BytecodePatchContext.hideFeedProductFooter() {
    val footerIds = FooterHiddenFingerprint.method.implementation!!.instructions.mapNotNull {
        if (it.opcode != Opcode.INVOKE_STATIC) return@mapNotNull null
        ((it as ReferenceInstruction).reference as MethodReference)
            .takeIf { call -> call.returnType == "Ljava/lang/String;" }
    }.toSet()
    check(footerIds.size == 1) { "Expected 1 footer id call, found $footerIds" }

    val footerId = footerIds.single()
    mutableClassDefBy(footerId.definingClass).methods.single {
        it.name == footerId.name &&
            it.parameterTypes.map { type -> type.toString() } == footerId.parameterTypes.map { type -> type.toString() }
    }.returnNull()
}

/**
 * The comment sheet asks the plugin for its model twice: to decide if the plugin shows, and to
 * build it. The first check returns false when the model is null, thus the build never runs. The
 * sheet gets a null model for every reel with no shop link.
 */
private fun BytecodePatchContext.hideCommentProductCard() {
    FloatingCardModelFingerprint.method.returnNull()
}

/** At the start of a method, v0 is free if it is not a parameter register. */
private fun MutableMethod.hasFreeV0(): Boolean {
    val wide = parameterTypes.count { it.toString() == "J" || it.toString() == "D" }
    val parameterRegisters = parameterTypes.size + wide +
        if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    return implementation!!.registerCount > parameterRegisters
}

private fun MutableMethod.returnNull() {
    check(hasFreeV0()) { "v0 of $definingClass->$name is a parameter" }
    addInstructions(
        0,
        """
            const/4 v0, 0x0
            return-object v0
        """,
    )
}

private fun MutableMethod.returnFalse() {
    check(hasFreeV0()) { "v0 of $definingClass->$name is a parameter" }
    addInstructions(
        0,
        """
            const/4 v0, 0x0
            return v0
        """,
    )
}
