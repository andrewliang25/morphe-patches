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
    description = "Hides all LINE Yahoo Premium (LYP) surfaces: the upsells, the badges, the " +
        "Premium settings page, and the subscribe and manage flows. Premium chat backup changes " +
        "to the ordinary chat-history backup. This patch unlocks nothing, because the server " +
        "enforces premium.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // LINE gates every LYP premium surface on one market-availability config flag, e13.a.d() ==
    // jw4.i1.W(). The facade b13.l reads it three ways that all cascade from d():
    //   z() = H().d()            -> "premium enabled"   (35 entry-point gates)
    //   l() = H().b()            -> q.UNAVAILABLE when !d()  (53 region switches)
    //   status mapper j13.m      -> i$d Unavailable when !d() (o()/a(), 10 instanceof hides)
    // Forcing d() = false reproduces the shipped "LYP not available here" state — the app's own
    // default for non-premium markets. Premium-scoped: the shared jw4.i1.W() that non-premium
    // features read is untouched, since we patch e13.a.d(), not i1.W().
    //
    // The obfuscated facade b13.l is located via the unique string "LITE_ENJOY", then its z()
    // accessor resolves e13.a.d() with no drifting name hardcoded: z() is the only parameterless
    // ()Z facade method with exactly two invoke-virtuals (`return H().d()`), and its 2nd call is
    // <config>.d() — the class + method to neuter.
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

        // Flipping d() alone leaves a state LINE never ships: premium "unavailable" while the
        // premium chat-BACKUP flag (ic4.d.j(), a separate server config via vc4.a0 ->
        // m2.a().i0().g()) stays on. The Chats settings screen then renders the premium-backup row,
        // whose badge provider asks the facade for an icon and gets null from b13.l.E()
        // (`if (!z()) return null`); the one view holder that doesn't null-guard calls
        // Context.getDrawable(0) -> Resources$NotFoundException, killing Settings > Chats. Flipping
        // the backup gate too makes both halves match a real non-LYP market.
        //
        // The gate is used complementarily across the settings UI (`j()` shows the premium row,
        // `!j()` the ordinary "Back up chat history" row), so false restores a working non-premium
        // backup entry point rather than leaving a hole.
        val backupFacade = mutableClassDefBy(PremiumBackupFacadeFingerprint.method.definingClass)

        // vc4.k0 has exactly three ()Z methods. Select on shape rather than the drift-prone name
        // `j`: it is the only one that opens with an `iget-object` (of the vc4.a0 lambda field)
        // and calls nothing through an interface. `q()` opens with invoke-virtual; `r()` opens
        // with an `iget-object` of a Lkotlin/Lazy; and reads it via invoke-interface.
        val backupGate = backupFacade.methods.single { method ->
            method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.let { instructions ->
                    instructions.first().opcode == Opcode.IGET_OBJECT &&
                        instructions.none { it.opcode == Opcode.INVOKE_INTERFACE }
                } == true
        }

        // `.locals 0`, so p0 is the only register — and it's dead after the immediate return.
        backupGate.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """,
        )
    }
}
