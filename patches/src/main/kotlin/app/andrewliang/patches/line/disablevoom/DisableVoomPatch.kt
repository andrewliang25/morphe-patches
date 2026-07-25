package app.andrewliang.patches.line.disablevoom

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableVoomPatch = bytecodePatch(
    name = "Disable VOOM",
    description = "Neutralizes VOOM entry points: VOOM deep links, shares, and notifications " +
        "(line://home/*) do nothing, and the standalone VOOM feed closes on open. Also " +
        "disables VOOM LIVE, the birthday board, and share-to-VOOM. Messaging, friend " +
        "profiles, and other tabs are unaffected.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // 1. Scheme handler for line://home/* : return the existing no-op "not handled"
        //    singleton at entry, so every VOOM deep link / share / notification does nothing.
        VoomSchemeHandlerFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Lv98/i;->b:Lv98/i${'$'}a;
                return-object v0
            """,
        )

        // 2. Standalone VOOM feed (notification center bypasses the router): finish after
        //    super.onCreate so it never renders.
        val afterSuperIndex = LineVoomActivityOnCreateFingerprint.instructionMatches.first().index + 1
        LineVoomActivityOnCreateFingerprint.method.addInstructions(
            afterSuperIndex,
            """
                invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                return-void
            """,
        )
    }
}
