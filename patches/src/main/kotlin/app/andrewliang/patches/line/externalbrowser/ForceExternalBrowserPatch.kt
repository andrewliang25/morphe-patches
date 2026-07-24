package app.andrewliang.patches.line.externalbrowser

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val forceExternalBrowserPatch = bytecodePatch(
    name = "Open links in external browser",
    description = "Opens tapped web links (http/https) in your default browser instead of " +
        "LINE's in-app browser. LIFF mini-apps and LINE deep links are unaffected.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // OpenUriActivity.h5 :cond_d (the non-internal branch, after LIFF/line:// are routed away)
    // opens with `OpenUriActivity$b.b(v3)` -> `move-result v0`, where v0 = "is http/https" and
    // v3 = the resolved Uri, v1 = the Activity. We inject right AFTER that move-result — a
    // fall-through point (so our code is always reached, unlike the :cond_d branch target
    // itself) — reusing v0. If it's a web link, open it externally and return; otherwise fall
    // through (v0 untouched) to LINE's original handling. `:notweb` is an internal label, so
    // plain addInstructions suffices.
    execute {
        // instructionMatches: [0] = the v98/c.k gate, [1] = the OpenUriActivity$b.b http test.
        val httpTestIndex = OpenUriHandlerFingerprint.instructionMatches[1].index
        OpenUriHandlerFingerprint.method.addInstructions(
            httpTestIndex + 2, // after the $b.b invoke (+1 = its move-result v0).
            """
                if-eqz v0, :notweb
                invoke-static {v1, v3}, Lapp/andrewliang/extension/BrowserRedirect;->openExternalIfWeb(Landroid/app/Activity;Landroid/net/Uri;)Z
                sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
                return-object v0
                :notweb
                nop
            """,
        )
    }
}
