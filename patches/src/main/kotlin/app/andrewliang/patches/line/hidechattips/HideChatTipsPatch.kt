package app.andrewliang.patches.line.hidechattips

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val hideChatTipsPatch = bytecodePatch(
    name = "[Chat] Hide tips under messages",
    description = "Hides the \"CHECK\" tips under some messages, such as a received video.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Replace the "show" state test with a const 0 into its own destination register. The method
    // then takes its own "hide" branch, which also skips the code that records the tip as shown.
    execute {
        val stateTest = ChatEntryBannerBindFingerprint.instructionMatches.first()
        val reg = (stateTest.instruction as TwoRegisterInstruction).registerA
        ChatEntryBannerBindFingerprint.method.replaceInstruction(
            stateTest.index,
            "const/16 v$reg, 0x0",
        )
    }
}
