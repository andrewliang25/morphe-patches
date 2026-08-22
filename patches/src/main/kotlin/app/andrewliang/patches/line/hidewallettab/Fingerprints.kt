package app.andrewliang.patches.line.hidewallettab

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

private const val MAIN_TAB = "Ljp/naver/line/android/activity/main/a;"

/**
 * Matches `wy7.b.a()` — the builder that assembles the ordered main bottom-nav tab list.
 *
 * The main-tab enum `jp.naver.line.android.activity.main.a` is non-obfuscated, so its `MINI`
 * and `WALLET` constants are stable anchors. The builder appends `MINI` (mini-tab mode,
 * earlier) and `WALLET` (normal, later). Both resolve to the Wallet/Pay fragment. Matching on
 * both field accesses (in program order) uniquely identifies this method and yields the two
 * `sget-object` indices to remove.
 */
internal object WalletTabListFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters = listOf(
        fieldAccess(definingClass = MAIN_TAB, name = "MINI"),
        fieldAccess(definingClass = MAIN_TAB, name = "WALLET"),
    ),
)
