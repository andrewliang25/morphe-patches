package app.andrewliang.patches.line.disablevoom

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

@Suppress("unused")
val disableVoomPatch = bytecodePatch(
    name = "[General] Disable VOOM",
    description = "VOOM deep links, shares, and notifications do nothing. If you open the " +
        "standalone VOOM feed, it closes. Messaging and the other tabs do not change.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // With the setting off, both methods run their original code.
    execute {
        // 1. Scheme handler for line://home/* : return the existing no-op "not handled"
        //    singleton at entry, so every VOOM deep link / share / notification does nothing.
        val handler = VoomSchemeHandlerFingerprint.method
        handler.addInstructionsWithLabels(
            0,
            readLineSetting("disableVoom", "v0") +
                """
                    if-eqz v0, :stock
                    sget-object v0, Lah8/i;->b:Lah8/i${'$'}a;
                    return-object v0
                """,
            ExternalLabel("stock", handler.getInstruction(0)),
        )

        // 2. Standalone VOOM feed (notification center bypasses the router): finish after
        //    super.onCreate so it never renders. v0 is dead there: the original code writes it
        //    before it reads it.
        val onCreate = LineVoomActivityOnCreateFingerprint.method
        val afterSuperIndex = LineVoomActivityOnCreateFingerprint.instructionMatches.first().index + 1
        onCreate.addInstructionsWithLabels(
            afterSuperIndex,
            readLineSetting("disableVoom", "v0") +
                """
                    if-eqz v0, :stock
                    invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                    return-void
                """,
            ExternalLabel("stock", onCreate.getInstruction(afterSuperIndex)),
        )
        markLineSettingIncluded("disableVoom")
    }
}
