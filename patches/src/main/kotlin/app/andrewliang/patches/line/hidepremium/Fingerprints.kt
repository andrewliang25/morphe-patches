package app.andrewliang.patches.line.hidepremium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

/**
 * `b13.l.h()Z` — a sibling accessor in the LYP premium facade impl
 * (`com.linecorp.line.lyppremium.impl.LypPremiumFacadeImpl`, obfuscated `b13.l`). Anchored on the
 * globally-unique, non-obfuscated string `"LITE_ENJOY"` to locate the (fully obfuscated) facade
 * class. We do NOT patch `h()`. From the class we read `z()` to resolve the market-availability
 * config gate `e13.a.d()` and force it false. Obfuscated types (`b13.l`, `e13.a`, `t13.q`) drift
 * between versions, so we never hardcode them.
 */
internal object PremiumFacadeFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("LITE_ENJOY"),
    ),
)

/**
 * `vc4.k0.h(String, Z)Unit` — a sibling in the premium-backup facade impl (`vc4.k0`, which
 * implements the premium-backup facade interface `ic4.d`). Anchored on the non-obfuscated
 * WorkManager unique-name `"PremiumBackupStatusSyncWorker"`.
 *
 * That string is not globally unique — it also appears in the worker helper
 * `com.linecorp.line.premium.backup.impl.common.worker.a.a()V` — so the `(String, Z)Unit`
 * signature is pinned to disambiguate. The two enclosing methods share nothing else.
 *
 * We do NOT patch `h()`. We only need its `definingClass` to reach the premium-backup
 * availability gate `ic4.d.j()` (see the patch). `vc4.k0`, `vc4.a0` and `ic4.d` are all
 * obfuscated and drift between versions, so none of them is hardcoded.
 */
internal object PremiumBackupFacadeFingerprint : Fingerprint(
    returnType = "Lkotlin/Unit;",
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        string("PremiumBackupStatusSyncWorker"),
    ),
)

/**
 * `x72.h$a.<init>(List<m52.z>, ...)` — the constructor of the Home Compose UI state. The state
 * holds the module list that the tab shows, in field `a`, the first ctor argument. The Home LYP
 * upsell module comes to the tab in that list. The master premium lever does not hide it,
 * because the module renderer `ac2.k` and its view model `ac2.n` read no premium gate.
 *
 * This object is a third copy of the same fingerprint, after `hidehomemodules` and
 * `hidehomefeed`. Each patch keeps its own copy, because the three patches are independent. The
 * user can apply each one alone. All three prepend a `List -> List` filter call at index 0 of
 * this constructor. The order does not matter (see HidePremiumPatch).
 */
internal object HomeStateCtorFingerprint : Fingerprint(
    definingClass = "Lx72/h\$a;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/util/List;",
        "Z", "Z", "Z", "Z", "Z",
        "Ljava/lang/String;",
        "Ljava/lang/Long;",
        "Ljava/lang/Long;",
        "I",
        "Z",
    ),
)
