package app.andrewliang.patches.line.hideadviews

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

// --- Robust targets: non-obfuscated LINE ad View classes. ---

/**
 * The Smart Channel controller `rb0/e.<init>(FrameLayout host, ...)` — it inflates the chat-
 * list banner view and addViews it into the host FrameLayout (p1). Hiding that host once at
 * construction collapses the empty banner strip regardless of the banner's visibility state
 * (the previous `dispatchDraw` hook never fired while the strip was an empty placeholder).
 * `rb0/e` is obfuscated. Anchored on the class + the non-obfuscated FrameLayout/`u0` params.
 */
internal object SmartChannelControllerFingerprint : Fingerprint(
    definingClass = "Lrb0/e;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Landroid/widget/FrameLayout;",
        "L",
        "Landroidx/lifecycle/u0;",
        "L",
        "L",
    ),
)

internal object LadAdViewFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/line/ladsdk/ui/common/view/lifecycle/LadAdView;",
    name = "onAttachedToWindow",
    returnType = "V",
    parameters = emptyList(),
)

internal object LyadAdViewFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/line/ladsdk/ui/v2/common/lifecycle/LyadAdView;",
    name = "onAttachedToWindow",
    returnType = "V",
    parameters = emptyList(),
)

// --- Best-effort targets: Google AdManager LINE wrapper constructors. ---
// Obfuscated class names (drift across versions) -> matched best-effort in the patch. The
// methodCall("<init>") filter locates the super constructor call so the hide is injected
// right after it (setVisibility before super = uninitialized-this verifier error).

/** fl5/c — Square/OpenChat chatroom-header banner ad. */
internal object AdManagerBannerChatroomFingerprint : Fingerprint(
    definingClass = "Lfl5/c;",
    name = "<init>",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(methodCall(name = "<init>")),
)

/** fl5/d — Square/OpenChat chatroom-header native ad. */
internal object AdManagerNativeChatroomFingerprint : Fingerprint(
    definingClass = "Lfl5/d;",
    name = "<init>",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(methodCall(name = "<init>")),
)

/** fl5/e — Square general-header banner ad (`.locals 0` -> hide via dead param p3). */
internal object AdManagerBannerGeneralFingerprint : Fingerprint(
    definingClass = "Lfl5/e;",
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
    filters = listOf(methodCall(name = "<init>")),
)

/** fl5/f — Square general-header native ad. */
internal object AdManagerNativeGeneralFingerprint : Fingerprint(
    definingClass = "Lfl5/f;",
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
    filters = listOf(methodCall(name = "<init>")),
)

/** f93/b — minor-region banner ad. */
internal object AdManagerBannerMinorRegionFingerprint : Fingerprint(
    definingClass = "Lf93/b;",
    name = "<init>",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(methodCall(name = "<init>")),
)

/** f93/c — minor-region native ad. */
internal object AdManagerNativeMinorRegionFingerprint : Fingerprint(
    definingClass = "Lf93/c;",
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "Lh93/b;", "Lf93/g;"),
    filters = listOf(methodCall(name = "<init>")),
)
