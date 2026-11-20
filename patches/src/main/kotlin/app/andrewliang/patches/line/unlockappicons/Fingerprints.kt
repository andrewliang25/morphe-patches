package app.andrewliang.patches.line.unlockappicons

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The App icon gate of LINE's custom app icon facade (`hf5.t.c()` in 26.17.1). The Settings row
 * shows only while it returns true, and at each start LINE puts the default icon back while it
 * returns false. It checks three things in order: the Premium market flag (an interface `()Z`
 * call), the server flag `function.premium.common.appicon.enabled`, and the profile region
 * (`JP`, `TH`, `TW`).
 *
 * A sibling gate in the same class does only the region check, so the match also needs the
 * interface `()Z` call.
 */
internal object AppIconGateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        methodCall(name = "getProfile"),
        methodCall(definingClass = "Ljava/lang/String;", name = "toUpperCase"),
        methodCall(definingClass = "Ljava/util/Set;", name = "contains"),
    ),
    custom = { method, _ -> premiumFlagCallIndex(method) >= 0 },
)

/** The index of the first interface `()Z` call of [method], or -1. */
internal fun premiumFlagCallIndex(method: Method): Int =
    method.implementation?.instructions?.indexOfFirst { instruction ->
        instruction.opcode == Opcode.INVOKE_INTERFACE &&
            ((instruction as ReferenceInstruction).reference as MethodReference).let {
                it.returnType == "Z" && it.parameterTypes.isEmpty()
            }
    } ?: -1

/**
 * The Premium eligibility check of the LYP premium facade (`bg3.m.A(sg3.c, Continuation)` in
 * 26.17.1). LINE asks it, with the feature enum value `APP_ICON`, whether an account may use a
 * Premium icon. It returns the `Eligible` object or `Ineligible(UNAVAILABLE / NOT_SUBSCRIBED)`.
 *
 * Only this method of the facade reads `NOT_SUBSCRIBED`. The facade is the class of the
 * premium-state sync method, which takes `PremiumStateBatchedSyncWorker$b` (name-kept).
 */
internal object PremiumEligibilityFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        fieldAccess(name = "UNAVAILABLE"),
        fieldAccess(name = "NOT_SUBSCRIBED"),
    ),
    custom = { method, classDef ->
        method.parameterTypes.size == 2 && classDef.methods.any { sibling ->
            sibling.parameterTypes.singleOrNull()?.toString() ==
                "Lcom/linecorp/line/lyppremium/impl/worker/PremiumStateBatchedSyncWorker\$b;"
        }
    },
)

/**
 * `toString` of the `Eligible` result of [PremiumEligibilityFingerprint] (`sg3.i$b` in 26.17.1).
 * The result is a Kotlin `data object`, so the class holds its one instance in a static field of
 * its own type. An enum elsewhere in LINE also has the text "Eligible", but not in `toString`.
 */
internal object EligibleToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("Eligible")),
    custom = { _, classDef ->
        classDef.fields.any { field ->
            AccessFlags.STATIC.isSet(field.accessFlags) && field.type == classDef.type
        }
    },
)

/**
 * The parse lambda of the App icon list (`pf5.i.invoke` in 26.17.1). It gets the JSON of the
 * server's `/app-icons` answer as a String (`check-cast p1, String` first) and decodes it. In a
 * beta build it also logs the JSON with the tag "CustomAppIcon".
 */
internal object AppIconListParseFingerprint : Fingerprint(
    name = "invoke",
    filters = listOf(string("RELEASE"), string("beta"), string("CustomAppIcon")),
)
