package app.andrewliang.patches.line.hidelinetodaytab

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.skipTab
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideLineTodayTabPatch = bytecodePatch(
    name = "[Tab] Hide LINE TODAY tab",
    description = "Removes the LINE TODAY (News) tab from the main bottom navigation, " +
        "in both the news-tab and news-row layouts.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // Skip the NEWS and NEWS_ROW `sget-object` + following `ArrayList.add` pairs of the tab-list
    // builder while the setting is on. instructionMatches[0] = NEWS (earlier), [1] = NEWS_ROW
    // (later). Change the higher index first so the earlier one stays valid.
    execute {
        val matches = LineTodayTabListFingerprint.instructionMatches
        val newsIndex = matches[0].index
        val newsRowIndex = matches[1].index
        LineTodayTabListFingerprint.method.apply {
            skipTab(newsRowIndex, "hideTodayTab")
            skipTab(newsIndex, "hideTodayTab")
        }
        markLineSettingIncluded("hideTodayTab")
    }
}
