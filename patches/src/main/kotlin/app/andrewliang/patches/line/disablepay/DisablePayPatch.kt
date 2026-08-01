package app.andrewliang.patches.line.disablepay

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

// Best-effort hand-off to the standalone LINE Pay app, then finish the in-app Pay screen and
// return. p0 is the Activity. The redirect swallows its own exceptions, so finish() always runs
// -> a failed redirect degrades to the old "just close the Pay screen" behavior. Injected right
// after super.onCreate (finishing before super would throw SuperNotCalledException).
private const val REDIRECT_AND_FINISH = """
    invoke-static {p0}, Lapp/andrewliang/extension/LinePayRedirect;->redirect(Landroid/app/Activity;)V
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V
    return-void
"""

@Suppress("unused")
val disablePayPatch = bytecodePatch(
    name = "Redirect LINE Pay",
    description = "Forwards LINE Pay flows to the standalone LINE Pay app instead of running " +
        "them in-app, so the device-integrity check that fails on a re-signed build never " +
        "runs. Messaging is unaffected.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // Front doors into Pay: PayLaunchActivity (all cold routes) and PayLiffActivity (the
    // independent LIFF/web path that loads web-pay.line.me). Inject the redirect+finish right
    // after their super.onCreate call (instructionMatches[0] = the super onCreate invoke).
    execute {
        listOf(
            PayLaunchActivityOnCreateFingerprint,
            PayLiffActivityOnCreateFingerprint,
        ).forEach { fingerprint ->
            val afterSuperIndex = fingerprint.instructionMatches.first().index + 1
            fingerprint.method.addInstructions(afterSuperIndex, REDIRECT_AND_FINISH)
        }
    }
}
