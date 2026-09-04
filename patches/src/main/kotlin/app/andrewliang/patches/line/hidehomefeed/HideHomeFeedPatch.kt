package app.andrewliang.patches.line.hidehomefeed

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HOME_STATE = "Llb2/g\$a;"
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
    // List<y82.j0>. Each element holds a typed y82.k0 module in field z.e. The list is the first
    // ctor argument (field `a`) of the Compose state lb2.g$a. This patch filters the list and
    // drops each module whose z.e.getType() belongs to the server content feed. Every type in
    // that feed starts with "HomeFeed" — see the HomeFeed extension.
    //
    // The loop lives in a new method, lb2.g$a.filterHomeFeed. If a patch injects a loop with a
    // backward branch inline, the loop corrupts the layout of an existing method. ART then
    // throws a VerifyError. At the top of lb2.g$a.<init> the patch injects a call with no
    // branch. The call replaces p1 (the list) with the filtered copy before the constructor
    // stores it. One constructor covers every feed build path and every state copy.
    //
    // "Hide Home modules" prepends the same call shape at the same index. Both are pure
    // List -> List filters on p1. Thus the patch that applies second runs first, and the
    // result is the same either way.
    //
    // The filter alone leaves the tab in a state that LINE does not expect. The Home tab is a
    // paged surface, and LINE measures the page AFTER this filter runs. The pager trigger is
    // `lastVisibleIndex + 6 >= itemCount` (GcsPageState). A tab with no feed is short, thus
    // the trigger stays true. LINE then asks for page after page, and the filter discards each
    // one. The item count never grows, and the loading footer stays. The reporter of issue #69
    // saw that spinner on v1.8.0-dev.2.
    //
    // Two more levers correct this:
    //   1. Stop the fetch. jb2.m2.H1(Z) is the "load more module content" action, and one
    //      place in the app calls it. An immediate return ends the loop.
    //   2. Hide the footer. lb2.g$a ctor parameter 6 is isLoadingMore, and its one reader is
    //      the LOADING_MORE footer item. Lever 1 keeps it false on its own. This lever costs
    //      one instruction on a fingerprint that the patch already owns. If lever 1 ever stops
    //      matching, this lever still hides the spinner.
    //
    // Every fingerprint resolves before the first addInstructions, because the patcher does
    // not undo a partial execute. If a lookup fails late, the Manager reports the patch as
    // failed and the earlier lever still ships.
    execute {
        val homeStateCtor = HomeStateCtorFingerprint.method
        val loadMoreContent = LoadMoreContentFingerprint.method

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
                invoke-static {v3}, Lapp/andrewliang/extension/HomeFeed;->shouldHide(Ljava/lang/String;)Z
                move-result v3
                if-nez v3, :loop
                invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :loop
                :done
                return-object v0
            """,
        )

        // 2. At the top of lb2.g$a.<init>, replace the list parameter (p1) with the filtered
        //    copy before the constructor stores it. The call has no branch (invoke +
        //    move-result) and it reuses p1 (`.locals 0`).
        //
        //    The same block clears parameter 6. The constructor is
        //    <init>(List, Z, Z, Z, Z, Z, String, Long, Long, I, Z) and it holds
        //    PageData(modules, isPageReady, isPageRefreshing, isError, isPullToRefreshLoading,
        //    isLoadingMore, orderRequestId, expiredTimeMillis, pageUpdatedTimeMillis, revision,
        //    isSafeMode). Parameter 6 is thus isLoadingMore, which the smali confirms:
        //    `iput-boolean p6, p0, Llb2/g$a;->f:Z`. Field `f` has one reader in the app, the
        //    map that feeds the LOADING_MORE footer item, so a false value hides that spinner
        //    and changes nothing else. `.locals 0` and 12 registers put p6 at v6, which
        //    const/4 accepts.
        homeStateCtor.addInstructions(
            0,
            """
                invoke-static {p1}, $HOME_STATE->$FILTER_NAME$FILTER_DESC
                move-result-object p1
                const/4 p6, 0x0
            """,
        )

        // 3. Stop the pager. The immediate return stops the requests for feed pages that
        //    step 1 then discards. The instructions that follow become unreachable.
        loadMoreContent.addInstructions(0, "return-void")
    }
}
