package app.andrewliang.patches.line.hidepremium

import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.resolveHomeState
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val LYP_MODULE_TYPE = "HomeTabLypRecommendation"

@Suppress("unused")
val hidePremiumPatch = bytecodePatch(
    name = "[Premium] Disable LINE Premium",
    description = "Hides all LINE Premium upsells, badges and pages. It unlocks nothing, " +
        "because the server enforces Premium.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // The class names in these comments are from 26.14.0, as in `docs/line-premium-map.md`. R8
    // renames them on each version, so the patch finds each one by shape.
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
    // This patch has six levers. It resolves every fingerprint and every lookup BEFORE the first
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

        val backupGate = backupFacade.methods.single(::isPremiumBackupGate)

        // Third lever: the Home tab upsell module. The Home tab shows one server-driven list of
        // typed modules. The LYP recommendation is the module of type "HomeTabLypRecommendation".
        // The master lever does not hide it. Its renderer and its
        // view model read no premium gate. As a result the tab shows the module
        // whenever the server sends it, and a false market gate changes nothing.
        //
        // Thus this patch removes the module from the list (see HomeState). It does not flip
        // another gate. One literal comparison is enough, so the filter needs no extension
        // method of its own.
        //
        // "[Home] Hide Home modules" and "[Home] Hide Home content feed" prepend the same call shape at the
        // same index. All three are pure List -> List filters on p1. Thus the patch that applies
        // last runs first, and the result is the same in every order.
        val homeState = resolveHomeState()

        // Fourth lever: the promo sheet for new fonts on the Font page. Its text and image come
        // from the server. The Font page view model shows it only while the configuration flag
        // `isPremiumFontCmsPopupEnabled` is true. The flag is the last field of the data class, so
        // it is the last `iget-boolean` of `toString`. The getter of the flag is the `()Z` method
        // that reads that field.
        val fontConfigToString = PremiumFontConfigToStringFingerprint.method
        val hasCmsPopupLabel = fontConfigToString.implementation!!.instructions.any { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                ", isPremiumFontCmsPopupEnabled="
        }
        if (!hasCmsPopupLabel) throw PatchException("premium: the font configuration has no CMS popup flag")
        val cmsPopupField = (fontConfigToString.implementation!!.instructions
            .lastOrNull { it.opcode == Opcode.IGET_BOOLEAN }
            ?.let { (it as ReferenceInstruction).reference as FieldReference }
            ?: throw PatchException("premium: no boolean field in the font configuration toString"))
        val cmsPopupGetter = mutableClassDefBy(fontConfigToString.definingClass).methods.single { method ->
            method.returnType == "Z" && method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { instruction ->
                    instruction.opcode == Opcode.IGET_BOOLEAN &&
                        ((instruction as ReferenceInstruction).reference as FieldReference).name == cmsPopupField.name
                } == true
        }

        // Fifth lever: the Font page banners ("A new font is available!", premium fonts). Each
        // one is a state object whose first constructor argument is `isVisible`.
        val bannerConstructor = mutableClassDefBy(FontBannerStateToStringFingerprint.method.definingClass)
            .methods.single { method ->
                method.name == "<init>" && method.parameterTypes.firstOrNull() == "Z"
            }

        // Sixth lever: every server-made promo popup. The CMS popup module reads one flag before it
        // fetches popups. The class has that one field, so its one `()Z` method is the getter. Today
        // only the Font page shows CMS popups, and the fourth lever already stops that one.
        val cmsPopupModuleGetter = mutableClassDefBy(CmsPopupConfigToStringFingerprint.method.definingClass)
            .methods.single { method -> method.returnType == "Z" && method.parameterTypes.isEmpty() }

        val gates = listOf(marketGate, backupGate, cmsPopupGetter, cmsPopupModuleGetter)
            .map { gate -> gate to gate.gateReturns() }

        // Every lookup is resolved. The patch mutates from this point. Each lever reads the
        // switch, so with the switch off LINE runs as stock.
        //
        // The gates have `.locals 0`, so the only register is `this`, which the body reads. Thus
        // the switch is applied to the result, just before each `return`. The highest index goes
        // first, so the lower indices stay valid.
        gates.forEach { (gate, returns) ->
            returns.sortedByDescending { it.first }.forEach { (index, register) ->
                gate.addInstructions(index, hideIfLineSetting("disablePremium", "v$register"))
            }
        }

        // p1 is `isVisible`. The constructor stores it, and the other constructors call this one.
        bannerConstructor.addInstructions(0, hideIfLineSetting("disablePremium", "p1"))

        // The literal is the receiver of equals(), so a null type is safe.
        with(homeState) {
            addModuleFilter(
                "filterPremiumModules",
                """
                    const-string v4, "$LYP_MODULE_TYPE"
                    invoke-virtual {v4, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v3
                """,
                feature = "disablePremium",
            )
        }
        markLineSettingIncluded("disablePremium")
    }
}

/**
 * The index and register of each `return` of the `()Z` gate. Code inserted before a `return` that
 * a branch or a catch handler jumps to does not run on that path, so such a gate fails.
 */
private fun MutableMethod.gateReturns(): List<Pair<Int, Int>> {
    val implementation = implementation!!
    val instructions = implementation.instructions.toList()
    val addresses = instructions.runningFold(0) { address, instruction -> address + instruction.codeUnits }
    val targets = instructions.indices.mapNotNull { index ->
        (instructions[index] as? OffsetInstruction)?.let { addresses[index] + it.codeOffset }
    } + implementation.tryBlocks.flatMap { block -> block.exceptionHandlers.map { it.handlerCodeAddress } }

    val returns = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN }
    if (returns.isEmpty()) throw PatchException("premium: the gate $definingClass->$name has no return")
    if (returns.any { addresses[it] in targets }) {
        throw PatchException("premium: a branch in $definingClass->$name jumps straight to a return")
    }
    return returns.map { it to (instructions[it] as OneRegisterInstruction).registerA }
}
