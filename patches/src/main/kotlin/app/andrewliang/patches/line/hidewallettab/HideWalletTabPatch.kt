package app.andrewliang.patches.line.hidewallettab

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideWalletTabPatch = bytecodePatch(
    name = "Hide Wallet tab",
    description = "Removes the Wallet (LINE Pay) tab from the main bottom navigation, " +
        "in both the normal and mini-tab layouts.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // The tab-list builder appends MINI then WALLET, each as `sget-object <const>` followed by
    // `ArrayList.add`. Remove both pairs. instructionMatches[0] = MINI (earlier), [1] = WALLET
    // (later); remove the higher index first so the earlier one stays valid.
    execute {
        val matches = WalletTabListFingerprint.instructionMatches
        val miniIndex = matches[0].index
        val walletIndex = matches[1].index
        WalletTabListFingerprint.method.apply {
            removeInstructions(walletIndex, 2)
            removeInstructions(miniIndex, 2)
        }
    }
}
