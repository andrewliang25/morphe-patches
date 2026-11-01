package app.andrewliang.patches.line.hideagenti

import app.andrewliang.patches.line.shared.forceServerFlagFalse
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.andrewliang.patches.shared.resourceIdsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val hideAgentIButtonsPatch = bytecodePatch(
    name = "[General] Hide Agent i buttons",
    description = "Removes the Agent i button from the Home header and from the search bar, " +
        "and hides Agent i in chats permanently.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(resourceIdsPatch)

    execute {
        // Home header: replace the read of the Agent i flag with a const 0 into its own
        // destination register. The lambda then skips the icon. The register can be above v15.
        val flagRead = HomeHeaderAgentIFingerprint.instructionMatches.first()
        val reg = (flagRead.instruction as TwoRegisterInstruction).registerA
        HomeHeaderAgentIFingerprint.method.replaceInstruction(flagRead.index, "const/16 v$reg, 0x0")

        // Search bar: turn off its server flag. The search bar then hides the button and moves
        // its guideline back, as it does for an account without the feature.
        forceServerFlagFalse(SearchBarAiEntryFlagFingerprint)

        // Chats: make both getters of the hidden-until time return Long.MAX_VALUE. The chat and
        // the Settings screen only compare it with the current time, so Agent i stays hidden
        // whatever the user picks.
        val getters = mutableListOf<String>()
        classDefForEach { classDef ->
            val matches = classDef.methods.filter(::isAgentIInChatHiddenUntilGetter)
            if (matches.isEmpty()) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef.type)
            matches.forEach { match ->
                val getter = mutableClass.methods.first {
                    it.name == match.name && it.returnType == "J" && it.parameterTypes.isEmpty()
                }
                val result = getter.implementation!!.instructions[2] as OneRegisterInstruction
                getter.addInstructions(
                    0,
                    """
                        const-wide v${result.registerA}, 0x7fffffffffffffffL
                        return-wide v${result.registerA}
                    """,
                )
                getters += "${classDef.type}->${match.name}()J"
            }
        }
        check(getters.size == 2) { "Expected 2 Agent i in chat hidden-until getters, found $getters" }
    }
}
