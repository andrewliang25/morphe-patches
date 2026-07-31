package app.andrewliang.patches.line.hidepremium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

/**
 * `b13.l.h()Z` — a sibling accessor in the LYP premium facade impl
 * (`com.linecorp.line.lyppremium.impl.LypPremiumFacadeImpl`, obfuscated `b13.l`). Anchored on the
 * globally-unique, non-obfuscated string `"LITE_ENJOY"` to locate the (fully obfuscated) facade
 * class. We do NOT patch `h()`; from the class we read `z()` to resolve the market-availability
 * config gate `e13.a.d()` and force it false. Obfuscated types (`b13.l`, `e13.a`, `t13.q`) drift
 * between versions, so we never hardcode them.
 */
internal object PremiumFacadeFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("LITE_ENJOY"),
    ),
)
