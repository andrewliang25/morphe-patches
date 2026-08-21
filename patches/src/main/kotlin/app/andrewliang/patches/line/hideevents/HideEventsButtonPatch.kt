package app.andrewliang.patches.line.hideevents

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val hideEventsButtonPatch = bytecodePatch(
    name = "Hide Events button",
    description = "Removes the \"Events\" row from the slide-out menu in a chat room. Events is " +
        "a different feature from LINE Calendar, and it opens a server-hosted page.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // The Events row is a shared d00.z item built in ChatHistoryMenuFragment, gated by the boolean
    // it loads from Lyz/s4;->l:Z right before the Events label; that value flows straight into the
    // row's isVisible ctor arg. Replace the `iget-boolean` (matched filter [0]) with a const 0 into
    // its own destination register, so only the Events row is dropped.
    execute {
        val flagMatch = EventsMenuRowFingerprint.instructionMatches.first()
        val flagReg = (flagMatch.instruction as TwoRegisterInstruction).registerA
        EventsMenuRowFingerprint.method.apply {
            removeInstruction(flagMatch.index)
            addInstructions(flagMatch.index, "const/16 v$flagReg, 0x0")
        }
    }
}
