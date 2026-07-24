package app.andrewliang.patches.line.disablepay

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

// finish the activity and return, right after super.onCreate. p0 is the Activity.
private const val FINISH_AND_RETURN = """
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V
    return-void
"""

@Suppress("unused")
val disablePayPatch = bytecodePatch(
    name = "Disable LINE Pay",
    description = "Closes any LINE Pay screen immediately on open, so Pay flows (and their " +
        "device-integrity check) never run. Messaging is unaffected.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Front doors into Pay: PayLaunchActivity (all cold routes) and PayLiffActivity (the
    // independent LIFF/web path). Inject `finish(); return-void` right after their
    // super.onCreate call (instructionMatches[0] = the super onCreate invoke).
    execute {
        listOf(
            PayLaunchActivityOnCreateFingerprint,
            PayLiffActivityOnCreateFingerprint,
        ).forEach { fingerprint ->
            val afterSuperIndex = fingerprint.instructionMatches.first().index + 1
            fingerprint.method.addInstructions(afterSuperIndex, FINISH_AND_RETURN)
        }
    }
}
