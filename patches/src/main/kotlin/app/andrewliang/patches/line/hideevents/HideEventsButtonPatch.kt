package app.andrewliang.patches.line.hideevents

import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val hideEventsButtonPatch = bytecodePatch(
    name = "[Chat] Hide Events button",
    description = "Removes the \"Events\" row from the slide-out menu in a chat room. Events is " +
        "a different feature from LINE Calendar, and it opens a server-hosted page.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // The Events row is a shared z00.n item built in ChatHistoryMenuFragment, gated by the boolean
    // it loads from Lv00/o4;->l:Z right before the Events label; that value flows straight into the
    // row's isVisible ctor arg. Right after the `iget-boolean` (matched filter [0]), the switch
    // clears the loaded value in its own register, so only the Events row is dropped.
    execute {
        val flagMatch = EventsMenuRowFingerprint.instructionMatches.first()
        val flagReg = (flagMatch.instruction as TwoRegisterInstruction).registerA
        if (flagReg > 15) throw PatchException("Events row flag is in v$flagReg")
        EventsMenuRowFingerprint.method.addInstructions(
            flagMatch.index + 1,
            hideIfLineSetting("hideEvents", "v$flagReg"),
        )
        markLineSettingIncluded("hideEvents")
    }
}
