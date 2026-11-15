package app.andrewliang.patches.line.callrecording

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE_CALL_RECORDING
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Starts recording a 1-to-1 call as soon as it connects. LINE's own recorder does the capture and
 * saves the file. Use the "Auto-record calls" switch to turn it off.
 *
 * After the recording extension is set up in `premiumcall/a.a()` (`st7.b.z(st7.b$a)`), the patch
 * calls `rt7.a.I()` on the same object to start recording. The `z()` receiver is the object, which
 * also implements `rt7.a`. Its argument register (the `st7.b$a`) is dead right after the call, so
 * it carries the switch value.
 *
 * Call recording is a 26.16.0+ feature, so the patch lists only those versions. The site stays
 * guarded, because a forced build (`-f`) can still apply it to an older version.
 */
@Suppress("unused")
val autoRecordCallsPatch = bytecodePatch(
    name = "[Call] Auto-record calls",
    description = "Starts recording a 1-to-1 LINE call as soon as it connects. LINE saves the " +
        "recording. Recording a call without the other person's consent is illegal " +
        "in some places. You are responsible for following the law where you are.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE_CALL_RECORDING)

    dependsOn(callRecordingEnablePatch, lineSettingsExtensionPatch)

    execute {
        // 26.16.0+ feature. Without it the premiumcall package is absent: no-op on older versions.
        val gate = CallRecordingGateFingerprint.methodOrNull ?: return@execute

        val instructions = gate.implementation!!.instructions.toList()
        val zIndex = instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_INTERFACE &&
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "z"
        }
        check(zIndex >= 0) { "could not find st7.b.z() setup call in premiumcall.a.a()" }
        val z = instructions[zIndex] as FiveRegisterInstruction
        val objectRegister = z.registerC
        val switchRegister = z.registerD
        gate.addInstructionsWithLabels(
            zIndex + 1,
            readLineSetting("autoRecordCalls", "v$switchRegister") +
                """
                    if-eqz v$switchRegister, :skip
                    invoke-interface {v$objectRegister}, Lrt7/a;->I()V
                """,
            ExternalLabel("skip", gate.getInstruction(zIndex + 1)),
        )

        markLineSettingIncluded("autoRecordCalls")
    }
}
