package app.andrewliang.patches.line.hidevoomtab

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.skipTab
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE_VOOM
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideVoomTabPatch = bytecodePatch(
    name = "[Tab] Hide VOOM tab",
    description = "Removes the VOOM (formerly Timeline) tab from the main bottom navigation.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE_VOOM)

    dependsOn(lineSettingsExtensionPatch)

    // Skip the TIMELINE `sget-object` + following `ArrayList.add` pair of the tab-list builder
    // while the setting is on. TIMELINE is added exactly once (behind a feature-flag guard, which
    // harmlessly remains). instructionMatches[0] = the TIMELINE sget-object.
    execute {
        // 26.19.0 removed the VOOM tab: skip it there (a forced build still applies).
        VoomTabListFingerprint.methodOrNull?.let { builder ->
            val timelineIndex = VoomTabListFingerprint.instructionMatches.first().index
            builder.skipTab(timelineIndex, "hideVoomTab")
        }
        markLineSettingIncluded("hideVoomTab")
    }
}
