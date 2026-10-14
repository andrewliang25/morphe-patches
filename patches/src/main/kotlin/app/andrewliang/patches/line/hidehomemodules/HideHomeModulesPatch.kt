package app.andrewliang.patches.line.hidehomemodules

import app.andrewliang.patches.line.shared.resolveHomeState
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideHomeModulesPatch = bytecodePatch(
    name = "[Home] Hide Home modules",
    description = "Hides the recommended content, hot-topics and ad modules on the Home tab.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // The Home tab renders the module list of its Compose state (see HomeState). Filter that list
    // to drop each module whose type the HomeModules extension blocklists.
    //
    // "[Home] Hide Home content feed" prepends the same call shape at the same index. Both are pure
    // List -> List filters on p1. Thus the patch that applies second runs first, and the
    // result is the same either way.
    execute {
        with(resolveHomeState()) {
            addModuleFilter(
                "filterHomeModules",
                """
                    invoke-static {v3}, Lapp/andrewliang/extension/HomeModules;->shouldHide(Ljava/lang/String;)Z
                    move-result v3
                """,
            )
        }
    }
}
