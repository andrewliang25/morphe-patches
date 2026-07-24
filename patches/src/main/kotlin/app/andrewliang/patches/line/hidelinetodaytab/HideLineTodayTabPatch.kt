package app.andrewliang.patches.line.hidelinetodaytab

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideLineTodayTabPatch = bytecodePatch(
    name = "Hide LINE TODAY tab",
    description = "Removes the LINE TODAY (News) tab from the main bottom navigation, " +
        "in both the news-tab and news-row layouts.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Remove the NEWS and NEWS_ROW `sget-object` + following `ArrayList.add` pairs from the
    // tab-list builder. instructionMatches[0] = NEWS (earlier), [1] = NEWS_ROW (later);
    // remove the higher index first so the earlier one stays valid.
    execute {
        val matches = LineTodayTabListFingerprint.instructionMatches
        val newsIndex = matches[0].index
        val newsRowIndex = matches[1].index
        LineTodayTabListFingerprint.method.apply {
            removeInstructions(newsRowIndex, 2)
            removeInstructions(newsIndex, 2)
        }
    }
}
