package app.andrewliang.patches.line.hidepremium

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HOME_STATE = "Llb2/g\$a;"
private const val FILTER_NAME = "filterPremiumModules"
private const val FILTER_DESC = "(Ljava/util/List;)Ljava/util/List;"
private const val LYP_MODULE_TYPE = "HomeTabLypRecommendation"

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

        // bk4.k0 has exactly three ()Z methods. Select on shape rather than the drift-prone name
        // `j`: it is the only one that opens with an `iget-object` (of the bk4.z lambda field)
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

        // Third lever: the Home tab upsell module. The Home tab shows one server-driven list of
        // typed modules. The LYP recommendation is the module of type "HomeTabLypRecommendation"
        // (y82.k0$q0, payload y82.x). The master lever does not hide it. Its renderer and its
        // view model read no premium gate. As a result the tab shows the module
        // whenever the server sends it, and a false market gate changes nothing.
        //
        // Thus this patch removes the module from the list. It does not flip another gate. The
        // list is the first ctor argument (field `a`) of the Compose state lb2.g$a. Every build
        // path goes to that constructor. One literal comparison needs no extension code, so this
        // patch declares no extension.
        //
        // "Hide Home modules" and "Hide Home content feed" prepend the same call shape at the
        // same index. All three are pure List -> List filters on p1. Thus the patch that applies
        // last runs first, and the result is the same in every order.
        val homeState = mutableClassDefBy(HOME_STATE)
        val homeStateCtor = HomeStateCtorFingerprint.method

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

        // The loop lives in a new method, lb2.g$a.filterPremiumModules. If a patch injects a loop
        // with a backward branch inline, the loop corrupts the layout of an existing method. ART
        // then throws a VerifyError.
        val filter = MutableMethod(
            ImmutableMethod(
                HOME_STATE,
                FILTER_NAME,
                listOf(ImmutableMethodParameter("Ljava/util/List;", null, null)),
                "Ljava/util/List;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(6),
            ),
        )
        homeState.methods.add(filter)
        // p0 = input List. v0 = result ArrayList, v1 = iterator, v2 = element, v3 = type/bool,
        // v4 = the literal. The literal is the receiver of equals(), so a null type is safe.
        filter.addInstructions(
            0,
            """
                new-instance v0, Ljava/util/ArrayList;
                invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
                invoke-interface {p0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                move-result-object v1
                :loop
                invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
                move-result v2
                if-eqz v2, :done
                invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v2
                check-cast v2, Ly82/j0;
                iget-object v3, v2, Ly82/j0;->e:Ly82/k0;
                invoke-interface {v3}, Ly82/k0;->getType()Ljava/lang/String;
                move-result-object v3
                const-string v4, "$LYP_MODULE_TYPE"
                invoke-virtual {v4, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v3
                if-nez v3, :loop
                invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :loop
                :done
                return-object v0
            """,
        )

        // At the top of lb2.g$a.<init>, replace the list parameter (p1) with the filtered copy
        // before the constructor stores it. The call has no branch (invoke + move-result) and it
        // reuses p1 (`.locals 0`).
        homeStateCtor.addInstructions(
            0,
            """
                invoke-static {p1}, $HOME_STATE->$FILTER_NAME$FILTER_DESC
                move-result-object p1
            """,
        )
    }
}
