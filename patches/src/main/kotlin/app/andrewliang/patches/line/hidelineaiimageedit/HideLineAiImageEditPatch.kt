package app.andrewliang.patches.line.hidelineaiimageedit

import app.andrewliang.patches.line.shared.LINE_SETTINGS
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
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
    dependsOn(lineSettingsExtensionPatch)

    execute {
        // While the switch is on, make the getter of the entry point list return an empty list.
        // Each entry then reads that it is off, as on an account where the server turns on no
        // entries.
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

        // The getter has no free register, so the list it read goes through the switch. The
        // switch gives an empty list while it is on, and the same list while it is off.
        val reg = (getter.getInstruction(0) as OneRegisterInstruction).registerA
        getter.addInstructions(
            1,
            """
                invoke-static {v$reg}, $LINE_SETTINGS->hideLineAiImageEdit(Ljava/util/List;)Ljava/util/List;
                move-result-object v$reg
            """,
        )
        markLineSettingIncluded("hideLineAiImageEdit")
    }
}
