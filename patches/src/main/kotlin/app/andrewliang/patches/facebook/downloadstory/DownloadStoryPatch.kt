package app.andrewliang.patches.facebook.downloadstory

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val downloadStoryPatch = bytecodePatch(
    name = "[Stories] Download any story",
    description = "Adds Facebook's own save option to the menu of any story, and not only to the " +
        "stories that you posted. It saves the picture or the video that the story shows, with " +
        "Facebook's own save code.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // The "More" menu of the story viewer asks one capability question before it offers the save
    // item. That question is what limits the save to your own stories. Everything after it is
    // unconditional. The surface that the menu reads next only decides which label the item gets.
    //
    // This works where the same unlock for feed video does not. The save code reads the media
    // address of the story itself, which is the address that the viewer already plays. It does not
    // read a separate download link that the server can withhold. That address must be present, or
    // the story does not appear at all.
    execute {
        // The class of the action that the menu creates. The patch finds it through the event that
        // the action reports, because Redex gives the class a new name on every Facebook release.
        val saveAction = SaveStoryActionFingerprint.let { fingerprint ->
            val matches = fingerprint.matchAll().filter { it.method.parameterTypes.size == 1 }

            check(matches.size == 1) {
                "Expected 1 save-story action, found ${matches.size}: " +
                    matches.joinToString { "${it.method.definingClass}->${it.method.name}" }
            }

            matches.single().method.definingClass
        }

        val menuBuilders = mutableClassDefBy(STORY_VIEWER_MORE_MENU).methods.filter { method ->
            method.instructionsOrEmpty().any {
                it.opcode == Opcode.NEW_INSTANCE && it.typeReferenceOrNull() == saveAction
            }
        }

        check(menuBuilders.size == 1) {
            "Expected 1 menu builder creating $saveAction, found ${menuBuilders.size}"
        }

        val builder = menuBuilders.single()
        val instructions = builder.instructionsOrEmpty()

        // The capability is the only call in the builder that takes nothing and answers a boolean.
        // Boolean.booleanValue is the one exception, and it is a framework call. The builder caches
        // the answer in a field and reads it back later. Thus one forced answer settles both reads.
        val capabilityCalls = instructions.withIndex().filter { (_, instruction) ->
            val reference = instruction.methodReferenceOrNull()

            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                reference != null &&
                reference.returnType == "Z" &&
                reference.parameterTypes.isEmpty() &&
                !reference.definingClass.startsWith("Ljava/") &&
                !reference.definingClass.startsWith("Landroid/")
        }

        check(capabilityCalls.size == 1) {
            "Expected 1 save capability check in ${builder.name}, found ${capabilityCalls.size}: " +
                capabilityCalls.joinToString { (_, it) -> "${it.methodReferenceOrNull()?.name}" }
        }

        builder.forceResultTrue(instructions, capabilityCalls.single().index)
    }
}

/**
 * Overwrite the result of the call at [index] with `true`.
 *
 * The patch replaces the `move-result` after the call, and not the branch that reads it. The
 * constant goes into the register that this instruction already writes. Both instructions are one
 * code unit. Thus the layout of the method, its branch offsets and its register allocation do not
 * change. The call still runs. Only its answer is ignored.
 */
private fun MutableMethod.forceResultTrue(instructions: List<Instruction>, index: Int) {
    val moveResult = instructions.getOrNull(index + 1)
    check(moveResult?.opcode == Opcode.MOVE_RESULT) {
        "$definingClass->$name: the capability check no longer stores its result"
    }

    val register = (moveResult as OneRegisterInstruction).registerA
    check(register < 16) { "$definingClass->$name: result register v$register is out of range" }

    replaceInstruction(index + 1, "const/4 v$register, 0x1")
}

private fun MutableMethod.instructionsOrEmpty(): List<Instruction> =
    implementation?.instructions?.toList() ?: emptyList()

private fun Instruction.methodReferenceOrNull() =
    (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Instruction.typeReferenceOrNull() =
    ((this as? ReferenceInstruction)?.reference as? TypeReference)?.type
