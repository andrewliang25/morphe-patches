package app.andrewliang.patches.line.hidepremium

import app.andrewliang.patches.line.shared.resolveHomeState
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val LYP_MODULE_TYPE = "HomeTabLypRecommendation"

@Suppress("unused")
val hidePremiumPatch = bytecodePatch(
    name = "[Premium] Disable LINE Premium",
    description = "Hides all LINE Premium upsells, badges and pages. It unlocks nothing, " +
        "because the server enforces Premium.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // The class names in these comments are from 26.14.0. R8 renames them on each version.
    //
    // LINE gates every LYP premium surface on one market-availability config flag, a83.a.d() ==
    // g45.i1.Z(). The facade z73.k reads it three ways that all cascade from d():
    //   z() = H().d()            -> "premium enabled"   (35 entry-point gates)
    //   l() = H().b()            -> q.UNAVAILABLE when !d()  (53 region switches)
    //   status mapper j13.m      -> i$d Unavailable when !d() (o()/a(), 10 instanceof hides)
    // Forcing d() = false reproduces the shipped "LYP not available here" state — the app's own
    // default for non-premium markets. Premium-scoped: the shared g45.i1.Z() that non-premium
    // features read is untouched, because we patch a83.a.d(), not i1.Z().
    //
    // The obfuscated facade z73.k is located via its premium-state sync method, which opens with
    // `D().d()` — the market-availability gate. Its 2nd invoke-virtual is that <config>.d() call,
    // giving the class + method to neuter with no drifting name hardcoded.
    //
    // This patch has three levers. It resolves every fingerprint and every lookup BEFORE the first
    // addInstructions, because the patcher does not undo a partial execute. If a later lookup
    // throws, the Manager reports the patch as failed but the earlier lever still ships. The user
    // then gets a half-disabled premium. Nothing in the Manager shows this state, and nothing
    // undoes it. The Compose-state ctor of the third lever is the most drift-prone lookup, so it
    // must resolve before the first mutation.
    execute {
        // 2nd invoke-virtual of the matched method: `D()` yields the config holder, then `d()` is
        // the market gate. Taking it from the matched method itself removes the need to search the
        // facade class for a `()Z` accessor of a particular shape.
        //
        // Any method with two invoke-virtuals satisfies "the 2nd one", so position alone proves
        // nothing. R8 also moves code, not only names, and a hoisted call would silently select a
        // different gate. Confirm the pick by its context instead: the gate result is branched on,
        // and the FEATURE_UNAVAILABLE branch follows within a few instructions.
        val facadeInstructions = PremiumFacadeFingerprint.method.implementation!!
            .instructions.toList()
        val dCallIndex = facadeInstructions.withIndex()
            .filter { (_, instruction) -> instruction.opcode == Opcode.INVOKE_VIRTUAL }
            .map { (index, _) -> index }
            .getOrNull(1)
            ?: throw PatchException("premium: market gate call not found in facade")

        val followsFeatureUnavailable = (dCallIndex until minOf(dCallIndex + 8, facadeInstructions.size))
            .any { index ->
                ((facadeInstructions[index] as? ReferenceInstruction)?.reference as? FieldReference)
                    ?.name == "FEATURE_UNAVAILABLE"
            }
        if (!followsFeatureUnavailable) {
            throw PatchException(
                "premium: the 2nd invoke-virtual is not the market gate - no FEATURE_UNAVAILABLE " +
                    "branch follows it",
            )
        }

        val dCall = (facadeInstructions[dCallIndex] as ReferenceInstruction)
            .reference as MethodReference

        val marketGate = mutableClassDefBy(dCall.definingClass).methods.single { method ->
            method.name == dCall.name &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty()
        }

        // Flipping d() alone leaves a state LINE never ships: premium "unavailable" while the
        // premium chat-BACKUP flag (nj4.d.m(), a separate server config via bk4.z ->
        // m2.a().i0().g()) stays on. The Chats settings screen then renders the premium-backup row,
        // whose badge provider asks the facade for an icon and gets null from z73.k.A()
        // (`if (!z()) return null`). The one view holder that does not null-guard calls
        // Context.getDrawable(0) -> Resources$NotFoundException, killing Settings > Chats. Flipping
        // the backup gate too makes both halves match a real non-LYP market.
        //
        // The gate is used complementarily across the settings UI (`j()` shows the premium row,
        // `!j()` the ordinary "Back up chat history" row), so false restores a working non-premium
        // backup entry point rather than leaving a hole.
        val backupFacade = mutableClassDefBy(PremiumBackupFacadeFingerprint.method.definingClass)

        // The facade has three ()Z methods. Select on shape rather than the drift-prone name: the
        // gate is the only one that opens with an `iget-object` of its lambda field, then calls
        // that lambda's `invoke()Object` by invoke-virtual. One sibling opens with
        // invoke-virtual. The other reads a lazy field, and the lazy's getter is not `invoke`.
        val backupGate = backupFacade.methods.single { method ->
            method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.toList()?.let { instructions ->
                    instructions.size > 1 &&
                        instructions[0].opcode == Opcode.IGET_OBJECT &&
                        instructions[1].opcode == Opcode.INVOKE_VIRTUAL &&
                        ((instructions[1] as ReferenceInstruction).reference as MethodReference)
                            .let { it.name == "invoke" && it.returnType == "Ljava/lang/Object;" }
                } == true
        }

        // Third lever: the Home tab upsell module. The Home tab shows one server-driven list of
        // typed modules. The LYP recommendation is the module of type "HomeTabLypRecommendation".
        // The master lever does not hide it. Its renderer and its
        // view model read no premium gate. As a result the tab shows the module
        // whenever the server sends it, and a false market gate changes nothing.
        //
        // Thus this patch removes the module from the list (see HomeState). It does not flip
        // another gate. One literal comparison needs no extension code, so this
        // patch declares no extension.
        //
        // "[Home] Hide Home modules" and "[Home] Hide Home content feed" prepend the same call shape at the
        // same index. All three are pure List -> List filters on p1. Thus the patch that applies
        // last runs first, and the result is the same in every order.
        val homeState = resolveHomeState()

        // Every lookup is resolved. The patch mutates from this point.
        marketGate.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )

        // `.locals 0`, so p0 is the only register — and it is dead after the immediate return.
        backupGate.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """,
        )

        // The literal is the receiver of equals(), so a null type is safe.
        with(homeState) {
            addModuleFilter(
                "filterPremiumModules",
                """
                    const-string v4, "$LYP_MODULE_TYPE"
                    invoke-virtual {v4, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v3
                """,
            )
        }
    }
}
