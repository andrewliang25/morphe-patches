package app.andrewliang.patches.line.hidehomemodules

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val I52_C = "Li52/c;"
private const val FILTER_NAME = "filterHomeModules"
private const val FILTER_DESC = "(Ljava/util/List;)Ljava/util/List;"

@Suppress("unused")
val hideHomeModulesPatch = bytecodePatch(
    name = "Hide Home modules",
    description = "Hides selected Home-tab modules (bottom ad, recommended content sections). " +
        "EXPERIMENTAL — blocklist being tuned.",
    default = false, // opt-in until the type blocklist is confirmed on device.
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // The Home module list (List<m52.z>, each z.e a typed m52.a0 module) is assembled in
    // i52.c.e and passed as arg3 (register v6) to m52.i0.<init>. We drop modules whose
    // z.e.getType() is blocklisted.
    //
    // The filtering loop lives in its OWN new method (i52.c.filterHomeModules) rather than
    // being injected inline. Injecting a backward-branching loop into the existing
    // 126-instruction e() corrupted its branch layout -> runtime VerifyError "target dex pc
    // not at instruction start". A freshly built method's branches assemble cleanly, and the
    // call injected into e() is branchless (invoke + move-result), so it can't misalign e().
    execute {
        // 1. Add the static filter helper method to i52.c.
        val cls = mutableClassDefBy(I52_C)
        val filter = MutableMethod(
            ImmutableMethod(
                I52_C,
                FILTER_NAME,
                listOf(ImmutableMethodParameter("Ljava/util/List;", null, null)),
                "Ljava/util/List;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(6),
            ),
        )
        cls.methods.add(filter)
        // p0 = input List. v0 = result ArrayList, v1 = iterator, v2 = element, v3 = type/bool.
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
                invoke-static {v3}, Lapp/andrewliang/extension/HomeModules;->shouldHide(Ljava/lang/String;)Z
                move-result v3
                if-nez v3, :loop
                invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :loop
                :done
                return-object v0
            """,
        )

        // 2. In e(), replace the module list (v6) with the filtered list right before the
        //    m52.i0.<init> call. Branchless: just invoke + move-result.
        val ctorIndex = HomeStateBuilderFingerprint.instructionMatches.first().index
        HomeStateBuilderFingerprint.method.addInstructions(
            ctorIndex,
            """
                invoke-static {v6}, $I52_C->$FILTER_NAME$FILTER_DESC
                move-result-object v6
            """,
        )
    }
}
