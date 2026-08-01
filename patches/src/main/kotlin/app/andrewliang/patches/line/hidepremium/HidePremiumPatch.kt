package app.andrewliang.patches.line.hidepremium

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val hidePremiumPatch = bytecodePatch(
    name = "Disable LINE Premium",
    description = "Hides all LINE Yahoo Premium (LYP) surfaces — upsells, badges, the " +
        "Premium settings page, and subscribe/manage flows. Doesn't unlock anything " +
        "(premium is server-enforced).",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // LINE gates every LYP premium surface on one market-availability config flag, e13.a.d() ==
    // jw4.i1.W(). The facade b13.l reads it three ways that all cascade from d():
    //   z() = H().d()            -> "premium enabled"   (35 entry-point gates)
    //   l() = H().b()            -> q.UNAVAILABLE when !d()  (53 region switches)
    //   status mapper j13.m      -> i$d Unavailable when !d() (o()/a(), 10 instanceof hides)
    // Forcing d() = false reproduces the shipped "LYP not available here" state (crash-safe: it's
    // the app's own default for non-premium markets). It is premium-scoped: the shared jw4.i1.W()
    // read by non-premium features is left untouched (we patch e13.a.d(), not i1.W()).
    //
    // We locate the obfuscated facade b13.l via the unique string "LITE_ENJOY", then read its z()
    // accessor to resolve e13.a.d() without hardcoding any drifting obfuscated name. z() is the
    // only parameterless ()Z facade method with exactly two invoke-virtual instructions
    // (`return H().d()`); its 2nd call is <config>.d() -> gives us the class + method to neuter.
    execute {
        val facade = mutableClassDefBy(PremiumFacadeFingerprint.method.definingClass)

        val zMethod = facade.methods.single { method ->
            method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions
                    ?.count { it.opcode == Opcode.INVOKE_VIRTUAL } == 2
        }

        // 2nd invoke-virtual in `return H().d()` is the <config>.d() call.
        val dCall = zMethod.implementation!!.instructions
            .last { it.opcode == Opcode.INVOKE_VIRTUAL }
            .let { (it as ReferenceInstruction).reference as MethodReference }

        val marketGate = mutableClassDefBy(dCall.definingClass).methods.single { method ->
            method.name == dCall.name &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty()
        }

        marketGate.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
