package app.andrewliang.patches.line.hidelinetodaytab

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

private const val MAIN_TAB = "Ljp/naver/line/android/activity/main/a;"

/**
 * Matches `wy7.b.a()` — the main bottom-nav tab-list builder — via its access to the
 * non-obfuscated LINE TODAY tab constants. LINE TODAY is represented by two variants:
 * `NEWS` (the main news tab → `NewsMainTabFragment`) and `NEWS_ROW` (the news-row variant →
 * `NewsRowFragment`). Both are added in `a()`; matching both yields the two `sget-object`
 * indices whose `sget`+`ArrayList.add` pairs are removed.
 */
internal object LineTodayTabListFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters = listOf(
        fieldAccess(definingClass = MAIN_TAB, name = "NEWS"),
        fieldAccess(definingClass = MAIN_TAB, name = "NEWS_ROW"),
    ),
)
