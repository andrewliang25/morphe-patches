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
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Records a call until it ends. LINE stops a voice recording after 60 minutes and a video
 * recording after 20 or 50 minutes, by video quality. Use the "Remove recording time limit"
 * switch to turn it off.
 *
 * The limit is a timer in LINE's own process. `CallRecordingLimitFingerprint` matches the check
 * that runs on each duration update. While the switch is on, the patch skips the check and goes to
 * the method's normal end, so it never reports that a limit was reached. The register is the
 * one that holds the voice limit, which the check writes before it reads it.
 *
 * Call recording is a 26.16.0+ feature, so the site is guarded and the patch no-ops on older
 * versions.
 */
@Suppress("unused")
val removeRecordingLimitPatch = bytecodePatch(
    name = "[Call] Remove recording time limit",
    description = "Records a call until it ends. LINE stops voice recordings after 60 minutes and " +
        "video recordings after 20 or 50 minutes.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    execute {
        // 26.16.0+ feature. Without it the limit check is absent: no-op on older versions.
        val method = CallRecordingLimitFingerprint.methodOrNull ?: return@execute
        val instructions = method.implementation!!.instructions.toList()

        // `const vX, 0x36ee80`: vX is written here before any read, so it is free above it.
        val limitIndex = CallRecordingLimitFingerprint.instructionMatches[0].index
        val register = (instructions[limitIndex] as OneRegisterInstruction).registerA

        // The check starts at `check-cast pN, <limit state>`, the last cast before the limit.
        // It falls through from the line before, so code put before it always runs.
        val start = (limitIndex downTo 0).first { instructions[it].opcode == Opcode.CHECK_CAST }

        // The normal end: the `sget-object` of the result just before the last `return-object`.
        val end = instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT } - 1
        check(instructions[end].opcode == Opcode.SGET_OBJECT) { "unexpected end of the limit check" }

        method.addInstructionsWithLabels(
            start,
            readLineSetting("removeCallRecordingLimit", "v$register") +
                "if-nez v$register, :skip",
            ExternalLabel("skip", method.getInstruction(end)),
        )

        markLineSettingIncluded("removeCallRecordingLimit")
    }
}
