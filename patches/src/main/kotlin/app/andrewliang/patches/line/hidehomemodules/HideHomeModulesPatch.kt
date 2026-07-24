package app.andrewliang.patches.line.hidehomemodules

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideHomeModulesPatch = bytecodePatch(
    name = "Hide Home modules",
    description = "Hides selected Home-tab modules (bottom ad, recommended content sections). " +
        "EXPERIMENTAL — blocklist being tuned.",
    default = false, // opt-in until the type blocklist is confirmed on device.
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // In i52.c.e, the Home module list (List<m52.z>) is assembled in v6 and passed as the 3rd
    // arg to m52.i0.<init>. Inject a filter right before that constructor call: rebuild v6
    // keeping only modules whose z.e.getType() is NOT flagged by the extension. v6 (the list)
    // and v3..v12 (the ctor args) stay live; v13/v14/v15 are free scratch under .locals 18.
    // Iteration is done in smali (it can reference the obfuscated m52.z/m52.a0 descriptors);
    // the extension only makes the String blocklist decision.
    //
    // Registers passed to iget-object (22c) / invoke-* (35c) must be v0-v15 (4-bit operands),
    // so the per-element scratch uses v0 — safe because nothing between here and the i0.<init>
    // reads v0. v13/v14/v15 are only used as invoke/iget operands (all <= v15).
    execute {
        val ctorIndex = HomeStateBuilderFingerprint.instructionMatches.first().index
        HomeStateBuilderFingerprint.method.addInstructions(
            ctorIndex,
            """
                new-instance v13, Ljava/util/ArrayList;
                invoke-direct {v13}, Ljava/util/ArrayList;-><init>()V
                invoke-interface {v6}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                move-result-object v14
                :loop
                invoke-interface {v14}, Ljava/util/Iterator;->hasNext()Z
                move-result v0
                if-eqz v0, :done
                invoke-interface {v14}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v15
                check-cast v15, Lm52/z;
                iget-object v0, v15, Lm52/z;->e:Lm52/a0;
                invoke-interface {v0}, Lm52/a0;->getType()Ljava/lang/String;
                move-result-object v0
                invoke-static {v0}, Lapp/andrewliang/extension/HomeModules;->shouldHide(Ljava/lang/String;)Z
                move-result v0
                if-nez v0, :loop
                invoke-virtual {v13, v15}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :loop
                :done
                move-object v6, v13
            """,
        )
    }
}
