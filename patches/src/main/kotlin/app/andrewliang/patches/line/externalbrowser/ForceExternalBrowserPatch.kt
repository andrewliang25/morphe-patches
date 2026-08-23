package app.andrewliang.patches.line.externalbrowser

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val forceExternalBrowserPatch = bytecodePatch(
    name = "Open links in external browser",
    description = "When you tap a web link (http or https), it opens in your default browser " +
        "instead of LINE's in-app browser. LIFF mini-apps and LINE deep links do not change.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Overwrite the OpenUriActivity$a mode parameter (p3) with EXTERNAL_WITHOUT_CUSTOMTABS at
    // method entry, so the web-URL branch routes into LINE's own native external-browser path.
    // After that, only a null-check reads p3 (our constant is non-null) and the mode switch
    // reads it. Non-web URLs skip the switch, so they do not change. No coroutine, no extension.
    execute {
        OpenUriIntentBuilderFingerprint.method.addInstructions(
            0,
            "sget-object p3, Lcom/linecorp/browser/OpenUriActivity\$a;->" +
                "EXTERNAL_WITHOUT_CUSTOMTABS:Lcom/linecorp/browser/OpenUriActivity\$a;",
        )
    }
}
