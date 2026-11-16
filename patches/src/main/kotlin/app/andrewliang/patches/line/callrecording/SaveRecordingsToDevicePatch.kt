package app.andrewliang.patches.line.callrecording

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Saves each finished call recording to the phone's Movies/LINE folder instead of sharing it to
 * Keep Memo. Keep Memo refuses files over 1 GB. Use the "Save recordings to device" switch to
 * turn it off.
 *
 * After a call, LINE sends an automatic export request to its export router (`bu7/a.a()` in
 * 26.16.0). For that request the router reads its default destination from a field that the
 * constructor sets to `KEEP_MEMO`. While the switch is on, the patch gives `DEVICE_STORAGE`
 * instead. That is the path of LINE's own "Save to device" menu item. The register is the
 * `instance-of` result that selected the automatic request, so it is dead at this point.
 */
@Suppress("unused")
val saveRecordingsToDevicePatch = bytecodePatch(
    name = "[Call] Save recordings to device",
    description = "Saves each call recording to the Movies/LINE folder on your phone instead of " +
        "sharing it to Keep Memo, which refuses files over 1 GB.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    execute {
        val routerClass = CallRecordingExportNetworkFingerprint.method.definingClass
        val router = mutableClassDefBy(routerClass)

        // The destination enum (`ut7.a`): the constructor stores its KEEP_MEMO default.
        val destinationType = router.methods.first { it.name == "<init>" }.implementation!!
            .instructions.firstNotNullOfOrNull {
                ((it as? ReferenceInstruction)?.reference as? FieldReference)
                    ?.takeIf { field -> it.opcode == Opcode.SGET_OBJECT && field.name == "KEEP_MEMO" }
                    ?.type
            } ?: error("could not find the KEEP_MEMO default in the export router constructor")

        // The one read of that default, on the branch of the automatic request.
        val (method, index) = router.methods.firstNotNullOfOrNull { method ->
            method.implementation?.instructions?.indexOfFirst {
                it.opcode == Opcode.IGET_OBJECT &&
                    ((it as ReferenceInstruction).reference as FieldReference).let { field ->
                        field.definingClass == routerClass && field.type == destinationType
                    }
            }?.takeIf { it >= 0 }?.let { method to it }
        } ?: error("could not find the default destination read in the export router")

        val instructions = method.implementation!!.instructions.toList()
        val check = instructions[index - 2]
        check(
            check.opcode == Opcode.INSTANCE_OF &&
                instructions[index - 1].opcode == Opcode.IF_EQZ &&
                instructions[index + 1].opcode.let { it == Opcode.GOTO || it == Opcode.GOTO_16 },
        ) { "the default destination read is not on the automatic-request branch" }
        val register = (instructions[index] as TwoRegisterInstruction).registerA
        check(register == (check as TwoRegisterInstruction).registerA) {
            "the default destination read does not reuse the instance-of register"
        }

        method.addInstructionsWithLabels(
            index,
            readLineSetting("saveCallRecordingsToDevice", "v$register") +
                """
                    if-eqz v$register, :keep
                    sget-object v$register, $destinationType->DEVICE_STORAGE:$destinationType
                    goto :done
                """,
            ExternalLabel("keep", method.getInstruction(index)),
            ExternalLabel("done", method.getInstruction(index + 1)),
        )

        markLineSettingIncluded("saveCallRecordingsToDevice")
    }
}
