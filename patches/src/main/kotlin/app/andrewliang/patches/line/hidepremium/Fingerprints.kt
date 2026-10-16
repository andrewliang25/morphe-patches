package app.andrewliang.patches.line.hidepremium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.string

/**
 * `z(PremiumStateBatchedSyncWorker$b)Object` — the premium-state sync entry point of the LYP
 * premium facade impl (`com.linecorp.line.lyppremium.impl.LypPremiumFacadeImpl`, obfuscated). We do NOT patch it. It is matched because it both identifies the fully obfuscated
 * facade class AND opens with the market-availability gate pair, so the patch reads the gate
 * straight off this method:
 *
 *     invoke-virtual {p0}, <facade>->D()<config>;     # the config holder
 *     invoke-virtual {v0}, <config>->d()Z             # the market gate  <- what we neuter
 *     if-nez v0, :cond_0
 *     sget-object p1, <status>->FEATURE_UNAVAILABLE
 *
 * Both anchors survive obfuscation: `PremiumStateBatchedSyncWorker` is name-kept because
 * WorkManager instantiates it reflectively, and `FEATURE_UNAVAILABLE` is a Kotlin enum constant
 * name. Only two methods in the APK take that parameter type — this one and the facade
 * interface's abstract declaration, which has no body and so cannot satisfy an instruction
 * filter. The obfuscated types are never hardcoded.
 */
internal object PremiumFacadeFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Lcom/linecorp/line/lyppremium/impl/worker/PremiumStateBatchedSyncWorker\$b;",
    ),
    filters = listOf(
        fieldAccess(name = "FEATURE_UNAVAILABLE"),
    ),
)

/**
 * A method of the premium-backup facade impl (which implements the premium-backup facade
 * interface). It holds the string `"InitialBackupRestore"`, which no other method in the APK holds.
 *
 * We do NOT patch this method. We only need its `definingClass` to reach the premium-backup
 * availability gate (see the patch). The facade, its gate lambda and the interface are all
 * obfuscated and drift between versions, so none of them is hardcoded. Do not anchor on
 * `kotlin.Unit` either: from LINE 26.15.1, R8 also renames the Kotlin stdlib.
 */
internal object PremiumBackupFacadeFingerprint : Fingerprint(
    filters = listOf(string("InitialBackupRestore")),
)

/**
 * `toString` of LINE's premium font configuration (`g45/c2` in 26.14.0, `ta5/f2` in 26.15.1), a
 * Kotlin data class. Its last field, `isPremiumFontCmsPopupEnabled`, comes from the server setting
 * `function.premium.common.font.cms_popup.enabled`. The Font page shows a server-made promo sheet
 * for new fonts ("Change to new fonts…") only while it is true.
 */
internal object PremiumFontConfigToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    // One filter only: the two versions load the labels in a different order.
    filters = listOf(string("PremiumFontConfiguration(isPremiumFontAvailable=")),
)

/**
 * `toString` of the state of the Font page banner (`dr4/a$b$b` in 26.14.0, `px4/a$b$b` in 26.15.1).
 * The banners are "A new font is available!" and the premium font banners. A banner shows only
 * while its first field, `isVisible`, is true.
 */
internal object FontBannerStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("SlimBannerState(isVisible=")),
)

/**
 * `toString` of LINE's CMS popup configuration (`g45/u` in 26.14.0, `ta5/a0` in 26.15.1). Its one
 * field comes from the server setting `function.premium.common.cms_popup`. While it is false, the
 * CMS popup module (`com.linecorp.line.cms.popup`) fetches and shows no server-made promo popup.
 */
internal object CmsPopupConfigToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("CmsPopupConfiguration(isCmsPopupEnabled=")),
)
