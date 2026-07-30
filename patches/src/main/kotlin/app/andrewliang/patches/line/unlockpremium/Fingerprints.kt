package app.andrewliang.patches.line.unlockpremium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

/**
 * `b13.l.h()Z` — a sibling accessor in the LYP premium facade impl
 * (`com.linecorp.line.lyppremium.impl.LypPremiumFacadeImpl`, obfuscated `b13.l`). It reads the
 * LypUserStatus and checks the feature code against the literal `"LITE_ENJOY"`.
 *
 * We do NOT patch `h()`. It is only used to locate its (fully obfuscated) defining class: the
 * string `"LITE_ENJOY"` is globally unique in the APK and non-obfuscated, whereas every type in
 * this class (`b13.l`, `z03.b`, `t13.i/k/n/b/q`) is obfuscated and drifts between LINE versions.
 * From the class we then select and patch the per-feature Boolean gate `u(...)` by shape.
 */
internal object LypPremiumFeatureGateFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("LITE_ENJOY"),
    ),
)
