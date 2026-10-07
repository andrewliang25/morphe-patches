package app.andrewliang.patches.line.hidevoomtab

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.skipTab
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideVoomTabPatch = bytecodePatch(
    name = "[Tab] Hide VOOM tab",
    description = "Removes the VOOM (formerly Timeline) tab from the main bottom navigation.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // Skip the TIMELINE `sget-object` + following `ArrayList.add` pair of the tab-list builder
    // while the setting is on. TIMELINE is added exactly once (behind a feature-flag guard, which
    // harmlessly remains). instructionMatches[0] = the TIMELINE sget-object.
    execute {
        val timelineIndex = VoomTabListFingerprint.instructionMatches.first().index
        VoomTabListFingerprint.method.apply {
            skipTab(timelineIndex, "hideVoomTab")
        }
        markLineSettingIncluded("hideVoomTab")
    }
}
