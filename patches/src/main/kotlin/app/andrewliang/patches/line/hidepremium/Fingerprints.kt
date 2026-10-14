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
