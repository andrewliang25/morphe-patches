package app.andrewliang.patches.line.hideadviews

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

// Hide the Smart Channel banner host (p1 = the FrameLayout the controller populates).
private const val HIDE_HOST = """
    const/16 v0, 0x8
    invoke-virtual {p1, v0}, Landroid/view/View;->setVisibility(I)V
"""

// Collapse the receiver ad view itself: zero its height + GONE. These ad classes never call
// setVisibility on themselves, so this sticks (no ancestor walk needed).
private const val HIDE_SELF = """
    invoke-virtual {p0}, Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;
    move-result-object v0
    if-eqz v0, :novp
    const/4 v1, 0x0
    iput v1, v0, Landroid/view/ViewGroup${'$'}LayoutParams;->height:I
    invoke-virtual {p0, v0}, Landroid/view/View;->setLayoutParams(Landroid/view/ViewGroup${'$'}LayoutParams;)V
    :novp
    const/16 v0, 0x8
    invoke-virtual {p0, v0}, Landroid/view/View;->setVisibility(I)V
"""

// AdManager wrappers hide self after super <init>. fl5/e is `.locals 0`, so it reuses the dead
// defStyleAttr param p3 instead of v0.
private const val HIDE_SELF_CTOR_V0 = """
    const/16 v0, 0x8
    invoke-virtual {p0, v0}, Landroid/view/View;->setVisibility(I)V
"""
private const val HIDE_SELF_CTOR_P3 = """
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
        // Chat-list Smart Channel banner: hide its host container at controller construction.
        SmartChannelControllerFingerprint.method.addInstructions(0, HIDE_HOST)

        // LINE Ads SDK display ads (Home/Wallet/etc.): self-collapse at onAttachedToWindow.
        LadAdViewFingerprint.method.addInstructions(0, HIDE_SELF)
        LyadAdViewFingerprint.method.addInstructions(0, HIDE_SELF)

        // Google AdManager wrappers — BEST-EFFORT. Obfuscated names drift across versions,
        // so skip silently if not found; can never break the robust hiding above. Inject
        // setVisibility(GONE) on self right AFTER the super <init> call.
        val adManagerWrappers = listOf(
            AdManagerBannerChatroomFingerprint to HIDE_SELF_CTOR_V0,
            AdManagerNativeChatroomFingerprint to HIDE_SELF_CTOR_V0,
            AdManagerBannerGeneralFingerprint to HIDE_SELF_CTOR_P3, // fl5/e: .locals 0
            AdManagerNativeGeneralFingerprint to HIDE_SELF_CTOR_V0,
            AdManagerBannerMinorRegionFingerprint to HIDE_SELF_CTOR_V0,
            AdManagerNativeMinorRegionFingerprint to HIDE_SELF_CTOR_V0,
        )
        adManagerWrappers.forEach { (fingerprint, hideSmali) ->
            val method = fingerprint.methodOrNull ?: return@forEach
            val afterSuperIndex = (fingerprint.instructionMatchesOrNull?.firstOrNull()?.index
                ?: return@forEach) + 1
            method.addInstructions(afterSuperIndex, hideSmali)
        }
    }
}
