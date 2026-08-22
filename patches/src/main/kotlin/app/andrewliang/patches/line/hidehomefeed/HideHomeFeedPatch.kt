package app.andrewliang.patches.line.hidehomefeed

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HOME_STATE = "Lx72/h\$a;"
private const val FILTER_NAME = "filterHomeFeed"
private const val FILTER_DESC = "(Ljava/util/List;)Ljava/util/List;"

@Suppress("unused")
val hideHomeFeedPatch = bytecodePatch(
    name = "Hide Home content feed",
    description = "Removes the content feed below the friends list on the Home tab. The feed " +
        "shows LINE NEWS posts, official account posts, live cards, content units, and ranking " +
        "units. The friends list, the service icons, and the other Home modules do not change.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // Same mechanism as "Hide Home modules", and on the same list. The Home feed is a
    // List<m52.z>. Each element holds a typed m52.a0 module in field z.e. The list is the first
    // ctor argument (field `a`) of the Compose state x72.h$a. This patch filters the list and
    // drops each module whose z.e.getType() belongs to the server content feed. Every type in
    // that feed starts with "HomeFeed" — see the HomeFeed extension.
    //
    // The loop lives in a new method, x72.h$a.filterHomeFeed. If a patch injects a loop with a
    // backward branch inline, the loop corrupts the layout of an existing method. ART then
    // throws a VerifyError. At the top of x72.h$a.<init> the patch injects a call with no
    // branch. The call replaces p1 (the list) with the filtered copy before the constructor
    // stores it. One constructor covers every feed build path and every state copy.
    //
    // "Hide Home modules" prepends the same call shape at the same index. Both are pure
    // List -> List filters on p1. Thus the patch that applies second runs first, and the
    // result is the same either way.
    execute {
        // 1. Add the static filter helper method to x72.h$a.
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
                check-cast v2, Lm52/z;
                iget-object v3, v2, Lm52/z;->e:Lm52/a0;
                invoke-interface {v3}, Lm52/a0;->getType()Ljava/lang/String;
                move-result-object v3
                invoke-static {v3}, Lapp/andrewliang/extension/HomeFeed;->shouldHide(Ljava/lang/String;)Z
                move-result v3
                if-nez v3, :loop
                invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :loop
                :done
                return-object v0
            """,
        )

        // 2. At the top of x72.h$a.<init>, replace the list parameter (p1) with the filtered
        //    copy before the constructor stores it. The call has no branch (invoke +
        //    move-result) and it reuses p1 (`.locals 0`).
        HomeStateCtorFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $HOME_STATE->$FILTER_NAME$FILTER_DESC
                move-result-object p1
            """,
        )
    }
}
