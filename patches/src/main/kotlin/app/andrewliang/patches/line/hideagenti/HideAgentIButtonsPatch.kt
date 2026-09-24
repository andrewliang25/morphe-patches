package app.andrewliang.patches.line.hideagenti

import app.andrewliang.patches.line.shared.forceServerFlagFalse
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val hideAgentIButtonsPatch = bytecodePatch(
    name = "[General] Hide Agent i buttons",
    description = "Removes the Agent i button from the Home header and from the search bar.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // Home header: replace the read of the Agent i flag with a const 0 into its own
        // destination register. The lambda then skips the icon. The register can be above v15.
        val flagRead = HomeHeaderAgentIFingerprint.instructionMatches.first()
        val reg = (flagRead.instruction as TwoRegisterInstruction).registerA
        HomeHeaderAgentIFingerprint.method.replaceInstruction(flagRead.index, "const/16 v$reg, 0x0")

        // Search bar: turn off its server flag. The search bar then hides the button and moves
        // its guideline back, as it does for an account without the feature.
        forceServerFlagFalse(SearchBarAiEntryFlagFingerprint)
    }
}
