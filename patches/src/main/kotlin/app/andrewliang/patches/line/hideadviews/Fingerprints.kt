package app.andrewliang.patches.line.hideadviews

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall

// --- Robust targets: non-obfuscated LINE ad View classes. ---

/**
 * The Smart Channel controller constructor `<init>(FrameLayout host, ...)` — it inflates the
 * chat-list banner view and addViews it into the host FrameLayout (p1). Hiding that host once at
 * construction collapses the empty banner strip regardless of the banner's visibility state
 * (the previous `dispatchDraw` hook never fired while the strip was an empty placeholder).
 * The class is obfuscated. Anchored on the FrameLayout param and the `ad_smartch_base_view`
 * layout, which only this class inflates.
 */
internal object SmartChannelControllerFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/widget/FrameLayout;", "L", "L", "L", "L"),
    filters = listOf(literal({ resourceId("layout", "ad_smartch_base_view") })),
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
// Obfuscated classes, each found by the one layout it inflates. The methodCall("<init>") filter
// locates the super constructor call, which comes before the layout, so the hide is injected
// right after it (setVisibility before super = uninitialized-this verifier error).

/** Square/OpenChat chatroom-header banner ad. */
internal object AdManagerBannerChatroomFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(name = "<init>"),
        literal({ resourceId("layout", "square_ad_chatroom_header_google_banner_ad_view") }),
    ),
)

/** Square/OpenChat chatroom-header native ad. */
internal object AdManagerNativeChatroomFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(name = "<init>"),
        literal({ resourceId("layout", "square_ad_chatroom_header_google_native_ad_view") }),
    ),
)

/** Square general-header banner ad (`.locals 0` -> hide via dead param p3). */
internal object AdManagerBannerGeneralFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
    filters = listOf(
        methodCall(name = "<init>"),
        literal({ resourceId("layout", "square_ad_general_header_google_banner_ad_view") }),
    ),
)

/** Square general-header native ad. */
internal object AdManagerNativeGeneralFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
    filters = listOf(
        methodCall(name = "<init>"),
        literal({ resourceId("layout", "square_ad_general_header_google_native_ad_view") }),
    ),
)

/** Minor-region banner ad. */
internal object AdManagerBannerMinorRegionFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(name = "<init>"),
        literal({ resourceId("layout", "minor_region_ad_ad_manager_banner_ad_view") }),
    ),
)

/** Minor-region native ad. */
internal object AdManagerNativeMinorRegionFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "L", "L"),
    filters = listOf(
        methodCall(name = "<init>"),
        literal({ resourceId("layout", "minor_region_ad_ad_manager_native_ad_view_height100") }),
    ),
)
