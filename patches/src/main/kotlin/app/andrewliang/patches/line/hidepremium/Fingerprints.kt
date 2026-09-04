package app.andrewliang.patches.line.hidepremium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

/**
 * `z73.k.z(PremiumStateBatchedSyncWorker$b)Object` — the premium-state sync entry point of the LYP
 * premium facade impl (`com.linecorp.line.lyppremium.impl.LypPremiumFacadeImpl`, obfuscated
 * `z73.k`). We do NOT patch it. It is matched because it both identifies the fully obfuscated
 * facade class AND opens with the market-availability gate pair, so the patch reads the gate
 * straight off this method:
 *
 *     invoke-virtual {p0}, Lz73/k;->D()La83/a;        # the config holder
 *     invoke-virtual {v0}, La83/a;->d()Z              # the market gate  <- what we neuter
 *     if-nez v0, :cond_0
 *     sget-object p1, Lq83/a$a;->FEATURE_UNAVAILABLE:Lq83/a$a;
 *
 * Both anchors survive obfuscation: `PremiumStateBatchedSyncWorker` is name-kept because
 * WorkManager instantiates it reflectively, and `FEATURE_UNAVAILABLE` is a Kotlin enum constant
 * name. Only two methods in the APK take that parameter type — this one and the facade
 * interface's abstract declaration, which has no body and so cannot satisfy an instruction
 * filter. The obfuscated types (`z73.k`, `a83.a`, `q83.a$a`) are never hardcoded.
 *
 * Replaces an earlier anchor on the string `"LITE_ENJOY"` inside a `()Z` accessor. LINE 26.14.0
 * deleted that accessor and moved the predicate into a suspend evaluator, so the old fingerprint
 * matched nothing. This one matches 26.11.0 as well.
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
 * `bk4.k0.h(String, Z)Unit` — a sibling in the premium-backup facade impl (`bk4.k0`, which
 * implements the premium-backup facade interface `nj4.d`).
 *
 * We do NOT patch `h()`. We only need its `definingClass` to reach the premium-backup
 * availability gate `nj4.d.m()` (see the patch). `bk4.k0`, `bk4.z` and `nj4.d` are all
 * obfuscated and drift between versions, so none of them is hardcoded.
 *
 * Anchored on the descriptor plus `Lkotlin/Unit;->INSTANCE`, both framework types R8 never
 * renames. `(String, Z)Lkotlin/Unit;` occurs exactly twice in the APK — here and the facade
 * interface's abstract declaration, which has no body and so cannot satisfy an instruction
 * filter.
 *
 * Replaces an earlier anchor on the string `"PremiumBackupStatusSyncWorker"`, which LINE 26.14.0
 * extracted out of this method into the (now obfuscated) worker helper `al4.l.b(String, Z)V`,
 * leaving `h()` a three-line delegate with no string of its own.
 */
internal object PremiumBackupFacadeFingerprint : Fingerprint(
    returnType = "Lkotlin/Unit;",
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        fieldAccess(definingClass = "Lkotlin/Unit;", name = "INSTANCE"),
    ),
)

/**
 * `lb2.g$a.<init>(List<y82.j0>, ...)` — the constructor of the Home Compose UI state. The state
 * holds the module list that the tab shows, in field `a`, the first ctor argument. The Home LYP
 * upsell module comes to the tab in that list. The master premium lever does not hide it,
 * because the module renderer and its view model read no premium gate.
 *
 * This object is a third copy of the same fingerprint, after `hidehomemodules` and
 * `hidehomefeed`. Each patch keeps its own copy, because the three patches are independent. The
 * user can apply each one alone. All three prepend a `List -> List` filter call at index 0 of
 * this constructor. The order does not matter (see HidePremiumPatch).
 */
internal object HomeStateCtorFingerprint : Fingerprint(
    definingClass = "Llb2/g\$a;",
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
