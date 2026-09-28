package app.andrewliang.patches.facebook.anonymousstoryviews

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/andrewliang/extension/AnonymousStories;"

private const val TREE = "Lcom/facebook/graphservice/tree/TreeJNI;"

/**
 * The hashes of the seen fields: `"is_seen_by_viewer"` of a story and `"is_bucket_seen_by_viewer"`
 * of the stories of one person. Facebook reads a tree field by the `hashCode` of its name.
 */
private val SEEN_FIELDS = setOf(
    "is_seen_by_viewer".hashCode(),
    "is_bucket_seen_by_viewer".hashCode(),
)

private const val STORY_BUCKET = "Lcom/facebook/stories/model/StoryBucket;"

private const val STORY_CARD = "Lcom/facebook/stories/model/StoryCard;"

private const val REGULAR_STORY_CARD = "Lcom/facebook/audience/snacks/model/RegularStoryCard;"

/**
 * `getRequest` of the class that tells the server which stories you saw. Redex keeps the method
 * name. The field names of the request are string literals.
 */
internal object StorySeenRequestFingerprint : Fingerprint(
    name = "getRequest",
    strings = listOf("story_ids_list", "is_story_peek_view"),
)

/**
 * The seen helper of the story viewer. It runs for each card that the viewer shows, and it flushes
 * its queue with the reason "max_queue_size" when the queue is full.
 */
internal object SeenHelperFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("max_queue_size"),
    custom = { method, _ ->
        method.parameterTypes.map { it.toString() }.containsAll(listOf(STORY_BUCKET, STORY_CARD))
    },
)

@Suppress("unused")
val anonymousStoryViewsPatch = bytecodePatch(
    name = "[Stories] View stories anonymously",
    description = "Stops Facebook telling the server which stories you saw, so you are not in " +
        "the viewer list. Stories that you saw still show as seen on this device.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)
    extendWith("extensions/extension.mpe")

    // The story viewer collects the cards that you saw and sends them in one request when it
    // pauses or closes. A logging build showed that this request (DirectSeenMutation) is the only
    // GraphQL call that viewing a story makes.
    //
    // Facebook marks a story as seen on the device only when the server answers that request. So
    // the patch does three things:
    //
    // 1. The sender returns at once. It returns void, so nothing reads a result.
    // 2. The seen helper of the viewer tells the extension about each card that it shows.
    // 3. Each read of a seen field goes through the extension, which also returns true for the
    //    cards and buckets that it keeps.
    execute {
        val getRequest = StorySeenRequestFingerprint.method
        val requestClass = mutableClassDefBy(getRequest.definingClass)

        // The sender is the only void method of the class that calls getRequest.
        val senders = requestClass.methods.filter { method ->
            method.returnType == "V" &&
                method.implementation?.instructions?.any { instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.name == getRequest.name &&
                        reference.definingClass == getRequest.definingClass
                } == true
        }
        check(senders.size == 1) { "Expected 1 story seen sender, found ${senders.size}" }
        val sender = senders.single()

        sender.addInstructions(0, "return-void")

        // The seen helper runs once for each card that the viewer shows. It takes the bucket and
        // the card, next to each other, so one range call passes both to the extension.
        val helper = SeenHelperFingerprint.method
        val types = helper.parameterTypes.map { it.toString() }
        val bucketIndex = types.indexOf(STORY_BUCKET)
        check(bucketIndex >= 0 && types.getOrNull(bucketIndex + 1) == STORY_CARD) {
            "Expected a StoryBucket and then a StoryCard parameter on the seen helper, found $types"
        }
        val first = bucketIndex + if (AccessFlags.STATIC.isSet(helper.accessFlags)) 0 else 1
        helper.addInstructions(
            0,
            "invoke-static/range { p$first .. p${first + 1} }, " +
                "$EXTENSION->markCardSeen(Ljava/lang/Object;Ljava/lang/Object;)V",
        )

        // Replace `invoke-virtual { vT, vH }, TreeJNI->getBooleanValue(I)Z`, where vH holds a seen
        // field, with a static call that takes the same two registers. Both forms are one 35c
        // instruction, so the size of the method and every branch stay the same.
        var replaced = 0
        val cardSeenGetters = mutableSetOf<String>()
        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/andrewliang/")) return@classDefForEach
            val hits = classDef.methods.mapNotNull { method ->
                val instructions = method.implementation?.instructions?.toList()
                    ?: return@mapNotNull null
                val indices = instructions.indices.filter { index ->
                    readsSeenField(instructions, index)
                }
                if (indices.isEmpty()) null else method to indices
            }
            if (hits.isEmpty()) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef.type)
            hits.forEach { (method, indices) ->
                val mutableMethod = mutableClass.methods.first {
                    it.name == method.name &&
                        it.parameterTypes == method.parameterTypes &&
                        it.returnType == method.returnType
                }
                val isGetter = method.returnType == "Z" && method.parameterTypes.isEmpty()
                if (classDef.type == REGULAR_STORY_CARD && isGetter) cardSeenGetters += method.name
                indices.forEach { index ->
                    val invoke = mutableMethod.implementation!!.instructions[index]
                        as FiveRegisterInstruction
                    mutableMethod.replaceInstruction(
                        index,
                        "invoke-static { v${invoke.registerC}, v${invoke.registerD} }, " +
                            "$EXTENSION->isSeen(Ljava/lang/Object;I)Z",
                    )
                    replaced++
                }
            }
        }
        check(replaced > 0) { "No read of a seen field found" }

        // The extension asks a story card whether it is seen. The getter is the no-argument boolean
        // of the story card class that reads is_seen_by_viewer, so its name comes from the reads
        // replaced above.
        val cardSeenGetter = cardSeenGetters.singleOrNull()
            ?: error("Expected 1 seen getter on $REGULAR_STORY_CARD, found $cardSeenGetters")
        check(
            mutableClassDefBy(STORY_CARD).methods.any {
                it.name == cardSeenGetter && it.returnType == "Z" && it.parameterTypes.isEmpty()
            },
        ) { "$STORY_CARD has no $cardSeenGetter()Z" }
        val isCardSeen = mutableClassDefBy(EXTENSION).methods.single { it.name == "isCardSeen" }
        isCardSeen.removeInstructions(0, isCardSeen.implementation!!.instructions.size)
        isCardSeen.addInstructions(
            0,
            """
                check-cast p0, $STORY_CARD
                invoke-virtual { p0 }, $STORY_CARD->$cardSeenGetter()Z
                move-result v0
                return v0
            """,
        )
    }
}

/** The register moves that can stand between the constant and the read. */
private val MOVES = setOf(
    Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
    Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16,
    Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16,
)

/**
 * True when the instruction at [index] is `TreeJNI.getBooleanValue(I)Z` and its hash register holds
 * one of [SEEN_FIELDS]. The constant can stand a few instructions earlier, when the code moves the
 * tree into place in between, but no instruction in between can write the hash register.
 */
private fun readsSeenField(
    instructions: List<Instruction>,
    index: Int,
): Boolean {
    val invoke = instructions[index]
    if (invoke.opcode != Opcode.INVOKE_VIRTUAL) return false
    val reference = (invoke as ReferenceInstruction).reference as MethodReference
    if (reference.definingClass != TREE || reference.name != "getBooleanValue") return false
    val hashRegister = (invoke as FiveRegisterInstruction).registerD

    for (previous in index - 1 downTo maxOf(0, index - 4)) {
        val instruction = instructions[previous]
        val writes = (instruction as? OneRegisterInstruction)?.registerA == hashRegister
        if (instruction.opcode == Opcode.CONST && writes) {
            return (instruction as NarrowLiteralInstruction).narrowLiteral in SEEN_FIELDS
        }
        if (writes || instruction.opcode !in MOVES) return false
    }
    return false
}
