package app.andrewliang.patches.facebook.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/** Kept GraphQL model class — Redex never renames it. */
internal const val FEED_UNIT_EDGE = "Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;"

/** Kept enum class. Its fields are renamed; its constant names survive in `<clinit>`. */
internal const val FEED_STORY_CATEGORY =
    "Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;"

/**
 * `FeedUnitCollectionManager.addNewEdgeToCollection` — the single funnel every news-feed edge
 * passes through into the `FeedUnitCollection`. Redex keeps the method name and it is unique
 * across all 20 dex files.
 *
 * Returning false is an outcome the app already handles: it logs "Edge not added to FUC" and the
 * caller carries on.
 */
internal object AddNewEdgeToCollectionFingerprint : Fingerprint(
    name = "addNewEdgeToCollection",
    returnType = "Z",
)

/**
 * The only zero-argument accessor on [FEED_UNIT_EDGE] returning the story-category enum (`B8f()`
 * here), selected by return type. It resolves through `getCachedEnum` with a default, so it never
 * returns null.
 */
internal fun BytecodePatchContext.storyCategoryGetter(): String =
    mutableClassDefBy(FEED_UNIT_EDGE).methods.single {
        it.returnType == FEED_STORY_CATEGORY && it.parameterTypes.isEmpty()
    }.name

/**
 * Resolve a [FEED_STORY_CATEGORY] constant to its obfuscated field via the enum's `<clinit>`.
 *
 * Bind by position, not by register: the constant name is loaded into one register and the enum
 * instance built in another, so the first `sput-object` of the enum's own type after the literal is
 * that constant's field. Matching on the name's register resolves the wrong constant.
 */
internal fun BytecodePatchContext.storyCategoryField(constant: String): String {
    val clinit = mutableClassDefBy(FEED_STORY_CATEGORY).methods.single { it.name == "<clinit>" }
    val instructions = clinit.implementation!!.instructions.toList()

    instructions.forEachIndexed { index, instruction ->
        val string = (instruction as? ReferenceInstruction)?.reference as? StringReference
        if (string?.string != constant) return@forEachIndexed

        for (next in index + 1 until instructions.size) {
            val candidate = instructions[next]
            if (candidate.opcode != Opcode.SPUT_OBJECT) continue
            val field = (candidate as ReferenceInstruction).reference as FieldReference
            if (field.type == FEED_STORY_CATEGORY) return field.name
        }
    }

    error("GraphQLFeedStoryCategory.$constant not found in <clinit>")
}

/**
 * The [FEED_UNIT_EDGE] accessor returning the edge's feed unit, inflating it if the tree has not
 * materialised it yet (`BQd()` here).
 *
 * Two zero-argument methods return the feed-unit interface — the plain cached getter and this
 * wrapper, which calls it. Pick the wrapper by its `"inflateFeedUnit"` literal; it is what the
 * surrounding code calls anyway, so its cost is already paid.
 */
internal fun BytecodePatchContext.feedUnitGetter(): Method =
    mutableClassDefBy(FEED_UNIT_EDGE).methods.single { method ->
        method.parameterTypes.isEmpty() &&
            method.implementation?.instructions.orEmpty().any {
                ((it as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                    "inflateFeedUnit"
            }
    }
