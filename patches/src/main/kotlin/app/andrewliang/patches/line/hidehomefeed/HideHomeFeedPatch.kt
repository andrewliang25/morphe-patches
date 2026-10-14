package app.andrewliang.patches.line.hidehomefeed

import app.andrewliang.patches.line.shared.resolveHomeState
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideHomeFeedPatch = bytecodePatch(
    name = "[Home] Hide Home content feed",
    description = "Removes the content feed below the friends list on the Home tab: LINE NEWS, " +
        "official account posts and rankings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // Same mechanism as "[Home] Hide Home modules", and on the same list (see HomeState). This
    // patch drops each module whose type belongs to the server content feed. Every type in that
    // feed starts with "HomeFeed" — see the HomeFeed extension.
    //
    // "[Home] Hide Home modules" prepends the same call shape at the same index. Both are pure
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
    //   1. Stop the fetch. The "load more module content" action has one caller in the app. An immediate return ends the loop.
    //   2. Hide the footer. Home state ctor parameter 6 is isLoadingMore, and its one reader is
    //      the LOADING_MORE footer item. Lever 1 keeps it false on its own. This lever costs
    //      one instruction on a fingerprint that the patch already owns. If lever 1 ever stops
    //      matching, this lever still hides the spinner.
    //
    // Every fingerprint resolves before the first addInstructions, because the patcher does
    // not undo a partial execute. If a lookup fails late, the Manager reports the patch as
    // failed and the earlier lever still ships.
    execute {
        val homeState = resolveHomeState()
        val repositoryType = homeState.stateClass.substringBefore('$') + ";"
        val pageType = GcsPageToStringFingerprint.method.definingClass
        val loadMoreContent = loadMoreContentFingerprint(repositoryType, pageType).method

        // 1. Filter the module list.
        with(homeState) {
            addModuleFilter(
                "filterHomeFeed",
                """
                    invoke-static {v3}, Lapp/andrewliang/extension/HomeFeed;->shouldHide(Ljava/lang/String;)Z
                    move-result v3
                """,
            )
        }

        // 2. Clear parameter 6 at the top of the Home state constructor. The constructor holds
        //    PageData(modules, isPageReady, isPageRefreshing, isError, isPullToRefreshLoading,
        //    isLoadingMore, …), so parameter 6 is isLoadingMore. Its field has one reader in the
        //    app, the map that feeds the LOADING_MORE footer item, so a false value hides that
        //    spinner and changes nothing else. `.locals 0` puts p6 at v6, which const/4 accepts.
        homeState.constructor.addInstructions(0, "const/4 p6, 0x0")

        // 3. Stop the pager. The immediate return stops the requests for feed pages that
        //    step 1 then discards. The instructions that follow become unreachable.
        loadMoreContent.addInstructions(0, "return-void")
    }
}
