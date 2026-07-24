package app.andrewliang.patches.line.hideadviews

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

// Hide the receiver's parent: p0.getParent() -> View -> GONE. (SmartChannelViewLayout host.)
private const val HIDE_PARENT = """
    invoke-virtual {p0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
    move-result-object v0
    instance-of v1, v0, Landroid/view/View;
    if-eqz v1, :skip
    check-cast v0, Landroid/view/View;
    const/16 v1, 0x8
    invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
    :skip
"""

// Hide the receiver's grandparent (height=0 + GONE) so the ad SDK can't re-show the ad view.
private const val HIDE_GRANDPARENT = """
    invoke-virtual {p0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
    move-result-object v0
    instance-of v1, v0, Landroid/view/View;
    if-eqz v1, :skip
    check-cast v0, Landroid/view/View;
    invoke-virtual {v0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
    move-result-object v0
    instance-of v1, v0, Landroid/view/View;
    if-eqz v1, :skip
    check-cast v0, Landroid/view/View;
    invoke-virtual {v0}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
    move-result-object v1
    if-eqz v1, :vis
    const/4 v2, 0x0
    iput v2, v1, Landroid/view/ViewGroup${'$'}LayoutParams;->height:I
    invoke-virtual {v0, v1}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V
    :vis
    const/16 v1, 0x8
    invoke-virtual {v0, v1}, Landroid/view/View;->setVisibility(I)V
    :skip
"""

// Hide the receiver itself. Two register variants for the AdManager wrappers: v0 is free
// right after the super call in most ctors; fl5/e is `.locals 0` so it clobbers dead param p3.
private const val HIDE_SELF_V0 = """
    const/16 v0, 0x8
    invoke-virtual {p0, v0}, Landroid/view/View;->setVisibility(I)V
"""
private const val HIDE_SELF_P3 = """
    const/16 p3, 0x8
    invoke-virtual {p0, p3}, Landroid/view/View;->setVisibility(I)V
"""

@Suppress("unused")
val hideAdViewsPatch = bytecodePatch(
    name = "Hide ad views",
    description = "Hides LINE display ad views — the LINE Ads SDK containers across the app, " +
        "the chat-list Smart Channel banner, and Google AdManager ads.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // Robust (non-obfuscated) targets — must match.
        SmartChannelViewLayoutFingerprint.method.addInstructions(0, HIDE_PARENT)
        LadAdViewFingerprint.method.addInstructions(0, HIDE_GRANDPARENT)
        LyadAdViewFingerprint.method.addInstructions(0, HIDE_GRANDPARENT)

        // Google AdManager wrappers — BEST-EFFORT. Obfuscated names drift across versions,
        // so skip silently if not found; this can never break the robust hiding above.
        // Inject setVisibility(GONE) on self right AFTER the super <init> call (located via
        // the methodCall("<init>") filter -> first instruction match).
        val adManagerWrappers = listOf(
            AdManagerBannerChatroomFingerprint to HIDE_SELF_V0,
            AdManagerNativeChatroomFingerprint to HIDE_SELF_V0,
            AdManagerBannerGeneralFingerprint to HIDE_SELF_P3, // fl5/e: .locals 0
            AdManagerNativeGeneralFingerprint to HIDE_SELF_V0,
            AdManagerBannerMinorRegionFingerprint to HIDE_SELF_V0,
            AdManagerNativeMinorRegionFingerprint to HIDE_SELF_V0,
        )
        adManagerWrappers.forEach { (fingerprint, hideSmali) ->
            val method = fingerprint.methodOrNull ?: return@forEach
            val afterSuperIndex = (fingerprint.instructionMatchesOrNull?.firstOrNull()?.index
                ?: return@forEach) + 1
            method.addInstructions(afterSuperIndex, hideSmali)
        }
    }
}
