package app.andrewliang.patches.line.unlockpremium

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val unlockPremiumFeaturesPatch = bytecodePatch(
    name = "Unlock premium features (experimental)",
    description = "EXPERIMENTAL / diagnostic. Forces LINE Yahoo Premium (LYP)'s CLIENT-SIDE " +
        "per-feature availability check to always report \"available\", to empirically probe " +
        "which premium features unlock locally vs. stay server-enforced. This only flips the " +
        "single client gate LypPremiumFacadeImpl.u(feature) -> Boolean; the server still " +
        "enforces entitlements, purchases, and delivers gated content (stickers, themes, " +
        "cloud-backup retention, etc.). May cause premium UI to appear for features that then " +
        "fail server-side. Off by default.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // The LYP premium facade impl (obfuscated `b13.l`,
    // com.linecorp.line.lyppremium.impl.LypPremiumFacadeImpl) exposes a suspend per-feature gate
    //   u(Lt13/b; feature, Continuation) : Boolean
    // returning true iff the user is a Subscribed LYP user AND the feature's Lt13/k;->a() ("is
    // restricted") is false. We short-circuit it to always return Boolean.TRUE.
    //
    // We locate the class via the sibling h()Z (anchored on the globally-unique, non-obfuscated
    // string "LITE_ENJOY") rather than by obfuscated type, then select u() by shape.
    //
    // DISAMBIGUATION: three methods in this class share the erased descriptor
    //   (Lt13/b;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
    //     u() -> boxes a Boolean  (the gate we want)
    //     s() -> returns Lt13/k;
    //     A() -> returns Lt13/n;
    // Selecting by descriptor alone is ambiguous, and forcing s()/A() to return a Boolean would
    // throw ClassCastException in their callers. Only u() boxes a primitive via Boolean.valueOf,
    // and it is the ONLY method in the whole class that calls Boolean.valueOf — so presence of
    // that call uniquely identifies u() without depending on any obfuscated (drifting) type name.
    //
    // LIMITATIONS (why unlocking may be partial): features gated via the object-returning
    // accessors s()/A(), or read directly off the raw status via o()/a(), are NOT covered by this
    // patch. Anything the server delivers or authorizes (stickers, themes, purchases, cloud-backup
    // retention windows, etc.) remains server-enforced regardless of this client flag.
    execute {
        val premiumFacade = mutableClassDefBy(LypPremiumFeatureGateFingerprint.method.definingClass)

        val featureGate = premiumFacade.methods.single { method ->
            method.returnType == "Ljava/lang/Object;" &&
                method.parameterTypes.size == 2 &&
                // Second param is the stable, non-obfuscated Continuation of a suspend fn.
                method.parameterTypes[1].toString() == "Lkotlin/coroutines/Continuation;" &&
                // Only u() boxes a boolean -> the sole Boolean.valueOf call in this class.
                method.implementation?.instructions?.any { insn ->
                    val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
                    ref != null &&
                        ref.definingClass == "Ljava/lang/Boolean;" &&
                        ref.name == "valueOf"
                } == true
        }

        // Branchless override at the very top: return Boolean.TRUE and never touch the coroutine
        // state machine. A suspend fn may legally return its resolved value synchronously, so this
        // is a valid completion. Method is `.locals 5`; v0 is a free local we can clobber because
        // we return immediately (the original body becomes dead code). v0 is 4-bit-safe.
        featureGate.addInstructions(
            0,
            """
                const/4 v0, 0x1
                invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object v0
                return-object v0
            """,
        )
    }
}
