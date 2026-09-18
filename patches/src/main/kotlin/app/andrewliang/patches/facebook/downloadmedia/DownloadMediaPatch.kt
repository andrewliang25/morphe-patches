package app.andrewliang.patches.facebook.downloadmedia

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/** Framework type. Only the menu builders take one; the string tables that share the tag do not. */
private const val MENU = "Landroid/view/Menu;"

/** Kept name. Every GraphQL model reads its cached fields through this class. */
private const val BASE_MODEL = "Lcom/facebook/graphql/modelutil/BaseModelWithTree;"

@Suppress("unused")
val downloadMediaPatch = bytecodePatch(
    name = "[Video] Download any video or reel",
    description = "Adds Facebook's own Download option to the menu of any video or reel, not only " +
        "the ones you posted. It saves through Facebook's own downloader. Videos whose download " +
        "link the server withholds still cannot be saved.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Facebook already ships the whole feature: the menu item, its label and download icon, the
    // click handler and the downloader behind it. All of that is reached only after a chain of
    // checks ending in "you posted this", so it appears on your own videos and reels and nowhere
    // else. This forces the checks that express that restriction, and changes nothing else.
    //
    // The last check in the chain, that the download link is not null, is deliberately left alone.
    // It is the server's decision rather than the client's, and bypassing it would hand a null URL
    // to the downloader. Where the server sends no link, the item simply stays hidden.
    execute {
        // The tag is also a constant in two string tables, which are <clinit> and so return void
        // as well. Taking a Menu is what actually distinguishes a builder, and Menu is a framework
        // type that obfuscation cannot touch.
        val matches = VideoMenuDownloadItemFingerprint.matchAll()
            .filter { MENU in it.method.parameterTypes }

        check(matches.size == 2) {
            "Expected 2 video menu builders carrying $DOWNLOAD_VIDEO_TAG, found ${matches.size}: " +
                matches.joinToString { "${it.method.definingClass}->${it.method.name}" }
        }

        val forced = matches.sumOf { match ->
            val method = match.method
            val instructions = method.implementation!!.instructions.toList()

            val tagIndex = instructions.indexOfFirst { it.stringLiteralOrNull() == DOWNLOAD_VIDEO_TAG }
            check(tagIndex >= 0) { "${method.definingClass}->${method.name} lost its menu tag" }

            // Only look between the previous menu item's tag and this one. The player's builder is
            // over 700 instructions and other items in it run their own string comparisons, so a
            // search that is not bounded this way reaches the wrong ones.
            val windowStart = (tagIndex - 1 downTo 0)
                .firstOrNull { instructions[it].stringLiteralOrNull() != null }
                ?.plus(1)
                ?: 0

            (windowStart until tagIndex).count { index ->
                val gate = instructions[index].isDownloadGate()
                if (gate) method.forceResultTrue(instructions, index)
                gate
            }
        }

        // Three checks in the feed builder, two in the player builder, whose owner check sits in a
        // helper it calls. A release that moves one of them should fail here rather than ship a
        // menu item that is still hidden.
        check(forced >= 4) { "Only $forced download checks were forced; the gate chain moved" }
    }
}

/**
 * Whether this instruction produces one of the checks that hide the download item.
 *
 * The three model reads keep their names through Redex because the classes they belong to do. The
 * fourth form is the player builder's owner check, which Facebook factored out into a helper: a
 * static call returning a boolean, which the two model reads are not.
 */
private fun Instruction.isDownloadGate(): Boolean {
    val reference = methodReferenceOrNull() ?: return false

    return when {
        reference.definingClass == BASE_MODEL && reference.name == "getCachedBoolean" -> true
        reference.definingClass == "Ljava/lang/String;" && reference.name == "equals" -> true
        reference.name == "getBooleanValue" -> true
        opcode == Opcode.INVOKE_STATIC && reference.returnType == "Z" -> true
        else -> false
    }
}

/**
 * Overwrite the result of the call at [index] with `true`.
 *
 * The `move-result` that follows the call is replaced rather than the branch that reads it, and the
 * constant goes into the register that instruction already writes. Both instructions are one code
 * unit, so the method's layout, its branch offsets and its register allocation are all untouched.
 * The call still runs; only its answer is ignored.
 */
private fun MutableMethod.forceResultTrue(
    instructions: List<Instruction>,
    index: Int,
) {
    val moveResult = instructions.getOrNull(index + 1)
    check(moveResult?.opcode == Opcode.MOVE_RESULT) {
        "$definingClass->$name: the check at $index no longer stores its result"
    }

    val register = (moveResult as OneRegisterInstruction).registerA
    // const/4 carries a 4-bit register. Every one of these results lives in v0-v15 today, and a
    // wider register would silently assemble into the wrong place.
    check(register < 16) { "$definingClass->$name: result register v$register is out of range" }

    replaceInstruction(index + 1, "const/4 v$register, 0x1")
}

private fun Instruction.methodReferenceOrNull() =
    (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Instruction.stringLiteralOrNull() =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
