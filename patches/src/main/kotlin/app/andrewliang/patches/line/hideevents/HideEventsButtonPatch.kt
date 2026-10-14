package app.andrewliang.patches.line.hideevents

import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.andrewliang.patches.shared.resourceIdsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val hideEventsButtonPatch = bytecodePatch(
    name = "[Chat] Hide Events button",
    description = "Removes the \"Events\" row from the slide-out menu in a chat room. Events is " +
        "a different feature from LINE Calendar, and it opens a server-hosted page.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(resourceIdsPatch)

    dependsOn(lineSettingsExtensionPatch)

    // The Events row is a shared menu item built in ChatHistoryMenuFragment. Its isVisible ctor
    // arg is the boolean loaded by the last `iget-boolean` before the Events label. Right after
    // that `iget-boolean`, the switch clears the loaded value in its own register, so only the
    // Events row is dropped.
    execute {
        EventsMenuRowFingerprint.method.apply {
            val labelIndex = EventsMenuRowFingerprint.instructionMatches.first().index
            val flagIndex = (labelIndex downTo 0).first {
                getInstruction(it).opcode == Opcode.IGET_BOOLEAN
            }
            val flagReg = getInstruction<TwoRegisterInstruction>(flagIndex).registerA
            if (flagReg > 15) throw PatchException("Events row flag is in v$flagReg")
            addInstructions(flagIndex + 1, hideIfLineSetting("hideEvents", "v$flagReg"))
        }
        markLineSettingIncluded("hideEvents")
    }
}
