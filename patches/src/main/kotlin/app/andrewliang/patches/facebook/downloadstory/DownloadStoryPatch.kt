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
    description = "Adds Facebook's own save option to the menu of any story, not only the ones you " +
        "posted. It saves the same picture or video the story is playing, through Facebook's own " +
        "saving code.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // The story viewer's More menu asks one capability question before it offers the save item, and
    // that question is what restricts saving to your own stories. Everything after it is
    // unconditional: the surface it reads next only decides which label the item gets.
    //
    // This is a better prospect than the same unlock for feed video. The saving code works from the
    // story's own media address -- the one the viewer is already playing -- rather than a separate
    // download link the server may withhold, and that address has to be there for the story to
    // appear at all.
    execute {
        // The action class the menu creates. Reached through the event it reports rather than named
        // outright, because its own name is reassigned on every Facebook release.
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

        // The capability is the builder's only call that takes nothing and answers a boolean, once
        // Boolean.booleanValue is set aside as the framework call it is. Its answer is cached in a
        // field and then read back, so forcing it here settles the check further down too.
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
 * The `move-result` after the call is replaced rather than the branch that reads it, and the
 * constant goes into the register that instruction already writes. Both are one code unit, so the
 * method's layout, its branch offsets and its register allocation are untouched. The call still
 * runs; only its answer is ignored.
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
