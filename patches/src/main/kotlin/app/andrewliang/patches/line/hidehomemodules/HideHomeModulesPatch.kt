package app.andrewliang.patches.line.hidehomemodules

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HOME_STATE = "Llb2/g\$a;"
private const val FILTER_NAME = "filterHomeModules"
private const val FILTER_DESC = "(Ljava/util/List;)Ljava/util/List;"

@Suppress("unused")
val hideHomeModulesPatch = bytecodePatch(
    name = "Hide Home modules",
    description = "Hides clutter modules on the Home tab: the recommended stickers and content " +
        "section, the real-time hot-topics (即時夯話題) block, and the ad modules. A separate " +
        "patch hides the content feed below the friends list.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // The rendered Home feed is a List<y82.j0> (each z.e a typed y82.k0 module) stored as the
    // first ctor arg (field `a`) of the Compose state lb2.g$a. Filter that list to drop
    // modules whose z.e.getType() is blocklisted.
    //
    // The filtering loop is a new method lb2.g$a.filterHomeModules (backward-branching loops
    // corrupt an existing method's layout when injected inline -> VerifyError). We then inject
    // a branchless call at the top of lb2.g$a.<init> to replace p1 (the list) with its
    // filtered copy before it is stored. One constructor covers every feed build path + copies.
    //
    // "Hide Home content feed" prepends the same call shape at the same index. Both are pure
    // List -> List filters on p1. Thus the patch that applies second runs first, and the
    // result is the same either way.
    execute {
        // 1. Add the static filter helper method to lb2.g$a.
        val cls = mutableClassDefBy(HOME_STATE)
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
                check-cast v2, Ly82/j0;
                iget-object v3, v2, Ly82/j0;->e:Ly82/k0;
                invoke-interface {v3}, Ly82/k0;->getType()Ljava/lang/String;
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

        // 2. At the top of lb2.g$a.<init>, replace the list param (p1) with its filtered copy
        //    before it is stored. Branchless (invoke + move-result), reuses p1 (`.locals 0`).
        HomeStateCtorFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $HOME_STATE->$FILTER_NAME$FILTER_DESC
                move-result-object p1
            """,
        )
    }
}
