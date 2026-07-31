package app.andrewliang.patches.line.hidepremiumunsend

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val hidePremiumUnsendPatch = bytecodePatch(
    name = "Hide premium unsend upsells",
    description = "Removes the two LYP premium-unsend upsells that survive \"Disable LINE " +
        "Premium\" (they read config directly instead of the market-availability flag): the " +
        "\"Unsend discreetly\" button/label in the unsend-message confirmation dialog, and the " +
        "\"How to unsend discreetly\" promotion link shown after unsending. The ordinary unsend " +
        "dialog and its buttons are unaffected. Off by default.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // --- 1) "Unsend discreetly" icon + label in UnsendMessageLdsDialog.onViewCreated ---
        // The discreet icon (n3) + label (o3) are shown only in the `instance-of …$a$c`
        // (UnsendSilently) branch, then hidden in the else. Force the instance-of result to 0 so
        // the else (hide) branch always runs; the dialog's real action buttons are separate views.
        UnsendDiscreetlyButtonFingerprint.instructionMatches.first().let { instanceOfMatch ->
            val reg = (instanceOfMatch.instruction as TwoRegisterInstruction).registerA
            UnsendDiscreetlyButtonFingerprint.method.addInstructions(
                instanceOfMatch.index + 1,
                "const/16 v$reg, 0x0",
            )
        }

        // --- 2) "How to unsend discreetly" promo link (wi1.j4 constructor) ---
        // The link handler is created only when `k2.a(i1.W() && i1.X(), …) == SUPPORTED_CHAT`.
        // Force k2.a's first argument (the W()&&X() result) to 0 so it returns non-SUPPORTED and
        // the link stays null. k2.a is called exactly once in this class; obfuscated `Lne1/k2;`
        // drifts between versions (re-verify on version bump).
        val promoClass = mutableClassDefBy(UnsendPromoLinkFingerprint.method.definingClass)
        var promoPatched = false
        promoClass.methods.forEach forEachMethod@{ method ->
            val instructions = method.implementation?.instructions?.toList() ?: return@forEachMethod
            val callIndex = instructions.indexOfFirst { instruction ->
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                ref?.definingClass == "Lne1/k2;" && ref.name == "a"
            }
            if (callIndex < 0) return@forEachMethod
            val firstArgReg = (instructions[callIndex] as FiveRegisterInstruction).registerC
            method.addInstructions(callIndex, "const/16 v$firstArgReg, 0x0")
            promoPatched = true
        }
        if (!promoPatched) throw PatchException("unsend promo-link k2.a call not found in ${promoClass.type}")
    }
}
