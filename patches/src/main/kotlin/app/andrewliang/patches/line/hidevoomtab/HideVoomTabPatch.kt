package app.andrewliang.patches.line.hidevoomtab

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideVoomTabPatch = bytecodePatch(
    name = "Hide VOOM tab",
    description = "Removes the VOOM (formerly Timeline) tab from the main bottom navigation.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Remove the TIMELINE `sget-object` + following `ArrayList.add` pair from the tab-list
    // builder. TIMELINE is added exactly once (behind a feature-flag guard, which harmlessly
    // remains). instructionMatches[0] = the TIMELINE sget-object.
    execute {
        val timelineIndex = VoomTabListFingerprint.instructionMatches.first().index
        VoomTabListFingerprint.method.removeInstructions(timelineIndex, 2)
    }
}
