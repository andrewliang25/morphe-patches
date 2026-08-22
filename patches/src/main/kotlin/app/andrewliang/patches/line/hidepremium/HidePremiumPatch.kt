package app.andrewliang.patches.line.hidepremium

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HOME_STATE = "Lx72/h\$a;"
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

        // Third lever: the Home tab upsell module. The Home tab shows one server-driven list of
        // typed modules. The LYP recommendation is the module of type "HomeTabLypRecommendation"
        // (m52.a0$n0, payload m52.y). The master lever does not hide it. Its renderer ac2.k and
        // its view model ac2.n read no premium gate. As a result the tab shows the module
        // whenever the server sends it, and a false market gate changes nothing.
        //
        // Thus this patch removes the module from the list. It does not flip another gate. The
        // list is the first ctor argument (field `a`) of the Compose state x72.h$a. Every build
        // path goes to that constructor. One literal comparison needs no extension code, so this
        // patch declares no extension.
        //
        // The loop lives in a new method, x72.h$a.filterPremiumModules. If a patch injects a loop
        // with a backward branch inline, the loop corrupts the layout of an existing method. ART
        // then throws a VerifyError.
        //
        // "Hide Home modules" and "Hide Home content feed" prepend the same call shape at the
        // same index. All three are pure List -> List filters on p1. Thus the patch that applies
        // last runs first, and the result is the same in every order.
        val homeState = mutableClassDefBy(HOME_STATE)
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
                check-cast v2, Lm52/z;
                iget-object v3, v2, Lm52/z;->e:Lm52/a0;
                invoke-interface {v3}, Lm52/a0;->getType()Ljava/lang/String;
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

        // At the top of x72.h$a.<init>, replace the list parameter (p1) with the filtered copy
        // before the constructor stores it. The call has no branch (invoke + move-result) and it
        // reuses p1 (`.locals 0`).
        HomeStateCtorFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $HOME_STATE->$FILTER_NAME$FILTER_DESC
                move-result-object p1
            """,
        )
    }
}
