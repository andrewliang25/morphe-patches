package app.andrewliang.patches.line.hidechattips

import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.andrewliang.patches.shared.resourceIdsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val hideChatTipsPatch = bytecodePatch(
    name = "[Chat] Hide tips under messages",
    description = "Hides the \"CHECK\" tips under some messages, such as a received video.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(resourceIdsPatch)

    dependsOn(lineSettingsExtensionPatch)

    // Right after the "show" state test, the switch clears its result in its own register. The
    // method then takes its own "hide" branch, which also skips the code that records the tip as
    // shown.
    execute {
        val stateTest = ChatEntryBannerBindFingerprint.instructionMatches.first()
        val reg = (stateTest.instruction as TwoRegisterInstruction).registerA
        if (reg > 15) throw PatchException("Chat tip state test is in v$reg")
        ChatEntryBannerBindFingerprint.method.addInstructions(
            stateTest.index + 1,
            hideIfLineSetting("hideChatTips", "v$reg"),
        )
        markLineSettingIncluded("hideChatTips")
    }
}
