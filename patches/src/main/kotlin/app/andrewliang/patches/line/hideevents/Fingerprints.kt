package app.andrewliang.patches.line.hideevents

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal

/**
 * The slide-out chat menu's **"Events"** row. It is a generic `z00.n` menu item built inside
 * `ChatHistoryMenuFragment` (the same `z00.n` class backs other rows such as Notes), so there is
 * no dedicated row class to neuter. Its visibility is instead decided at the build site by the
 * boolean field `Lv00/o4;->l:Z` — the only read of that field outside its own data class — which
 * is loaded and then passed as the row's `isVisible` constructor argument immediately before
 * the Events label `chatmenu_mainlist_button_events` (0x7f150e87).
 *
 * Matching the `iget-boolean` of `o4.l` followed (in program order) by the Events label literal
 * uniquely locates that load, which the patch overwrites with a constant `false`.
 */
internal object EventsMenuRowFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = "Lv00/o4;", name = "l"),
        literal(0x7f150e87),
    ),
)
