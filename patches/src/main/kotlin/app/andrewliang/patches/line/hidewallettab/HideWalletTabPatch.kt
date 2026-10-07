package app.andrewliang.patches.line.hidewallettab

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.skipTab
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideWalletTabPatch = bytecodePatch(
    name = "[Tab] Hide Wallet tab",
    description = "Removes the Wallet (LINE Pay) tab from the main bottom navigation, " +
        "in both the normal and mini-tab layouts.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // The tab-list builder appends MINI then WALLET, each as `sget-object <const>` followed by
    // `ArrayList.add`. Skip both pairs while the setting is on. instructionMatches[0] = MINI
    // (earlier), [1] = WALLET (later). Change the higher index first so the earlier one stays
    // valid.
    execute {
        val matches = WalletTabListFingerprint.instructionMatches
        val miniIndex = matches[0].index
        val walletIndex = matches[1].index
        WalletTabListFingerprint.method.apply {
            skipTab(walletIndex, "hideWalletTab")
            skipTab(miniIndex, "hideWalletTab")
        }
        markLineSettingIncluded("hideWalletTab")
    }
}
