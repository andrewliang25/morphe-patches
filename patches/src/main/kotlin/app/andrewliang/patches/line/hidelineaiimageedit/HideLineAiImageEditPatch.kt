package app.andrewliang.patches.line.hidelineaiimageedit

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val hideLineAiImageEditPatch = bytecodePatch(
    name = "[General] Hide LINE AI image editing",
    description = "Removes the LINE AI image editing buttons: \"AI Edit\" in the message long-press " +
        "menu, the button next to an image message, and the button in the chat, album, Note and " +
        "profile photo viewers. The normal photo editor stays.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // Make the getter of the entry point list return an empty list. Each entry then reads
        // that it is off, as on an account where the server turns on no entries.
        val call = LineAiEntryPointsParseFingerprint.instructionMatches[2].instruction as ReferenceInstruction
        val getterRef = call.reference as MethodReference
        val getter = mutableClassDefBy(getterRef.definingClass).methods.single {
            it.name == getterRef.name && it.returnType == "Ljava/util/List;" && it.parameterTypes.isEmpty()
        }

        // Only edit a plain field getter, so a changed shape fails here and not on the device.
        val opcodes = getter.implementation!!.instructions.map { it.opcode }
        check(opcodes == listOf(Opcode.IGET_OBJECT, Opcode.RETURN_OBJECT)) {
            "Unexpected LINE AI entry point getter $getterRef: $opcodes"
        }

        getter.addInstructions(
            0,
            """
                invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
                move-result-object v0
                return-object v0
            """,
        )
    }
}
