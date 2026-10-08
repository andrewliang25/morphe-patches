package app.andrewliang.patches.line.externalbrowser

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

@Suppress("unused")
val forceExternalBrowserPatch = bytecodePatch(
    name = "[General] Open links in external browser",
    description = "Opens web links in your default browser instead of LINE's in-app browser. " +
        "LIFF mini-apps and LINE links stay in LINE.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // Overwrite the OpenUriActivity$a mode parameter (p3) with EXTERNAL_WITHOUT_CUSTOMTABS at
    // method entry, so the web-URL branch routes into LINE's own native external-browser path.
    // After that, only a null-check reads p3 (our constant is non-null) and the mode switch
    // reads it. Non-web URLs skip the switch, so they do not change.
    //
    // With the setting off, p3 keeps the mode LINE chose. v0 is free at entry: the first
    // original instruction writes it.
    execute {
        val method = OpenUriIntentBuilderFingerprint.method
        method.addInstructionsWithLabels(
            0,
            readLineSetting("externalBrowser", "v0") +
                """
                    if-eqz v0, :stock
                    sget-object p3, Lcom/linecorp/browser/OpenUriActivity${'$'}a;->EXTERNAL_WITHOUT_CUSTOMTABS:Lcom/linecorp/browser/OpenUriActivity${'$'}a;
                """,
            ExternalLabel("stock", method.getInstruction(0)),
        )
        markLineSettingIncluded("externalBrowser")
    }
}
