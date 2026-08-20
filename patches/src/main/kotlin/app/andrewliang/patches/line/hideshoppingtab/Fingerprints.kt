package app.andrewliang.patches.line.hideshoppingtab

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

private const val MAIN_TAB = "Ljp/naver/line/android/activity/main/a;"

/**
 * Matches `wy7.b.a()` — the main bottom-nav tab-list builder — via its access to the
 * non-obfuscated commerce main-tab constants. The commerce slot has two regional variants,
 * added by the same `if`/`else-if` chain: `COMMERCE` (Japan, label `gnb_commerce` =
 * "Shopping" / "ショッピング", gated by `function.maintab.commercetab`) and `COMMERCE_TW`
 * (Taiwan, label `tw_commerce_tab_gnb` = "Discover" / "逛逛", gated by
 * `function.maintab.commercetwtab`). Matching both, in program order, yields the two
 * `sget-object` indices whose `sget`+`ArrayList.add` pairs are removed.
 *
 * Constraining the return type to `List` keeps the enum's own `<clinit>` `sput`s — and the
 * switch tables in `xy7.g` / `xy7.d` / `wy7.f0` — out of the match. Deliberately anchors on
 * neither `MINI`/`WALLET` nor `TIMELINE`, which the Hide-Wallet-tab and Hide-VOOM-tab patches
 * may remove first.
 */
internal object ShoppingTabListFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters = listOf(
        fieldAccess(definingClass = MAIN_TAB, name = "COMMERCE"),
        fieldAccess(definingClass = MAIN_TAB, name = "COMMERCE_TW"),
    ),
)
