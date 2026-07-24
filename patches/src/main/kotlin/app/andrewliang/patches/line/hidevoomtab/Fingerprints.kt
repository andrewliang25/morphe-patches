package app.andrewliang.patches.line.hidevoomtab

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

private const val MAIN_TAB = "Ljp/naver/line/android/activity/main/a;"

/**
 * Matches `wy7.b.a()` — the main bottom-nav tab-list builder — via its access to the
 * non-obfuscated `TIMELINE` main-tab constant (VOOM, ordinal 4 → `TimelineFragment`). A
 * `List`-returning method that reads `a;->TIMELINE` is uniquely the tab builder; the match
 * yields the `sget-object …TIMELINE` index whose `sget`+`ArrayList.add` pair is removed.
 * (Deliberately does NOT anchor on WALLET/MINI, which the Hide-Wallet-tab patch may remove
 * first.)
 */
internal object VoomTabListFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters = listOf(
        fieldAccess(definingClass = MAIN_TAB, name = "TIMELINE"),
    ),
)
